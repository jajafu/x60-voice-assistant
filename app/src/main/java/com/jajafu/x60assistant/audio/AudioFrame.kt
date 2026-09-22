package com.jajafu.x60assistant.audio

data class AudioFrame(
    val samples: ShortArray,
    val elapsedRealtimeNanos: Long,
)
