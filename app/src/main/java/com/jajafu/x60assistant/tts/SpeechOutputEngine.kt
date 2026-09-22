package com.jajafu.x60assistant.tts

data class TtsStatus(
    val available: Boolean,
    val engine: String = "—",
    val voice: String = "—",
    val locale: String = "—",
    val message: String = "",
)

interface SpeechOutputEngine : AutoCloseable {
    suspend fun initialize(): TtsStatus
    suspend fun speak(text: String)
    fun stop()
    override fun close()
}

class TtsException(message: String, cause: Throwable? = null) : Exception(message, cause)
