package com.va.voiceassistant.orchestration

import android.os.SystemClock

fun interface MonotonicClock {
    fun elapsedRealtimeMs(): Long
}

object AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
