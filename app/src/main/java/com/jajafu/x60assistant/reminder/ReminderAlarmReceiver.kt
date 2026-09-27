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
        val reminder = store.consumeDue(id, System.currentTimeMillis()) ?: return
        val nextOccurrence = store.find(id)
        if (nextOccurrence != null) {
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
