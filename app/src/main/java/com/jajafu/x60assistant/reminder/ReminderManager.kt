package com.jajafu.x60assistant.reminder

import android.content.Context
import java.util.UUID

class ReminderManager(context: Context) {
    private val store = ReminderStore(context.applicationContext)
    private val scheduler = ReminderScheduler(context.applicationContext)

    fun pending(nowEpochMillis: Long = System.currentTimeMillis()): List<ScheduledReminder> =
        store.all().filter { it.triggerAtEpochMillis > nowEpochMillis }
            .sortedBy(ScheduledReminder::triggerAtEpochMillis)

    fun create(message: String, triggerAtEpochMillis: Long): ReminderScheduleMode {
        requireValid(message, triggerAtEpochMillis)
        val reminder = ScheduledReminder(
            id = UUID.randomUUID().toString(),
            message = message.trim(),
            triggerAtEpochMillis = triggerAtEpochMillis,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        store.add(reminder)
        return try {
            scheduler.schedule(reminder)
        } catch (error: Exception) {
            store.remove(reminder.id)
            throw error
        }
    }

    fun update(id: String, message: String, triggerAtEpochMillis: Long): ReminderScheduleMode {
        requireValid(message, triggerAtEpochMillis)
        val previous = store.find(id) ?: error("Reminder no longer exists")
        val updated = previous.copy(message = message.trim(), triggerAtEpochMillis = triggerAtEpochMillis)
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

    private fun requireValid(message: String, triggerAtEpochMillis: Long) {
        require(message.isNotBlank()) { "Reminder message is blank" }
        require(triggerAtEpochMillis > System.currentTimeMillis()) { "Reminder time is not in the future" }
    }
}
