package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.data.AssistantSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** 可替換的語音合成器；正式實作為 OpenAI `/audio/speech`，測試可用 Fake。 */
fun interface SpeechSynthesizer {
    suspend fun synthesize(text: String): ByteArray
}

/**
 * OpenAI `/audio/speech` TTS 客戶端（spike）。
 *
 * 沿用 LLM 的 Base URL 與 API Key，只把 Endpoint 換成 `/audio/speech`；
 * Base URL 已含 `/v1` 時不會重複拼接（見 [ProviderUrl]）。
 * 成功時回傳完整音訊位元組（預設 mp3），由呼叫端自行播放。
 */
class OpenAiAudioSpeechClient(
    private val settings: AssistantSettings,
    private val apiKeyProvider: () -> CharArray?,
    clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
    private val allowHttp: Boolean = false,
) : SpeechSynthesizer, AutoCloseable {
    private val client = clientBuilder
        .connectTimeout(settings.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(settings.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun speechUrl(): String = ProviderUrl.normalize(settings.baseUrl, "/audio/speech", allowHttp)

    override suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        if (text.isBlank()) throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "沒有可朗讀的文字")
        if (text.length > AssistantSettings.CLOUD_TTS_MAX_CHARS) {
            throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "單次朗讀超過上限")
        }
        val apiKey = apiKeyProvider() ?: throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "尚未設定 API Key")
        val authorization = try {
            "Bearer ${apiKey.concatToString()}"
        } finally {
            apiKey.fill('\u0000')
        }
        val body = buildJsonObject {
            put("model", settings.cloudTtsModel)
            put("input", text)
            put("voice", settings.cloudTtsVoice)
            put("response_format", "mp3")
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(speechUrl())
            .header("Authorization", authorization)
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw mapHttpError(response.code)
                val bytes = response.body?.bytes()
                    ?: throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "回應沒有內容")
                if (bytes.isEmpty()) throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "回應沒有內容")
                bytes
            }
        } catch (error: LlmException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw LlmException(LlmErrorCode.NETWORK_TIMEOUT, message = "連線或讀取逾時", cause = error)
        } catch (error: IOException) {
            throw LlmException(LlmErrorCode.NETWORK_UNAVAILABLE, message = "網路連線失敗", cause = error)
        }
    }

    private fun mapHttpError(status: Int): LlmException = when (status) {
        400 -> LlmException(LlmErrorCode.RESPONSE_INVALID, status, message = "請求或設定無效")
        401, 403 -> LlmException(LlmErrorCode.AUTH_FAILED, status, message = "API Key 驗證失敗")
        404 -> LlmException(LlmErrorCode.NOT_FOUND, status, message = "找不到語音 Endpoint 或 Model")
        429 -> LlmException(LlmErrorCode.RATE_LIMITED, status, message = "請求過多")
        500, 502, 503, 504 -> LlmException(LlmErrorCode.SERVER_ERROR, status, message = "語音服務暫時無法使用")
        else -> LlmException(LlmErrorCode.RESPONSE_INVALID, status, message = "HTTP $status")
    }

    override fun close() {
        val executor = client.dispatcher.executorService
        runCatching {
            executor.execute {
                try {
                    client.connectionPool.evictAll()
                } finally {
                    executor.shutdown()
                }
            }
        }.onFailure {
            executor.shutdown()
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
