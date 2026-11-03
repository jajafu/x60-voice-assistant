package com.va.voiceassistant.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.va.voiceassistant.data.AssistantSettings

/**
 * 每日定時啟停的鬧鐘排程，沿用 `ReminderScheduler` 的模式：
 * 可排精確就用 `setExactAndAllowWhileIdle`，權限未授權退回
 * `setAndAllowWhileIdle`（可能延遲，不改行為、不另外發通知——精確鬧鐘
 * 權限引導由設定頁的既有 onboarding 負責）。
 *
 * 啟動與停止是兩個獨立的每日鬧鐘；設定變更、開機、時間／時區變更後
 * 都會整組重排（`VoiceScheduleReceiver` 與 MainActivity 呼叫 [reschedule]）。
 */
class VoiceScheduleScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun reschedule(settings: AssistantSettings, nowEpochMillis: Long = System.currentTimeMillis()) {
        if (!VoiceSchedulePlanner.isValid(
                settings.scheduleEnabled,
                settings.scheduleStartMinutes,
                settings.scheduleStopMinutes,
            )
        ) {
            cancel()
            return
        }
        val startAt = VoiceSchedulePlanner.nextOccurrence(settings.scheduleStartMinutes, nowEpochMillis)
        val stopAt = VoiceSchedulePlanner.nextOccurrence(settings.scheduleStopMinutes, nowEpochMillis)
        setExact(startAt, pendingOperation(ACTION_RUN_START, REQUEST_START))
        setExact(stopAt, pendingOperation(ACTION_RUN_STOP, REQUEST_STOP))
    }

    fun cancel() {
        alarmManager.cancel(pendingOperation(ACTION_RUN_START, REQUEST_START))
        alarmManager.cancel(pendingOperation(ACTION_RUN_STOP, REQUEST_STOP))
    }

    private fun setExact(triggerAtEpochMillis: Long, operation: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
        }
    }

    private fun pendingOperation(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, VoiceScheduleReceiver::class.java).apply { this.action = action },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_RUN_START = "com.va.voiceassistant.action.RUN_SCHEDULED_START"
        const val ACTION_RUN_STOP = "com.va.voiceassistant.action.RUN_SCHEDULED_STOP"
        private const val REQUEST_START = 240
        private const val REQUEST_STOP = 241
    }
}
