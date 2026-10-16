package com.va.voiceassistant.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads an APK inside the app (no browser popup) and opens the installer.
 */
object AppUpdateDownloader {

    suspend fun downloadApk(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit,
        expectedSizeBytes: Long = 0,
        client: OkHttpClient = AppUpdateChecker.defaultClient(),
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // 檔名來自 GitHub API，先清掉路徑符號，避免寫到資料夾外面。
        val safeName = sanitizeFileName(fileName)
        val part = File(updatesDir, "$safeName.part")
        val target = File(updatesDir, safeName)
        part.delete()
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("更新下載失敗（HTTP ${response.code}）")
                val body = response.body ?: throw IOException("更新下載回應為空")
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
            target.delete()
            if (!part.renameTo(target)) throw IOException("無法儲存更新檔")
            if (expectedSizeBytes > 0 && target.length() != expectedSizeBytes) {
                target.delete()
                throw IOException("更新檔大小不符，請重新下載")
            }
            target
        } finally {
            part.delete()
        }
    }

    /** API 回傳的檔名只取最後一段並濾掉非法字元；清完是空的就用預設名。 */
    fun sanitizeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = base.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        if (cleaned.isBlank() || cleaned.all { it == '.' }) return "update.apk"
        return cleaned
    }

    /** Android 8+ 需要「安裝未知應用」授權，否則系統會擋下安裝。 */
    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
