package com.jajafu.x60assistant.audio

interface AudioInputEngine {
    val frameQueue: BoundedAudioFrameQueue
    fun start(): String
    fun stop()
    fun isRunning(): Boolean
    suspend fun nextFrame(timeoutMs: Long): AudioFrame?
}
