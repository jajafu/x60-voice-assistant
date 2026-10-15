package com.va.voiceassistant.reminder

object ExactAlarmOnboardingPolicy {
    private const val ANDROID_12_API = 31

    fun shouldShow(apiLevel: Int, permissionGranted: Boolean, promptAlreadyShown: Boolean): Boolean =
        apiLevel >= ANDROID_12_API && !permissionGranted && !promptAlreadyShown
}
