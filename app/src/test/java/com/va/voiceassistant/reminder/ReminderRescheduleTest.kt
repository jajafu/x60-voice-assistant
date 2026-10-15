package com.va.voiceassistant.reminder

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderRescheduleTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val due = Instant.parse("2026-09-28T01:00:00Z").toEpochMilli()
    private val now = due + 10 * 60_000
    private fun reminder(recurrence: ReminderRecurrence? = null) =
        ScheduledReminder("id", "提醒內容", due, due - 60_000, recurrence)

    @Test fun `opening app after due time preserves one shot until receiver fires`() {
        val original = reminder()
        val pending = ReminderOccurrencePlanner.beforeRescheduling(original, now, zone)
        assertEquals(original, pending)
        assertEquals(pending, ReminderOccurrencePlanner.beforeRescheduling(pending, now + 60_000, zone))
        assertNull(ReminderOccurrencePlanner.afterFiring(pending, now, zone))
    }

    @Test fun `delayed daily occurrence catches up once before advancing`() {
        val original = reminder(ReminderRecurrence(ReminderFrequency.DAILY, hour = 9, minute = 0))
        val pending = ReminderOccurrencePlanner.beforeRescheduling(original, now, zone)
        assertEquals(original, pending)
        val next = requireNotNull(ReminderOccurrencePlanner.afterFiring(pending, now, zone))
        assertEquals(due + 24 * 60 * 60_000, next.triggerAtEpochMillis)
        assertEquals(next, ReminderOccurrencePlanner.beforeRescheduling(next, now, zone))
    }

    @Test fun `multiple missed weekly occurrences produce only one catch up`() {
        val original = reminder(ReminderRecurrence(ReminderFrequency.WEEKLY, listOf(1), 9, 0))
        val later = now + 21 * 24 * 60 * 60_000L
        assertEquals(original, ReminderOccurrencePlanner.beforeRescheduling(original, later, zone))
        val next = requireNotNull(ReminderOccurrencePlanner.afterFiring(original, later, zone))
        assertEquals(due + 28 * 24 * 60 * 60_000L, next.triggerAtEpochMillis)
    }

    @Test fun `future recurring schedule still follows changed timezone`() {
        val original = reminder(ReminderRecurrence(ReminderFrequency.DAILY, hour = 9, minute = 0))
        val before = due - 60_000
        val next = ReminderOccurrencePlanner.beforeRescheduling(original, before, ZoneId.of("UTC"))
        assertEquals(Instant.parse("2026-09-28T09:00:00Z").toEpochMilli(), next.triggerAtEpochMillis)
    }
}
