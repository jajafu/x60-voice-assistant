package com.jajafu.x60assistant.model

import android.content.Context
import com.jajafu.x60assistant.asr.AsrModelPack
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

data class AsrDownloadProgress(
    val modelId: String,
    val phase: String,
    val percent: Int? = null,
    val active: Boolean = true,
)

/** Downloads a pinned archive, verifies every model file, then installs it as one pack. */
class AsrModelDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val manager = ModelPackManager(context)

    suspend fun download(
        pack: AsrModelPack,
        onProgress: suspend (AsrDownloadProgress) -> Unit,
    ): ModelPackStatus = withContext(Dispatchers.IO) {
        val existing = manager.verifyAsrPack(pack)
        if (existing.valid) return@withContext existing

        val parent = requireNotNull(manager.asrDirectory(pack).parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("無法建立模型資料夾")
        val archiveDirectory = File(context.filesDir, "model-packs/downloads")
        if (!archiveDirectory.isDirectory && !archiveDirectory.mkdirs()) throw IOException("無法建立下載暫存資料夾")
        val archive = File(archiveDirectory, "${pack.id}.part")
        val stage = File(parent, "${pack.id}.staging")
        val backup = File(parent, "${pack.id}.backup")
        val target = manager.asrDirectory(pack)

        if (!target.exists() && backup.exists()) backup.renameTo(target)
        stage.deleteRecursively()
        try {
            downloadArchive(pack, archive, onProgress)
            onProgress(AsrDownloadProgress(pack.id, "正在解壓並驗證"))
            if (!stage.mkdirs()) throw IOException("無法建立模型暫存資料夾")
            extractModelFiles(pack, archive, stage)

            if (backup.exists()) backup.deleteRecursively()
            if (target.exists() && !target.renameTo(backup)) throw IOException("無法保留舊模型")
            if (!stage.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IOException("無法安裝模型")
            }
            val installed = manager.verifyAsrPack(pack)
            if (!installed.valid) {
                target.deleteRecursively()
                if (backup.exists()) backup.renameTo(target)
                throw IOException("模型驗證失敗：${installed.reason}")
            }
            backup.deleteRecursively()
            archive.delete()
            onProgress(AsrDownloadProgress(pack.id, "下載完成", 100))
            installed
        } finally {
            stage.deleteRecursively()
        }
    }

    private suspend fun downloadArchive(
        pack: AsrModelPack,
        archive: File,
        onProgress: suspend (AsrDownloadProgress) -> Unit,
    ) {
        if (archive.length() > pack.archiveBytes) archive.delete()
        var offset = archive.length()
        if (offset == pack.archiveBytes) {
            if (archive.sha256() == pack.archiveSha256) {
                onProgress(AsrDownloadProgress(pack.id, "下載完成，準備驗證", 100))
                return
            }
            archive.delete()
            offset = 0
        }
        onProgress(AsrDownloadProgress(pack.id, if (offset > 0) "正在續傳" else "正在下載", (offset * 100 / pack.archiveBytes).toInt()))
        val request = Request.Builder().url(pack.sourceUrl).get().apply {
            if (offset > 0) header("Range", "bytes=$offset-")
        }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416 && offset > 0) {
                archive.delete()
                throw IOException("伺服器拒絕續傳，將重新下載")
            }
            if (response.code !in listOf(200, 206)) {
                throw HttpDownloadException(response.code)
            }
            val append = offset > 0 && response.code == 206
            if (response.code == 206) {
                val range = response.header("Content-Range")
                val expectedStart = if (offset > 0) offset else 0L
                val match = range?.let { CONTENT_RANGE.matchEntire(it) }
                if (match == null || match.groupValues[1].toLongOrNull() != expectedStart ||
                    match.groupValues[2].toLongOrNull() != pack.archiveBytes - 1 ||
                    match.groupValues[3].toLongOrNull() != pack.archiveBytes
                ) {
                    archive.delete()
                    throw IOException("伺服器續傳範圍無效，將重新下載")
                }
            }
            // A 200 response to Range means the server sent the whole file again.
            if (!append) offset = 0
            val body = response.body ?: throw IOException("下載內容為空")
            var received = offset
            var reportedPercent = (received * 100 / pack.archiveBytes).toInt()
            body.byteStream().use { input ->
                FileOutputStream(archive, append).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > pack.archiveBytes) throw IOException("模型檔大小超出預期")
                        output.write(buffer, 0, count)
                        val percent = (received * 100 / pack.archiveBytes).toInt()
                        if (percent != reportedPercent) {
                            reportedPercent = percent
                            onProgress(AsrDownloadProgress(pack.id, if (append) "正在續傳" else "正在下載", percent))
                        }
                    }
                }
            }
            if (received != pack.archiveBytes) {
                throw IOException("下載未完成，將從 $received 位元組繼續")
            }
            if (archive.sha256() != pack.archiveSha256) {
                archive.delete()
                throw IOException("模型下載校驗失敗，將重新下載")
            }
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.hex()
    }

    private suspend fun extractModelFiles(pack: AsrModelPack, archive: File, stage: File) {
        val found = mutableSetOf<String>()
        val prefix = "${pack.id}/"
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextEntry ?: break
                if (!entry.name.startsWith(prefix)) continue
                val name = entry.name.removePrefix(prefix)
                val expectedHash = pack.files[name] ?: continue
                if (!entry.isFile || entry.size < 0 || entry.size > MAX_MODEL_FILE_BYTES || !found.add(name)) {
                    throw IOException("模型封裝格式無效：$name")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                File(stage, name).outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = tar.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > entry.size) throw IOException("模型檔大小超出預期：$name")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
                if (copied != entry.size || digest.hex() != expectedHash) {
                    throw IOException("模型檔校驗失敗：$name")
                }
            }
        }
        if (found != pack.files.keys) {
            throw IOException("模型封裝缺少檔案：${(pack.files.keys - found).joinToString()}")
        }
    }

    private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_MODEL_FILE_BYTES = 512_000_000L
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

        fun partialBytes(context: Context, pack: AsrModelPack): Long =
            File(context.filesDir, "model-packs/downloads/${pack.id}.part").length()
    }
}

class HttpDownloadException(val statusCode: Int) : IOException("下載失敗：HTTP $statusCode")
