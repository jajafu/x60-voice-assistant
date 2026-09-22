package com.jajafu.x60assistant.llm

import java.net.ConnectException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import kotlin.math.min

data class RetryDecision(val retry: Boolean, val delayMs: Long = 0)

object RetryPolicy {
    fun decide(
        attempt: Int,
        httpStatus: Int? = null,
        failure: Throwable? = null,
        responseStarted: Boolean = false,
        jitterMs: Long = 0,
    ): RetryDecision {
        val maxRetries = when {
            httpStatus in setOf(500, 502, 503, 504) -> 2
            failure is UnknownHostException || failure is ConnectException -> 2
            failure is SocketTimeoutException && !responseStarted -> 1
            else -> 0
        }
        if (attempt >= maxRetries) return RetryDecision(false)
        val base = min(2_000L, 250L shl attempt)
        return RetryDecision(true, base + jitterMs.coerceIn(0, 250))
    }
}
