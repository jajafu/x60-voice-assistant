package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.data.AssistantSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

/** 可替換的語音轉文字器；正式實作為 OpenAI `/audio/transcriptions`，測試可用 Fake。 */
fun interface AudioTranscriber {
    /** 輸入 16 kHz／mono／PCM16 的 WAV 位元組，回傳辨識文字。同步呼叫，由呼叫端決定執行緒。 */
    fun transcribe(wavBytes: ByteArray): String

    fun cancel() = Unit
}

/**
 * OpenAI `/audio/transcriptions` STT 客戶端（spike，預設 `whisper-1`）。
 *
 * 沿用 LLM 的 Base URL 與 API Key。錄完一整句才上傳整段 WAV，
 * 與本機 VAD 切出的語句邊界一致；不做串流、不做即時字幕。
 */
class OpenAiAudioTranscriptionClient(
    private val settings: AssistantSettings,
    private val apiKeyProvider: () -> CharArray?,
    clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
    private val allowHttp: Boolean = false,
) : AudioTranscriber, AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = clientBuilder
        .connectTimeout(settings.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(settings.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val activeCall = AtomicReference<Call?>(null)

    fun transcriptionsUrl(): String = ProviderUrl.normalize(settings.baseUrl, "/audio/transcriptions", allowHttp)

    override fun transcribe(wavBytes: ByteArray): String {
        if (wavBytes.size <= WAV_HEADER_SIZE) {
            throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "沒有可辨識的語音")
        }
        val apiKey = apiKeyProvider() ?: throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "尚未設定 API Key")
        val authorization = try {
            "Bearer ${apiKey.concatToString()}"
        } finally {
            apiKey.fill('\u0000')
        }
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "utterance.wav",
                wavBytes.toRequestBody(WAV_MEDIA_TYPE),
            )
            .addFormDataPart("model", settings.cloudAsrModel)
            .apply {
                val language = settings.cloudAsrLanguage.trim()
                if (language.isNotEmpty()) addFormDataPart("language", language)
            }
            .build()
        val request = Request.Builder()
            .url(transcriptionsUrl())
            .header("Authorization", authorization)
            .post(multipart)
            .build()
        val call = client.newCall(request)
        activeCall.set(call)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw mapHttpError(response.code)
                val body = response.body?.string().orEmpty()
                parseText(body)
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

    private fun parseText(body: String): String = try {
        json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
    } catch (error: Throwable) {
        throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "JSON 回應格式無效", cause = error)
    }

    private fun mapHttpError(status: Int): LlmException = when (status) {
        400 -> LlmException(LlmErrorCode.RESPONSE_INVALID, status, message = "請求或設定無效")
        401, 403 -> LlmException(LlmErrorCode.AUTH_FAILED, status, message = "API Key 驗證失敗")
        404 -> LlmException(LlmErrorCode.NOT_FOUND, status, message = "找不到語音 Endpoint 或 Model")
        413 -> LlmException(LlmErrorCode.RESPONSE_INVALID, status, message = "音訊超過上限")
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
        private val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
        private const val WAV_HEADER_SIZE = 44
    }
}
