package com.jajafu.x60assistant.orchestration

import android.os.SystemClock

fun interface MonotonicClock {
    fun elapsedRealtimeMs(): Long
}

object AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
