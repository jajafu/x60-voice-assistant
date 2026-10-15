package com.va.voiceassistant.vad

import com.va.voiceassistant.audio.AudioFrame

interface VadEngine : AutoCloseable {
    fun reset()
    fun accept(frame: AudioFrame): VadObservation
    override fun close()
}

data class VadObservation(
    val speechDetected: Boolean,
    val probability: Float? = null,
)

data class SpeechEndpointConfig(
    val waitForSpeechMs: Long = 5_000,
    val minSpeechMs: Long = 250,
    val trailingSilenceMs: Long = 800,
    val maxUtteranceMs: Long = 20_000,
) {
    init {
        require(waitForSpeechMs in 3_000..10_000)
        require(minSpeechMs in 150..600)
        require(trailingSilenceMs in 500..1_500)
        require(maxUtteranceMs in 10_000..60_000)
    }
}

sealed interface SpeechEndpointEvent {
    data object None : SpeechEndpointEvent
    data object SpeechStarted : SpeechEndpointEvent
    data object NoSpeechTimeout : SpeechEndpointEvent
    data object SegmentTooShort : SpeechEndpointEvent
    data object Endpoint : SpeechEndpointEvent
    data object MaxDuration : SpeechEndpointEvent
}
