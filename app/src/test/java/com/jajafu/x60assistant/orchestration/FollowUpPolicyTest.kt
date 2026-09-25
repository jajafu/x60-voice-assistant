package com.jajafu.x60assistant.orchestration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpPolicyTest {
    @Test
    fun `zero seconds expires immediately`() {
        val policy = FollowUpPolicy(0)
        assertTrue(policy.expired(1_000, 1_000))
    }

    @Test
    fun `uses monotonic elapsed time and clamps to thirty seconds`() {
        val policy = FollowUpPolicy(99)
        assertFalse(policy.expired(1_000, 30_999))
        assertTrue(policy.expired(1_000, 31_000))
    }
}
