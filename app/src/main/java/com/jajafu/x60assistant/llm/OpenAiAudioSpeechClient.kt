package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.audio.RiffWav
import com.jajafu.x60assistant.data.AssistantSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

/** 可替換的語音合成器；正式實作為 OpenAI `/audio/speech`，測試可用 Fake。 */
fun interface SpeechSynthesizer {
    suspend fun synthesize(text: String): ByteArray

    fun cancel() = Unit
}

/**
 * OpenAI `/audio/speech` TTS 客戶端（spike）。
 *
 * 使用獨立 Audio Provider 的 Base URL 與 API Key，只把 Endpoint 換成 `/audio/speech`；
 * Base URL 已含 `/v1` 時不會重複拼接（見 [ProviderUrl]）。
 * 成功時回傳完整 WAV 音訊位元組，由呼叫端自行播放。
 *
 * 請求已指定 `response_format=wav`，但部分 Provider 會忽略它而回傳 MP3 或錯誤內容。
 * 這裡在送出播放前驗證 RIFF/WAVE 檔頭：不合就回報實際收到的 Content-Type 與檔頭位元組，
 * 取代 `MediaPlayer` 不透明且難以診斷的 `1/-2147483648`。
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
        // 非串流的整句語音：總逾時與讀取逾時一致，避免慢速回應無上限佔住呼叫執行緒。
        .callTimeout(settings.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val activeCall = AtomicReference<Call?>(null)

    fun speechUrl(): String = ProviderUrl.normalize(settings.audioBaseUrl, "/audio/speech", allowHttp)

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
            put("response_format", "wav")
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(speechUrl())
            .header("Authorization", authorization)
            .post(body)
            .build()
        val call = client.newCall(request)
        activeCall.set(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw mapHttpError(response.code)
                val bytes = response.body?.bytes()
                    ?: throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "回應沒有內容")
                if (bytes.isEmpty()) throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "回應沒有內容")
                if (!RiffWav.isWave(bytes)) {
                    // 只記錄 Content-Type 與檔頭，不記錄音訊或 API Key。
                    val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()
                    throw LlmException(
                        LlmErrorCode.RESPONSE_INVALID,
                        message = "Audio Provider 未回傳 WAV（Content-Type=${contentType ?: "無"}，" +
                            "檔頭 ${RiffWav.signature(bytes)}）；請確認該 Provider 支援 /audio/speech 的 wav 格式",
                    )
                }
                bytes
            }
        } catch (error: LlmException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw LlmException(LlmErrorCode.NETWORK_TIMEOUT, message = "連線或讀取逾時", cause = error)
        } catch (error: IOException) {
            throw LlmException(LlmErrorCode.NETWORK_UNAVAILABLE, message = "網路連線失敗", cause = error)
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    override fun cancel() {
        activeCall.getAndSet(null)?.cancel()
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
        cancel()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
