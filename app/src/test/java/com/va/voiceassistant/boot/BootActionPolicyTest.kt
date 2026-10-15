package com.va.voiceassistant.boot

import org.junit.Assert.assertEquals
import org.junit.Test

class BootActionPolicyTest {
    @Test
    fun `boot actions can only request a reminder`() {
        assertEquals(BootAction.SHOW_REMINDER, BootActionPolicy.actionFor("android.intent.action.BOOT_COMPLETED"))
        assertEquals(BootAction.SHOW_REMINDER, BootActionPolicy.actionFor("android.intent.action.LOCKED_BOOT_COMPLETED"))
        assertEquals(BootAction.IGNORE, BootActionPolicy.actionFor("com.example.START_MICROPHONE"))
    }
}
