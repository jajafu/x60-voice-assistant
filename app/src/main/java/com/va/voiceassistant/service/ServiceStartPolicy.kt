package com.va.voiceassistant.service

object ServiceStartPolicy {
    fun canStart(
        microphonePermissionGranted: Boolean,
        notificationPermissionGranted: Boolean,
    ): Boolean = microphonePermissionGranted && notificationPermissionGranted

    fun requiresNotificationPermission(apiLevel: Int): Boolean = apiLevel >= 33
}

