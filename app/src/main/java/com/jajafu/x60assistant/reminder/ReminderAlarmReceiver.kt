package com.jajafu.x60assistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val store = ReminderStore(context)
        val reminder = store.find(id) ?: return
        val nextOccurrence = ReminderOccurrencePlanner.afterFiring(
            reminder = reminder,
            firedAtEpochMillis = System.currentTimeMillis(),
        )
        if (nextOccurrence == null) {
            store.remove(id)
        } else {
            // Persist the next occurrence before starting playback so a process death cannot lose the series.
            store.add(nextOccurrence)
            runCatching { ReminderScheduler(context).schedule(nextOccurrence, notifyWhenApproximate = false) }
        }
        val serviceIntent = Intent(context, ReminderAlarmService::class.java).apply {
            action = ReminderAlarmService.ACTION_START
            putExtra(ReminderAlarmService.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderAlarmService.EXTRA_MESSAGE, reminder.message)
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
            .onFailure { ReminderAlarmService.showFallbackNotification(context, reminder.message) }
    }

    companion object {
        const val ACTION_FIRE = "com.jajafu.x60assistant.action.FIRE_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
