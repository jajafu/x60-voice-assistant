package com.va.voiceassistant.audio

data class AudioFrame(
    val samples: ShortArray,
    val elapsedRealtimeNanos: Long,
)
