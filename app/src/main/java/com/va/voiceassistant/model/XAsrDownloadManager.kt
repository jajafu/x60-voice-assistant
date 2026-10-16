package com.va.voiceassistant.model

import android.content.Context
import com.va.voiceassistant.asr.AsrModelPacks
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Downloads the X-ASR archive from the user's Drive link and installs it.
 *
 * Progress is reported as 0..100 for the download phase; the extract phase reports -1.
 * Callers must run [downloadAndInstall] off the main thread (it uses Dispatchers.IO).
 */
class XAsrDownloadManager(
    private val context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    private val pack = AsrModelPacks.xAsr
    @Volatile private var currentCall: okhttp3.Call? = null

    /** Cancels an in-flight download; the awaiting [downloadAndInstall] fails. */
    fun cancel() {
        currentCall?.cancel()
    }

    suspend fun downloadAndInstall(onProgress: (Int) -> Unit): ModelPackStatus =
        withContext(Dispatchers.IO) {
            val downloadsDir = File(context.filesDir, "model-packs/downloads").apply { mkdirs() }
            val part = File(downloadsDir, "${pack.archiveName}.part")
            val archive = File(downloadsDir, pack.archiveName)
            part.delete()
            try {
                val request = Request.Builder().url(pack.sourceUrl).get().build()
                val call = client.newCall(request)
                currentCall = call
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("模型下載失敗（HTTP ${response.code}）")
                        // Drive 病毒掃描提示頁是 HTML；真正的壓縮檔是 bzip2，提早擋掉避免浪費流量。
                        val contentType = response.header("Content-Type").orEmpty()
                        if (contentType.contains("text/html")) {
                            throw IOException("雲端連結回傳網頁而非模型檔，請確認共用設定為知道連結者可檢視")
                        }
                        val body = response.body ?: throw IOException("模型下載回應為空")
                        val total = body.contentLength()
                        var copied = 0L
                        var lastReported = -1
                        body.byteStream().use { input ->
                            part.outputStream().buffered().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    copied += count
                                    if (total > 0) {
                                        val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                                        if (percent != lastReported) {
                                            lastReported = percent
                                            onProgress(percent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    currentCall = null
                }
                if (!part.renameTo(archive)) throw IOException("無法儲存下載的模型檔")
                onProgress(-1)
                try {
                    BundledAsrInstaller(context).installFromArchive(archive)
                } finally {
                    // 壓縮檔已解壓，不再佔用 136 MB。
                    archive.delete()
                }
            } finally {
                part.delete()
            }
        }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
