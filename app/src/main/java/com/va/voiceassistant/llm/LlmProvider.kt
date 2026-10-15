package com.va.voiceassistant.llm

import kotlinx.coroutines.flow.Flow

data class ChatMessage(val role: String, val content: String)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val maxOutputTokens: Int,
    val temperature: Double,
    val requestId: String,
)

data class LlmChunk(val text: String)

data class ProviderHealth(
    val success: Boolean,
    val stage: String,
    val httpStatus: Int? = null,
    val latencyMs: Long,
    val message: String,
)

interface LlmProvider : AutoCloseable {
    suspend fun healthCheck(): ProviderHealth
    fun streamChat(request: ChatRequest): Flow<LlmChunk>
    fun cancel()
    override fun close()
}

class LlmException(
    val code: LlmErrorCode,
    val httpStatus: Int? = null,
    val retryAfterSeconds: Long? = null,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class LlmErrorCode {
    CONFIG_INVALID,
    AUTH_FAILED,
    NOT_FOUND,
    RATE_LIMITED,
    NETWORK_TIMEOUT,
    NETWORK_UNAVAILABLE,
    SERVER_ERROR,
    RESPONSE_INVALID,
    CANCELLED,
}
