package com.jajafu.x60assistant.vad

class SpeechEndpointDetector(
    private val config: SpeechEndpointConfig,
) {
    private var sessionStartedMs = 0L
    private var speechStartedMs: Long? = null
    private var lastSpeechMs: Long? = null
    private var finished = false

    fun reset(nowMs: Long) {
        sessionStartedMs = nowMs
        speechStartedMs = null
        lastSpeechMs = null
        finished = false
    }

    fun accept(speechDetected: Boolean, nowMs: Long): SpeechEndpointEvent {
        if (finished) return SpeechEndpointEvent.None
        val started = speechStartedMs
        if (started == null) {
            if (speechDetected) {
                speechStartedMs = nowMs
                lastSpeechMs = nowMs
                return SpeechEndpointEvent.SpeechStarted
            }
            if (nowMs - sessionStartedMs >= config.waitForSpeechMs) {
                finished = true
                return SpeechEndpointEvent.NoSpeechTimeout
            }
            return SpeechEndpointEvent.None
        }

        if (speechDetected) lastSpeechMs = nowMs
        val speechDuration = nowMs - started
        if (speechDuration >= config.maxUtteranceMs) {
            finished = true
            return SpeechEndpointEvent.MaxDuration
        }
        val silenceDuration = nowMs - (lastSpeechMs ?: started)
        if (!speechDetected && silenceDuration >= config.trailingSilenceMs) {
            finished = true
            return if ((lastSpeechMs ?: started) - started < config.minSpeechMs) {
                SpeechEndpointEvent.SegmentTooShort
            } else {
                SpeechEndpointEvent.Endpoint
            }
        }
        return SpeechEndpointEvent.None
    }
}
