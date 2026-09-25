package com.jajafu.x60assistant.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmOnboardingPolicyTest {
    @Test
    fun `shows once on Android 12 or newer when permission is missing`() {
        assertTrue(ExactAlarmOnboardingPolicy.shouldShow(31, permissionGranted = false, promptAlreadyShown = false))
        assertFalse(ExactAlarmOnboardingPolicy.shouldShow(31, permissionGranted = false, promptAlreadyShown = true))
        assertFalse(ExactAlarmOnboardingPolicy.shouldShow(31, permissionGranted = true, promptAlreadyShown = false))
    }

    @Test
    fun `does not show on systems without exact alarm special access`() {
        assertFalse(ExactAlarmOnboardingPolicy.shouldShow(30, permissionGranted = false, promptAlreadyShown = false))
    }
}
