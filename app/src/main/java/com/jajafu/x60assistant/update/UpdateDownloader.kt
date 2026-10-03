package com.jajafu.x60assistant.update

import android.content.Context
import com.jajafu.x60assistant.model.Sha256
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 下載 Release APK 到 App 私有快取，SHA-256 通過才交付。
 * 驗證失敗會刪除暫存檔並回傳 [UpdateDownload.Failed]。
 */
class UpdateDownloader(
    private val client: OkHttpClient = UpdateChecker.defaultHttpClient(),
    private val validateUrl: (String) -> String = UpdateUrls::requireTrustedHttps,
) {
    fun download(info: UpdateInfo, targetDir: File): Flow<UpdateDownload> = flow {
        emit(UpdateDownload.Progress(0, -1))
        try {
            val url = validateUrl(info.apkUrl)
            if (!targetDir.isDirectory) targetDir.mkdirs()
            val part = File(targetDir, APK_FILE_NAME + PART_SUFFIX)
            if (part.exists()) part.delete()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateException(UpdateFailure.DOWNLOAD_FAILED, "下載回應 ${response.code}")
                }
                val body = response.body
                    ?: throw UpdateException(UpdateFailure.DOWNLOAD_FAILED, "下載內容為空")
                val total = body.contentLength()
                var read = 0L
                part.outputStream().buffered().use { out ->
                    body.byteStream().buffered().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            read += count
                            emit(UpdateDownload.Progress(read, total))
                        }
                    }
                }
            }
            val actual = Sha256.digest(part.inputStream()).lowercase()
            if (actual != info.sha256.lowercase()) {
                part.delete()
                throw UpdateException(UpdateFailure.VERIFY_FAILED, "")
            }
            val apk = File(targetDir, APK_FILE_NAME)
            if (apk.exists()) apk.delete()
            if (!part.renameTo(apk)) {
                part.delete()
                throw UpdateException(UpdateFailure.DOWNLOAD_FAILED, "無法寫入安裝檔")
            }
            emit(UpdateDownload.Done(apk))
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            emit(UpdateDownload.Failed(e.failure, e.message.orEmpty()))
        } catch (_: Exception) {
            emit(UpdateDownload.Failed(UpdateFailure.DOWNLOAD_FAILED, ""))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val APK_FILE_NAME = "x60-update.apk"
        private const val PART_SUFFIX = ".part"

        fun updateDir(context: Context): File = File(context.cacheDir, "updates")
    }
}
