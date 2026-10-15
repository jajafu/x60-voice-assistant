package com.va.voiceassistant.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderRecurrenceCalculatorTest {
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun `daily recurrence chooses today when time is still ahead`() {
        val now = ZonedDateTime.of(2026, 9, 24, 8, 10, 0, 0, zone)
        val recurrence = ReminderRecurrence(ReminderFrequency.DAILY, hour = 9, minute = 0)

        assertEquals(
            now.withHour(9).withMinute(0).toInstant().toEpochMilli(),
            ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(
                recurrence,
                now.toInstant().toEpochMilli(),
                zone,
            ),
        )
    }

    @Test
    fun `daily recurrence rolls to tomorrow when time has passed`() {
        val now = ZonedDateTime.of(2026, 9, 24, 21, 0, 0, 0, zone)
        val recurrence = ReminderRecurrence(ReminderFrequency.DAILY, hour = 18, minute = 30)

        assertEquals(
            now.plusDays(1).withHour(18).withMinute(30).toInstant().toEpochMilli(),
            ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(
                recurrence,
                now.toInstant().toEpochMilli(),
                zone,
            ),
        )
    }

    @Test
    fun `weekly recurrence chooses nearest selected weekday`() {
        val thursday = ZonedDateTime.of(2026, 9, 24, 8, 10, 0, 0, zone)
        val recurrence = ReminderRecurrence(ReminderFrequency.WEEKLY, listOf(1, 3, 5), 18, 0)
        val expectedFriday = ZonedDateTime.of(2026, 9, 25, 18, 0, 0, 0, zone)

        assertEquals(
            expectedFriday.toInstant().toEpochMilli(),
            ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(
                recurrence,
                thursday.toInstant().toEpochMilli(),
                zone,
            ),
        )
    }

    @Test
    fun `weekly recurrence rolls past an elapsed selected day`() {
        val mondayEvening = ZonedDateTime.of(2026, 9, 28, 21, 0, 0, 0, zone)
        val recurrence = ReminderRecurrence(ReminderFrequency.WEEKLY, listOf(1, 3), 20, 0)
        val expectedWednesday = ZonedDateTime.of(2026, 9, 30, 20, 0, 0, 0, zone)

        assertEquals(
            expectedWednesday.toInstant().toEpochMilli(),
            ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(
                recurrence,
                mondayEvening.toInstant().toEpochMilli(),
                zone,
            ),
        )
    }

    @Test
    fun `recurrence uses local wall clock in requested timezone`() {
        val utc = ZoneId.of("UTC")
        val after = ZonedDateTime.of(2026, 9, 24, 0, 0, 0, 0, utc).toInstant().toEpochMilli()
        val recurrence = ReminderRecurrence(ReminderFrequency.DAILY, hour = 9, minute = 0)
        val expectedTaipei = ZonedDateTime.of(2026, 9, 24, 9, 0, 0, 0, zone)

        assertEquals(
            expectedTaipei.toInstant().toEpochMilli(),
            ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(recurrence, after, zone),
        )
    }

    @Test
    fun `planner keeps id and advances recurring reminder after firing`() {
        val firedAt = ZonedDateTime.of(2026, 9, 24, 9, 0, 0, 0, zone)
        val reminder = ScheduledReminder(
            id = "same-id",
            message = "吃藥",
            triggerAtEpochMillis = firedAt.toInstant().toEpochMilli(),
            createdAtEpochMillis = firedAt.minusDays(1).toInstant().toEpochMilli(),
            recurrence = ReminderRecurrence(ReminderFrequency.DAILY, hour = 9, minute = 0),
        )

        val next = ReminderOccurrencePlanner.afterFiring(reminder, firedAt.toInstant().toEpochMilli(), zone)

        assertEquals("same-id", next?.id)
        assertEquals(firedAt.plusDays(1).toInstant().toEpochMilli(), next?.triggerAtEpochMillis)
    }

    @Test
    fun `planner removes one-shot reminder after firing`() {
        val reminder = ScheduledReminder("one-shot", "倒垃圾", 2L, 1L)

        assertNull(ReminderOccurrencePlanner.afterFiring(reminder, 2L, zone))
    }
}
