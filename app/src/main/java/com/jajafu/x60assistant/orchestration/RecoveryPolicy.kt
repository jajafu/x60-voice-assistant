package com.jajafu.x60assistant.orchestration

data class RecoveryDecision(val recover: Boolean, val delayMs: Long, val failuresInWindow: Int)

class RecoveryPolicy(
    private val windowMs: Long = 10 * 60 * 1_000L,
    private val maxFailuresInWindow: Int = 5,
    private val delaysMs: List<Long> = listOf(0, 1_000, 2_000, 4_000, 8_000, 30_000),
) {
    private val failures = ArrayDeque<Long>()

    @Synchronized
    fun recordFailure(nowMs: Long): RecoveryDecision {
        while (failures.isNotEmpty() && nowMs - failures.first() >= windowMs) failures.removeFirst()
        failures.addLast(nowMs)
        val count = failures.size
        return RecoveryDecision(
            recover = count <= maxFailuresInWindow,
            delayMs = delaysMs[(count - 1).coerceAtMost(delaysMs.lastIndex)],
            failuresInWindow = count,
        )
    }

    @Synchronized
    fun reset() = failures.clear()
}
