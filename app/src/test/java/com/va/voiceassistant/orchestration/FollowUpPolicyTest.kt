package com.va.voiceassistant.orchestration

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

    @Test
    fun `five seconds keeps follow up open until the full interval ends`() {
        val policy = FollowUpPolicy(5)
        assertFalse(policy.expired(10_000, 14_999))
        assertTrue(policy.expired(10_000, 15_000))
    }
}
