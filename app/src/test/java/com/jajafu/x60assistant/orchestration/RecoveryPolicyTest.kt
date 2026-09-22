package com.jajafu.x60assistant.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPolicyTest {
    @Test
    fun `uses bounded backoff and stops after five failures in ten minutes`() {
        val policy = RecoveryPolicy()
        val decisions = (0..5).map { policy.recordFailure(it * 1_000L) }
        assertEquals(listOf(0L, 1_000L, 2_000L, 4_000L, 8_000L), decisions.take(5).map { it.delayMs })
        assertTrue(decisions.take(5).all { it.recover })
        assertFalse(decisions.last().recover)
    }

    @Test
    fun `old failures leave the rolling window`() {
        val policy = RecoveryPolicy()
        repeat(5) { policy.recordFailure(it.toLong()) }
        assertTrue(policy.recordFailure(600_001).recover)
        assertEquals(1, policy.recordFailure(1_200_002).failuresInWindow)
    }
}
