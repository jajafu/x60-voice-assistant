package com.jajafu.x60assistant.asr

import com.jajafu.x60assistant.audio.AudioFrame

interface SpeechRecognizerEngine : AutoCloseable {
    val modelId: String
    fun beginSession()
    fun accept(frame: AudioFrame): String?
    fun finish(): AsrResult
    fun cancel()
    override fun close()
}

data class AsrResult(
    val text: String,
    val audioDurationMs: Long,
    val recognitionDurationMs: Long,
    val realTimeFactor: Double,
    val finalLatencyMs: Long,
)

class AsrModelInvalidException(message: String) : IllegalStateException(message)
