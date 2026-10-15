package com.va.voiceassistant.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.va.voiceassistant.R

enum class ReminderScheduleMode { EXACT, APPROXIMATE }

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactly(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(reminder: ScheduledReminder, notifyWhenApproximate: Boolean = true): ReminderScheduleMode {
        val operation = alarmPendingIntent(reminder)
        return if (canScheduleExactly()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtEpochMillis,
                operation,
            )
            ReminderScheduleMode.EXACT
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtEpochMillis,
                operation,
            )
            if (notifyWhenApproximate) showExactAlarmPermissionNotification()
            ReminderScheduleMode.APPROXIMATE
        }
    }

    fun rescheduleAll(nowEpochMillis: Long = System.currentTimeMillis()) {
        val store = ReminderStore(context)
        store.all().forEach { reminder ->
            val candidate = ReminderOccurrencePlanner.beforeRescheduling(reminder, nowEpochMillis)
            if (candidate != reminder) store.add(candidate)
            // AlarmManager delivers past-due alarms as soon as allowed; the receiver consumes them.
            runCatching { schedule(candidate, notifyWhenApproximate = false) }
        }
    }

    fun cancelAll() {
        val store = ReminderStore(context)
        store.all().forEach { alarmManager.cancel(alarmPendingIntent(it)) }
        store.clear()
        context.getSystemService(NotificationManager::class.java).cancel(PERMISSION_NOTIFICATION_ID)
    }

    fun cancel(reminder: ScheduledReminder) {
        alarmManager.cancel(alarmPendingIntent(reminder))
    }

    fun showExactAlarmPermissionNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                PERMISSION_CHANNEL_ID,
                "提醒排程權限",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_PERMISSION,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            PERMISSION_NOTIFICATION_ID,
            NotificationCompat.Builder(context, PERMISSION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("允許小助手準時提醒")
                .setContentText("目前提醒可能延遲；點此開啟「鬧鐘與提醒」權限。")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun alarmPendingIntent(reminder: ScheduledReminder): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE
            data = "voiceassistant-reminder://alarm/${reminder.id}".toUri()
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val PERMISSION_CHANNEL_ID = "reminder_permission"
        private const val PERMISSION_NOTIFICATION_ID = 2102
        private const val REQUEST_PERMISSION = 210
        private const val REQUEST_ALARM = 211
    }
}
