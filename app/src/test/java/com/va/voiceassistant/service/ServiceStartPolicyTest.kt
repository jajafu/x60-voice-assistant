package com.va.voiceassistant.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStartPolicyTest {
    @Test
    fun `requires both permissions before service starts`() {
        assertTrue(ServiceStartPolicy.canStart(true, true))
        assertFalse(ServiceStartPolicy.canStart(true, false))
        assertFalse(ServiceStartPolicy.canStart(false, true))
    }

    @Test
    fun `notification runtime permission starts at api 33`() {
        assertFalse(ServiceStartPolicy.requiresNotificationPermission(32))
        assertTrue(ServiceStartPolicy.requiresNotificationPermission(33))
        assertTrue(ServiceStartPolicy.requiresNotificationPermission(35))
    }
}

