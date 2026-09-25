package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.data.AssistantSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class OpenAiCompatibleLlmProvider(
    private val settings: AssistantSettings,
    private val apiKeyProvider: () -> CharArray?,
    clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
    private val allowHttp: Boolean = false,
    private val random: Random = Random.Default,
) : LlmProvider {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false; explicitNulls = false }
    private val activeCall = AtomicReference<Call?>(null)
    private val client = clientBuilder
        .connectTimeout(settings.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(settings.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val url = ProviderUrl.normalize(settings.baseUrl, settings.endpoint, allowHttp)

    override suspend fun healthCheck(): ProviderHealth = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        try {
            var attempt = 0
            var result: ProviderHealth? = null
            while (result == null) {
                val response = execute(
                    ChatRequest(
                        messages = ConversationHistory(0).messages(
                            settings.systemInstruction,
                            "請只回答：好",
                        ),
                        maxOutputTokens = 1,
                        temperature = 0.0,
                        requestId = "health-check",
                    ),
                    stream = false,
                )
                response.use {
                    val latency = (System.nanoTime() - started) / 1_000_000
                    if (it.isSuccessful) {
                        parseNonStreaming(it.body?.string().orEmpty())
                        result = ProviderHealth(
                            true,
                            "完成",
                            it.code,
                            latency,
                            "DNS、TLS、認證、Model 與 Endpoint 均可用",
                        )
                    } else {
                        val decision = RetryPolicy.decide(
                            attempt = attempt,
                            httpStatus = it.code,
                            jitterMs = random.nextLong(0, 251),
                        )
                        if (!decision.retry) {
                            result = mapHealthError(it.code, latency, it.header("Retry-After"))
                        } else {
                            attempt++
                            delay(decision.delayMs)
                        }
                    }
                }
            }
            result
        } catch (error: Throwable) {
            val latency = (System.nanoTime() - started) / 1_000_000
            when (error) {
                is UnknownHostException -> ProviderHealth(false, "DNS", latencyMs = latency, message = "無法解析主機")
                is SocketTimeoutException -> ProviderHealth(false, "Timeout", latencyMs = latency, message = "連線或讀取逾時")
                is LlmException -> ProviderHealth(false, "回應", error.httpStatus, latency, error.message.orEmpty())
                else -> ProviderHealth(false, "網路", latencyMs = latency, message = "連線失敗：${error.javaClass.simpleName}")
            }
        } finally {
            activeCall.set(null)
        }
    }

    override fun streamChat(request: ChatRequest): Flow<LlmChunk> = flow {
        var attempt = 0
        var emitted = false
        val pendingChunks = ArrayDeque<String>()
        while (true) {
            try {
                val response = execute(request, stream = settings.streaming)
                response.use {
                    if (!it.isSuccessful) {
                        val decision = RetryPolicy.decide(attempt, httpStatus = it.code, responseStarted = emitted, jitterMs = random.nextLong(0, 251))
                        if (decision.retry) {
                            attempt++
                            delay(decision.delayMs)
                            return@use
                        }
                        throw mapHttpError(it.code, it.header("Retry-After"))
                    }
                    if (!settings.streaming) {
                        val text = parseNonStreaming(it.body?.string().orEmpty())
                        if (text.isNotBlank()) emit(LlmChunk(text))
                        return@flow
                    }

                    var done = false
                    val parser = SseParser { data ->
                        if (data == "[DONE]") {
                            done = true
                        } else {
                            parseStreamingChunk(data)?.let { chunk -> pendingChunks.add(chunk) }
                        }
                    }
                    val source = it.body?.source() ?: throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "回應沒有內容")
                    while (!source.exhausted() && !done) {
                        parser.acceptLine(source.readUtf8Line())
                        while (pendingChunks.isNotEmpty()) {
                            emitted = true
                            emit(LlmChunk(pendingChunks.removeFirst()))
                        }
                    }
                    parser.finish()
                    while (pendingChunks.isNotEmpty()) {
                        emitted = true
                        emit(LlmChunk(pendingChunks.removeFirst()))
                    }
                    if (!done) throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "SSE 在 [DONE] 前中斷")
                    return@flow
                }
            } catch (cancelled: CancellationException) {
                cancel()
                throw cancelled
            } catch (failure: Throwable) {
                val decision = RetryPolicy.decide(attempt, failure = failure, responseStarted = emitted, jitterMs = random.nextLong(0, 251))
                if (!decision.retry) throw mapNetworkError(failure)
                attempt++
                delay(decision.delayMs)
            } finally {
                activeCall.set(null)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun execute(request: ChatRequest, stream: Boolean): okhttp3.Response {
        val apiKey = apiKeyProvider() ?: throw LlmException(LlmErrorCode.CONFIG_INVALID, message = "尚未設定 API Key")
        val authorization = try {
            "Bearer ${apiKey.concatToString()}"
        } finally {
            apiKey.fill('\u0000')
        }
        val body = buildRequestJson(request, stream).toString().toRequestBody(JSON_MEDIA_TYPE)
        val call = client.newCall(
            Request.Builder()
                .url(url)
                .header("Authorization", authorization)
                .header("Accept", if (stream) "text/event-stream" else "application/json")
                .header("X-Request-ID", request.requestId.take(64))
                .post(body)
                .build(),
        )
        activeCall.set(call)
        return call.execute()
    }

    private fun buildRequestJson(request: ChatRequest, stream: Boolean): JsonObject = buildJsonObject {
        put("model", settings.model)
        put("stream", stream)
        put("temperature", JsonPrimitive(request.temperature))
        put("max_tokens", request.maxOutputTokens)
        put("messages", buildJsonArray {
            request.messages.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        })
    }

    private fun parseStreamingChunk(data: String): String? {
        val root = parseRoot(data)
        root["error"]?.let { throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "Provider 回傳錯誤") }
        val choices = root["choices"] as? JsonArray
            ?: throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "缺少 choices")
        if (choices.isEmpty()) return null
        val delta = choices.first().jsonObject["delta"]?.jsonObject ?: return null
        return delta["content"]?.jsonPrimitive?.contentOrNull
    }

    private fun parseNonStreaming(body: String): String {
        val root = parseRoot(body)
        val choices = root["choices"]?.jsonArray
            ?: throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "缺少 choices")
        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "缺少 message")
        return message["content"]?.jsonPrimitive?.contentOrNull
            ?: throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "缺少回答內容")
    }

    private fun parseRoot(value: String): JsonObject = try {
        json.parseToJsonElement(value).jsonObject
    } catch (error: Throwable) {
        throw LlmException(LlmErrorCode.RESPONSE_INVALID, message = "JSON 回應格式無效", cause = error)
    }

    private fun mapHttpError(status: Int, retryAfter: String?): LlmException = when (status) {
        400 -> LlmException(LlmErrorCode.RESPONSE_INVALID, status, message = "請求或設定無效")
        401, 403 -> LlmException(LlmErrorCode.AUTH_FAILED, status, message = "API Key 驗證失敗")
        404 -> LlmException(LlmErrorCode.NOT_FOUND, status, message = "找不到 Endpoint 或 Model")
        429 -> LlmException(LlmErrorCode.RATE_LIMITED, status, retryAfter?.toLongOrNull(), "請求過多")
        500, 502, 503, 504 -> LlmException(LlmErrorCode.SERVER_ERROR, status, message = "Provider 暫時無法服務")
        else -> LlmException(LlmErrorCode.RESPONSE_INVALID, status, message = "HTTP $status")
    }

    private fun mapHealthError(status: Int, latency: Long, retryAfter: String?): ProviderHealth {
        val error = mapHttpError(status, retryAfter)
        return ProviderHealth(false, when (status) {
            401, 403 -> "認證"
            404 -> "Endpoint / Model"
            429 -> "Rate limit"
            else -> "HTTP"
        }, status, latency, error.message.orEmpty())
    }

    private fun mapNetworkError(error: Throwable): Throwable = when (error) {
        is LlmException -> error
        is SocketTimeoutException -> LlmException(LlmErrorCode.NETWORK_TIMEOUT, message = "連線或讀取逾時", cause = error)
        is IOException -> LlmException(LlmErrorCode.NETWORK_UNAVAILABLE, message = "網路連線失敗", cause = error)
        else -> error
    }

    override fun cancel() {
        activeCall.getAndSet(null)?.cancel()
    }

    override fun close() {
        cancel()
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
