package com.va.voiceassistant.model

import android.content.Context
import com.va.voiceassistant.asr.AsrModelPacks
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/** Installs the bundled X-ASR archive into the file paths required by sherpa-onnx. */
class BundledAsrInstaller(private val context: Context) {
    private val pack = AsrModelPacks.xAsr
    private val manager = ModelPackManager(context)

    suspend fun ensureInstalled(): ModelPackStatus = withContext(Dispatchers.IO) {
        val target = manager.asrDirectory(pack)
        val stage = File(target.parentFile, "${pack.id}.staging")
        val backup = File(target.parentFile, "${pack.id}.backup")
        if (!target.exists() && backup.exists() && !backup.renameTo(target)) {
            throw IOException("無法還原既有模型")
        }
        manager.verifyAsrPack(pack).takeIf { it.valid }?.let { return@withContext it }

        val assetPath = "models/asr/${pack.archiveName}"
        val archiveHash = try {
            Sha256.digest(context.assets.open(assetPath))
        } catch (error: IOException) {
            throw IOException("APK 缺少內建 X-ASR 模型", error)
        }
        if (archiveHash != pack.archiveSha256) throw IOException("內建 X-ASR 壓縮檔校驗失敗")

        if (!stage.parentFile!!.isDirectory && !stage.parentFile!!.mkdirs()) {
            throw IOException("無法建立模型資料夾")
        }
        stage.deleteRecursively()
        try {
            if (!stage.mkdirs()) throw IOException("無法建立模型暫存資料夾")
            extract(assetPath, stage)
            if (backup.exists() && !backup.deleteRecursively()) throw IOException("無法清除舊模型備份")
            if (target.exists() && !target.renameTo(backup)) throw IOException("無法保留舊模型")
            if (!stage.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IOException("無法安裝內建模型")
            }
            val installed = manager.verifyAsrPack(pack)
            if (!installed.valid) {
                target.deleteRecursively()
                if (backup.exists()) backup.renameTo(target)
                throw IOException("內建模型驗證失敗：${installed.reason}")
            }
            backup.deleteRecursively()
            installed
        } finally {
            stage.deleteRecursively()
        }
    }

    private suspend fun extract(assetPath: String, stage: File) {
        val found = mutableSetOf<String>()
        val prefix = "${pack.id}/"
        TarArchiveInputStream(BZip2CompressorInputStream(context.assets.open(assetPath).buffered())).use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextEntry ?: break
                if (!entry.name.startsWith(prefix)) continue
                val name = entry.name.removePrefix(prefix)
                val expectedHash = pack.files[name] ?: continue
                if (!entry.isFile || entry.size < 0 || entry.size > MAX_MODEL_FILE_BYTES || !found.add(name)) {
                    throw IOException("內建模型封裝格式無效：$name")
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
                        if (copied > entry.size) throw IOException("內建模型檔大小超出預期：$name")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (copied != entry.size || hash != expectedHash) {
                    throw IOException("內建模型檔校驗失敗：$name")
                }
            }
        }
        if (found != pack.files.keys) {
            throw IOException("內建模型封裝缺少檔案：${(pack.files.keys - found).joinToString()}")
        }
    }

    private companion object {
        const val MAX_MODEL_FILE_BYTES = 512_000_000L
    }
}
