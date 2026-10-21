package com.va.voiceassistant.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionTag: String,
    val apkUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    val notes: String,
)

/**
 * Checks the GitHub releases page for a newer APK. Pure parsing logic lives in
 * [parseLatestRelease] and [isNewerVersion] so it can be unit tested without network.
 */
object AppUpdateChecker {
    /**
     * App 未登入 GitHub，所以倉庫必須是公開且發佈過 release，檢查才會有結果；
     * 查無 release 時回 404，App 會保持安靜（見 checkForUpdate）。
     * 主倉庫已開源（1.0.12 起），更新檢查與 APK 下載直接指向主倉庫的 releases，
     * 不再使用舊架構的獨立公開倉庫。
     */
    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/jajafu/x60-voice-assistant/releases/latest"

    // Match MusicCabin's updater: foreground checks use the in-process cache,
    // while an explicit button tap can force a fresh request.
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L
    private val cacheLock = Any()
    private var cachedApiUrl: String? = null
    private var cachedRelease: AppUpdateInfo? = null
    private var hasCachedResult = false
    private var lastCheckTime = 0L

    private val json = Json { ignoreUnknownKeys = true }

    fun parseLatestRelease(payload: String): AppUpdateInfo? {
        val root = json.parseToJsonElement(payload).jsonObject
        val tag = root["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (tag.isEmpty()) return null
        val notes = root["body"]?.jsonPrimitive?.content.orEmpty()
        val assets = root["assets"]?.jsonArray ?: return null
        // 同一 release 有多顆 APK 時優先拿 release 版，避免誤裝 debug 簽章。
        val apkAssets = assets.mapNotNull {
            runCatching { it.jsonObject }.getOrNull()
        }.filter { obj ->
            obj["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true &&
                obj["browser_download_url"]?.jsonPrimitive?.content?.isNotEmpty() == true
        }
        if (apkAssets.isEmpty()) return null
        val chosen = apkAssets.firstOrNull { obj ->
            obj["name"]!!.jsonPrimitive.content.contains("release", ignoreCase = true)
        } ?: apkAssets.first()
        val name = chosen["name"]!!.jsonPrimitive.content
        val url = chosen["browser_download_url"]!!.jsonPrimitive.content
        val size = chosen["size"]?.jsonPrimitive?.long ?: 0L
        return AppUpdateInfo(tag, url, name, size, notes)
    }

    /** Compares version strings like "1.0.6" vs "1.0.5"; leading "v" is ignored. */
    fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val remote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val local = localVersion.trim().removePrefix("v").removePrefix("V")
        if (remote.isEmpty() || local.isEmpty()) return false
        if (remote == local) return false
        // Prerelease tags (e.g. 1.0.6-rc1) never count as newer than their stable core.
        val remoteCore = remote.substringBefore('-')
        val localCore = local.substringBefore('-')
        val remoteParts = remoteCore.split('.')
        val localParts = localCore.split('.')
        val length = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until length) {
            val right = remoteParts.getOrNull(index)?.toIntOrNull() ?: 0
            val left = localParts.getOrNull(index)?.toIntOrNull() ?: 0
            if (right != left) return right > left
        }
        // Same numeric core: stable beats prerelease, otherwise fall back to string order.
        val remotePre = remote.substringAfter('-', "")
        val localPre = local.substringAfter('-', "")
        if (remotePre.isEmpty() && localPre.isNotEmpty()) return true
        if (remotePre.isNotEmpty() && localPre.isEmpty()) return false
        return remote > local
    }

    suspend fun checkForUpdate(
        localVersionName: String,
        client: OkHttpClient = defaultClient(),
        apiUrl: String = LATEST_RELEASE_URL,
        forceRefresh: Boolean = false,
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val cached = synchronized(cacheLock) {
            if (!forceRefresh &&
                hasCachedResult &&
                cachedApiUrl == apiUrl &&
                System.currentTimeMillis() - lastCheckTime < CHECK_INTERVAL_MILLIS
            ) {
                cachedRelease
            } else {
                CACHE_MISS
            }
        }
        if (cached !== CACHE_MISS) {
            return@withContext cached as AppUpdateInfo?
        }

        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "x60-voice-assistant-updater")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            // No releases yet, repo renamed, or (for private repos) no access: stay silent.
            if (response.code == 404) {
                cacheResult(apiUrl, null)
                return@withContext null
            }
            if (!response.isSuccessful) throw IOException("檢查更新失敗（HTTP ${response.code}）")
            val payload = response.body?.string().orEmpty()
            val info = parseLatestRelease(payload)
            cacheResult(apiUrl, info)
            if (info != null && isNewerVersion(info.versionTag, localVersionName)) info else null
        }
    }

    private fun cacheResult(apiUrl: String, release: AppUpdateInfo?) {
        synchronized(cacheLock) {
            cachedApiUrl = apiUrl
            cachedRelease = release
            hasCachedResult = true
            lastCheckTime = System.currentTimeMillis()
        }
    }

    private object CACHE_MISS

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** The release asset redirects from GitHub to an object-storage URL. */
    fun defaultDownloadClient(): OkHttpClient = defaultClient().newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}
