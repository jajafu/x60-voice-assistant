package com.va.voiceassistant.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads an APK inside the app (no browser popup) and opens the installer.
 *
 * Downloads are staged by release tag and keep their `.part` file after a
 * network failure or cancellation, so the next attempt can resume with HTTP
 * Range instead of starting over.
 */
object AppUpdateDownloader {
    private const val STAGED_APK_PREFIX = "x60-voice-assistant-"
    private const val MAX_REDIRECTS = 5

    fun updateApkFile(context: Context, versionTag: String): File {
        val directory = File(context.externalCacheDir ?: context.cacheDir, "updates")
            .apply { mkdirs() }
        val safeVersion = sanitizeFileName(versionTag).removeSuffix(".apk")
        return File(directory, "$STAGED_APK_PREFIX$safeVersion.apk")
    }

    fun partialApkFile(target: File): File = File(target.parentFile, "${target.name}.part")

    fun partialDownloadLength(target: File): Long {
        val partial = partialApkFile(target)
        return if (partial.isFile) partial.length().coerceAtLeast(0L) else 0L
    }

    /** Remove old staged releases so the cache does not retain one APK per version. */
    fun removeOtherStagedApks(keep: File) {
        val directory = keep.parentFile ?: return
        directory.listFiles()?.forEach { file ->
            if (file != keep && file.name.startsWith(STAGED_APK_PREFIX)) file.delete()
        }
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        versionTag: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        expectedSizeBytes: Long = 0,
        client: OkHttpClient = AppUpdateChecker.defaultDownloadClient(),
        maxRetries: Int = 5,
    ): File = withContext(Dispatchers.IO) {
        val target = updateApkFile(context, versionTag)
        downloadApkToFile(
            target = target,
            url = url,
            onProgress = onProgress,
            expectedSizeBytes = expectedSizeBytes,
            client = client,
            maxRetries = maxRetries,
        )
    }

    /**
     * File-based entry point kept separate from Android Context so the resume
     * protocol can be covered by JVM tests.
     */
    internal suspend fun downloadApkToFile(
        target: File,
        url: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        expectedSizeBytes: Long = 0,
        client: OkHttpClient = AppUpdateChecker.defaultDownloadClient(),
        maxRetries: Int = 5,
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val expected = expectedSizeBytes.takeIf { it > 0 }

        if (target.isFile && target.length() > 0L) {
            if (expected == null || target.length() == expected) {
                onProgress(target.length(), expected)
                return@withContext target
            }
            target.delete()
        }

        val partial = partialApkFile(target)
        var attempt = 0
        var lastError: Throwable = IOException("更新下載失敗")
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return@withContext downloadOnce(
                    url = url,
                    target = target,
                    partial = partial,
                    expectedSizeBytes = expected,
                    client = client,
                    onProgress = onProgress,
                )
            } catch (cancelled: CancellationException) {
                // Keep the partial file so a later tap can resume it.
                throw cancelled
            } catch (error: IOException) {
                lastError = error
            } catch (error: IllegalStateException) {
                lastError = error
            }

            attempt++
            if (attempt > maxRetries) {
                throw lastError
            }
            delay((1_000L * attempt).coerceAtMost(8_000L))
        }
        @Suppress("UNREACHABLE_CODE")
        throw lastError
    }

    private suspend fun downloadOnce(
        url: String,
        target: File,
        partial: File,
        expectedSizeBytes: Long?,
        client: OkHttpClient,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): File {
        currentCoroutineContext().ensureActive()
        var existing = partial.length().coerceAtLeast(0L)
        if (expectedSizeBytes != null && existing > expectedSizeBytes) {
            partial.delete()
            existing = 0L
        }

        var currentUrl = url
        var redirects = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder()
                .url(currentUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "x60-voice-assistant-updater")
                .apply { if (existing > 0L) header("Range", "bytes=$existing-") }
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    if (redirects++ >= MAX_REDIRECTS) {
                        throw IllegalStateException("更新下載失敗：重新導向次數過多")
                    }
                    val location = response.header("Location")
                        ?: throw IllegalStateException("更新下載失敗：重新導向缺少位置")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    return@use
                }

                val body = response.body
                when (response.code) {
                    206 -> {
                        val total = parseResumedTotal(response.header("Content-Range"))
                            ?: body?.contentLength()?.takeIf { it > 0 }?.let { existing + it }
                            ?: expectedSizeBytes
                        if (body == null) throw IOException("更新下載回應為空")
                        copyToPartial(
                            body.byteStream(),
                            partial,
                            append = existing > 0L,
                            baseOffset = existing,
                            total = total,
                            onProgress = onProgress,
                        )
                        return finishPartial(partial, target, expectedSizeBytes)
                    }

                    200 -> {
                        if (body == null) throw IOException("更新下載回應為空")
                        // A server may ignore Range. Restart safely rather than
                        // appending the complete APK to an old partial file.
                        val total = body.contentLength().takeIf { it > 0 } ?: expectedSizeBytes
                        copyToPartial(
                            body.byteStream(),
                            partial,
                            append = false,
                            baseOffset = 0L,
                            total = total,
                            onProgress = onProgress,
                        )
                        return finishPartial(partial, target, expectedSizeBytes)
                    }

                    416 -> {
                        if (existing > 0L) {
                            partial.delete()
                            existing = 0L
                            return@use
                        }
                        throw IllegalStateException("更新下載失敗：HTTP 416")
                    }

                    else -> throw IOException("更新下載失敗（HTTP ${response.code}）")
                }
            }
            // Redirects and a stale partial (HTTP 416) loop back here.
        }
    }

    private fun parseResumedTotal(contentRange: String?): Long? {
        if (contentRange != null) {
            val slash = contentRange.lastIndexOf('/')
            if (slash >= 0) {
                contentRange.substring(slash + 1).trim().toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?.let { return it }
            }
        }
        return null
    }

    private suspend fun copyToPartial(
        input: java.io.InputStream,
        partial: File,
        append: Boolean,
        baseOffset: Long,
        total: Long?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        partial.parentFile?.mkdirs()
        input.use { source ->
            FileOutputStream(partial, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = baseOffset
                onProgress(downloaded, total)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    onProgress(downloaded, total)
                }
                output.flush()
            }
        }
        if (partial.length() == 0L) throw IllegalStateException("更新檔為空")
        if (total != null && total > 0L && partial.length() < total) {
            throw EOFException("更新下載未完成：${partial.length()}/$total bytes")
        }
    }

    private fun finishPartial(partial: File, target: File, expectedSizeBytes: Long?): File {
        if (expectedSizeBytes != null && partial.length() != expectedSizeBytes) {
            throw IOException("更新檔大小不符，請繼續下載")
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
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
