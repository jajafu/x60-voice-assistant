package com.va.voiceassistant.reminder

import java.time.Instant
import java.time.ZoneId

object ReminderRecurrenceCalculator {
    fun nextTriggerAtEpochMillis(
        recurrence: ReminderRecurrence,
        afterEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        require(recurrence.hour in 0..23) { "Recurrence hour must be between 0 and 23" }
        require(recurrence.minute in 0..59) { "Recurrence minute must be between 0 and 59" }
        val weekdays = recurrence.daysOfWeek.distinct().sorted()
        if (recurrence.frequency == ReminderFrequency.WEEKLY) {
            require(weekdays.isNotEmpty()) { "Weekly recurrence requires at least one weekday" }
            require(weekdays.all { it in 1..7 }) { "Weekdays must use ISO values 1 through 7" }
        }

        val after = Instant.ofEpochMilli(afterEpochMillis).atZone(zoneId)
        val maxDays = if (recurrence.frequency == ReminderFrequency.DAILY) 1 else 7
        for (offset in 0..maxDays) {
            val date = after.toLocalDate().plusDays(offset.toLong())
            if (recurrence.frequency == ReminderFrequency.WEEKLY && date.dayOfWeek.value !in weekdays) continue
            val candidate = date.atTime(recurrence.hour, recurrence.minute).atZone(zoneId)
            if (candidate.toInstant().toEpochMilli() > afterEpochMillis) {
                return candidate.toInstant().toEpochMilli()
            }
        }
        error("Unable to calculate the next reminder occurrence")
    }
}

object ReminderOccurrencePlanner {
    /** An overdue stored occurrence has not fired yet. Catch it up once before advancing the series. */
    fun beforeRescheduling(
        reminder: ScheduledReminder,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ScheduledReminder {
        val recurrence = reminder.recurrence ?: return reminder
        if (reminder.triggerAtEpochMillis <= nowEpochMillis) return reminder
        return reminder.copy(
            triggerAtEpochMillis = ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(
                recurrence, nowEpochMillis, zoneId,
            ),
        )
    }

    fun afterFiring(
        reminder: ScheduledReminder,
        firedAtEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ScheduledReminder? {
        val recurrence = reminder.recurrence ?: return null
        val after = maxOf(reminder.triggerAtEpochMillis, firedAtEpochMillis)
        return reminder.copy(
            triggerAtEpochMillis = ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(
                recurrence = recurrence,
                afterEpochMillis = after,
                zoneId = zoneId,
            ),
        )
    }
}

fun ReminderRecurrence.label(): String = when (frequency) {
    ReminderFrequency.DAILY -> "每天"
    ReminderFrequency.WEEKLY -> daysOfWeek.distinct().sorted().joinToString("、", prefix = "每週") { day ->
        when (day) {
            1 -> "一"
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            7 -> "日"
            else -> "?"
        }
    }
}
