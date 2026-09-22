package com.jajafu.x60assistant.orchestration

class FollowUpPolicy(seconds: Int) {
    val durationMs: Long = seconds.coerceIn(0, 30) * 1_000L
    fun expired(startedMs: Long, nowMs: Long): Boolean = nowMs - startedMs >= durationMs
}
