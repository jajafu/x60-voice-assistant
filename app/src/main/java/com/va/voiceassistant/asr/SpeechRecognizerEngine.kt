package com.va.voiceassistant.asr

import com.va.voiceassistant.audio.AudioFrame

interface SpeechRecognizerEngine : AutoCloseable {
    val modelId: String

    /**
     * 引擎自己出麥克風收整句（系統語音辨識服務）才為真：聆聽期間共用 AudioRecord
     * 必須先停掉，由 `VoiceOrchestrator` 交棒給系統；本機模型引擎一律走音框路徑。
     */
    val takesOverMicrophone: Boolean get() = false

    fun beginSession()
    fun accept(frame: AudioFrame): String?
    fun finish(): AsrResult
    /** Cancels only an in-flight blocking request, without interrupting native decode. */
    fun cancelActiveRequest() = Unit
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
