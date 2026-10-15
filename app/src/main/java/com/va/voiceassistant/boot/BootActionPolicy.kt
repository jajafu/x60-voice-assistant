package com.va.voiceassistant.boot

enum class BootAction { SHOW_REMINDER, IGNORE }

object BootActionPolicy {
    fun actionFor(intentAction: String?): BootAction = when (intentAction) {
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.LOCKED_BOOT_COMPLETED",
        -> BootAction.SHOW_REMINDER
        else -> BootAction.IGNORE
    }
}
