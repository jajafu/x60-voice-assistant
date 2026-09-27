package com.jajafu.x60assistant.reminder

import android.content.Context
import java.util.UUID

class ReminderManager(context: Context) {
    private val store = ReminderStore(context.applicationContext)
    private val scheduler = ReminderScheduler(context.applicationContext)

    fun pending(): List<ScheduledReminder> =
        store.all().sortedBy(ScheduledReminder::triggerAtEpochMillis)

    fun create(
        message: String,
        triggerAtEpochMillis: Long,
        recurrence: ReminderRecurrence? = null,
    ): ReminderScheduleMode {
        val effectiveTrigger = effectiveTrigger(triggerAtEpochMillis, recurrence)
        requireValid(message, effectiveTrigger, recurrence)
        val reminder = ScheduledReminder(
            id = UUID.randomUUID().toString(),
            message = message.trim(),
            triggerAtEpochMillis = effectiveTrigger,
            createdAtEpochMillis = System.currentTimeMillis(),
            recurrence = recurrence,
        )
        store.add(reminder)
        return try {
            scheduler.schedule(reminder)
        } catch (error: Exception) {
            store.remove(reminder.id)
            throw error
        }
    }

    fun update(
        id: String,
        message: String,
        triggerAtEpochMillis: Long,
        recurrence: ReminderRecurrence? = null,
    ): ReminderScheduleMode {
        val effectiveTrigger = effectiveTrigger(triggerAtEpochMillis, recurrence)
        requireValid(message, effectiveTrigger, recurrence)
        val previous = store.find(id) ?: error("Reminder no longer exists")
        val updated = previous.copy(
            message = message.trim(),
            triggerAtEpochMillis = effectiveTrigger,
            recurrence = recurrence,
        )
        store.add(updated)
        return try {
            scheduler.schedule(updated)
        } catch (error: Exception) {
            store.add(previous)
            runCatching { scheduler.schedule(previous, notifyWhenApproximate = false) }
            throw error
        }
    }

    fun delete(id: String) {
        val reminder = store.find(id) ?: return
        scheduler.cancel(reminder)
        store.remove(id)
    }

    private fun effectiveTrigger(triggerAtEpochMillis: Long, recurrence: ReminderRecurrence?): Long =
        recurrence?.let {
            ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(it, System.currentTimeMillis())
        } ?: triggerAtEpochMillis

    private fun requireValid(
        message: String,
        triggerAtEpochMillis: Long,
        recurrence: ReminderRecurrence?,
    ) {
        require(message.isNotBlank()) { "Reminder message is blank" }
        require(triggerAtEpochMillis > System.currentTimeMillis()) { "Reminder time is not in the future" }
        recurrence?.let {
            require(it.hour in 0..23 && it.minute in 0..59) { "Reminder recurrence time is invalid" }
            if (it.frequency == ReminderFrequency.WEEKLY) {
                require(it.daysOfWeek.isNotEmpty() && it.daysOfWeek.all { day -> day in 1..7 }) {
                    "Weekly reminder requires valid weekdays"
                }
            }
        }
    }
}
