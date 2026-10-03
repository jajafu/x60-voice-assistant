package com.jajafu.x60assistant.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 從 GitHub Releases 檢查新版。流程：
 * `releases/latest` → 找 `update.json` 資產 → 解析比對 versionCode。
 * 任何失敗都回傳 [UpdateCheckResult.Failed]，不拋例外、不擋主流程。
 */
class UpdateChecker(
    private val client: OkHttpClient = defaultHttpClient(),
    private val latestUrl: String = DEFAULT_LATEST_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val validateUrl: (String) -> String = UpdateUrls::requireTrustedHttps,
) {
    suspend fun checkLatest(currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val release = json.decodeFromString<GithubRelease>(getText(validateUrl(latestUrl)))
            val manifestAsset = release.assets.firstOrNull { it.name == MANIFEST_ASSET }
                ?: return@withContext UpdateCheckResult.Failed(
                    UpdateFailure.INVALID_RESPONSE,
                    "Release 沒有 update.json",
                )
            val info = json.decodeFromString<UpdateInfo>(
                getText(validateUrl(manifestAsset.browserDownloadUrl)),
            )
            validate(info)
            if (info.isNewerThan(currentVersionCode)) UpdateCheckResult.Available(info)
            else UpdateCheckResult.UpToDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            UpdateCheckResult.Failed(e.failure, e.message.orEmpty())
        } catch (_: IOException) {
            UpdateCheckResult.Failed(UpdateFailure.NETWORK, "")
        } catch (_: SerializationException) {
            UpdateCheckResult.Failed(UpdateFailure.INVALID_RESPONSE, "JSON 格式無效")
        } catch (_: IllegalArgumentException) {
            UpdateCheckResult.Failed(UpdateFailure.INVALID_RESPONSE, "")
        }
    }

    private fun getText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateException(UpdateFailure.NETWORK, "更新伺服器回應 ${response.code}")
            }
            return response.body?.string()
                ?: throw UpdateException(UpdateFailure.INVALID_RESPONSE, "更新回應為空")
        }
    }

    private fun validate(info: UpdateInfo) {
        validateUrl(info.apkUrl)
        require(info.versionCode > 0) { "versionCode 無效" }
        require(info.versionName.isNotBlank()) { "versionName 為空" }
        require(SHA256_PATTERN.matches(info.sha256)) { "SHA-256 無效" }
    }

    companion object {
        const val DEFAULT_LATEST_URL = "https://api.github.com/repos/jajafu/x60-voice-assistant/releases/latest"
        const val MANIFEST_ASSET = "update.json"
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    @SerialName("assets")
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    @SerialName("name")
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
)
