package com.jajafu.x60assistant.reminder

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Serializable
enum class ReminderFrequency { DAILY, WEEKLY }

@Serializable
data class ReminderRecurrence(
    val frequency: ReminderFrequency,
    /** ISO-8601 weekday numbers: Monday=1 through Sunday=7. Empty for DAILY. */
    val daysOfWeek: List<Int> = emptyList(),
    val hour: Int,
    val minute: Int,
)

@Serializable
data class ScheduledReminder(
    val id: String,
    val message: String,
    val triggerAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    /** Missing in older JSON and therefore decoded as a one-shot reminder. */
    val recurrence: ReminderRecurrence? = null,
)

@Serializable
private data class ReminderState(val reminders: List<ScheduledReminder> = emptyList())

class ReminderStore(context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<ScheduledReminder> = synchronized(lock) { readState().reminders }

    fun find(id: String): ScheduledReminder? = synchronized(lock) {
        readState().reminders.firstOrNull { it.id == id }
    }

    /** Atomically claim a due occurrence so a delayed duplicate broadcast cannot fire the next one. */
    fun consumeDue(id: String, nowEpochMillis: Long): ScheduledReminder? = synchronized(lock) {
        val state = readState()
        val reminder = state.reminders.firstOrNull { it.id == id } ?: return@synchronized null
        if (reminder.triggerAtEpochMillis > nowEpochMillis) return@synchronized null
        val next = ReminderOccurrencePlanner.afterFiring(reminder, nowEpochMillis)
        val remaining = state.reminders.filterNot { it.id == id } + listOfNotNull(next)
        writeState(ReminderState(remaining.sortedBy(ScheduledReminder::triggerAtEpochMillis)))
        reminder
    }

    fun add(reminder: ScheduledReminder) = synchronized(lock) {
        val reminders = readState().reminders.filterNot { it.id == reminder.id } + reminder
        writeState(ReminderState(reminders.sortedBy(ScheduledReminder::triggerAtEpochMillis)))
    }

    fun remove(id: String) = synchronized(lock) {
        writeState(ReminderState(readState().reminders.filterNot { it.id == id }))
    }

    fun removeAll(ids: Set<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        writeState(ReminderState(readState().reminders.filterNot { it.id in ids }))
    }

    fun clear() = synchronized(lock) {
        writeState(ReminderState())
    }

    private fun readState(): ReminderState {
        val raw = preferences.getString(KEY_STATE, null) ?: return ReminderState()
        return runCatching { json.decodeFromString<ReminderState>(raw) }.getOrDefault(ReminderState())
    }

    @SuppressLint("ApplySharedPref")
    private fun writeState(state: ReminderState) {
        // Persist before AlarmManager is called so a process death cannot leave an unresolvable alarm.
        preferences.edit(commit = true) {
            putString(KEY_STATE, json.encodeToString(state))
        }
        _changes.tryEmit(Unit)
    }

    companion object {
        private const val PREFERENCES_NAME = "scheduled_reminders"
        private const val KEY_STATE = "state"
        private val lock = Any()
        private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val changes = _changes.asSharedFlow()
    }
}
