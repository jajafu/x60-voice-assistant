package com.va.voiceassistant.audio

interface AudioInputEngine {
    val frameQueue: BoundedAudioFrameQueue
    fun start(): String
    fun stop()
    fun isRunning(): Boolean
    suspend fun nextFrame(timeoutMs: Long): AudioFrame?
}
