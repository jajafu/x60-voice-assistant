package com.va.voiceassistant.reminder

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提醒頁「停止鈴聲」橫幅靠 [ReminderAlarmState.ringing] 觀察響鈴狀態；
 * 確認 boolean 寫入與 StateFlow 值恆一致，且測試後復原，避免影響其他測試。
 */
class ReminderAlarmStateTest {
    @Test
    fun `ringing flow mirrors isRinging writes`() {
        try {
            ReminderAlarmState.isRinging = false
            assertFalse(ReminderAlarmState.ringing.value)
            ReminderAlarmState.isRinging = true
            assertTrue(ReminderAlarmState.ringing.value)
            runBlocking { assertTrue(ReminderAlarmState.ringing.first()) }
        } finally {
            ReminderAlarmState.isRinging = false
        }
        assertFalse(ReminderAlarmState.ringing.value)
    }
}
