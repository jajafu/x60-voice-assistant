package com.jajafu.x60assistant.reminder

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduledReminderSerializationTest {
    @Test
    fun `older reminder json without recurrence remains a one-shot reminder`() {
        val reminder = Json.decodeFromString<ScheduledReminder>(
            """{"id":"legacy","message":"吃藥","triggerAtEpochMillis":2000,"createdAtEpochMillis":1000}""",
        )

        assertEquals("legacy", reminder.id)
        assertEquals("吃藥", reminder.message)
        assertNull(reminder.recurrence)
    }
}
