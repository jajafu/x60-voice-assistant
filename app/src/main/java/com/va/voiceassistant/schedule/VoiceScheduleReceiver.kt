package com.va.voiceassistant.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.va.voiceassistant.MainActivity
import com.va.voiceassistant.R
import com.va.voiceassistant.data.SettingsRepository
import com.va.voiceassistant.service.ServiceStateRepository
import com.va.voiceassistant.service.VoiceForegroundService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 每日定時啟停的到點與重排入口（manifest 註冊、exported=false）。
 *
 * - **到點啟動**：服務未運作才啟動。Android 12+ 可能拋
 *   `ForegroundServiceStartNotAllowedException`（背景啟動限制），Android 14+ 對
 *   背景啟動的麥克風服務限制更嚴——失敗即降級為高優先通知，使用者點「啟動」
 *   （屬使用者互動，一定合法）。使用者 2026-11-03 決議接受此降級。
 * - **到點停止**：服務運作中才 `stopService`（背景停止無系統限制），未運作略過。
 * - **開機／時間／時區／精確鬧鐘權限變更**：只重新排程，不啟動麥克風（維持「開機不錄音」）。
 * - 到點處理完都重排下一天；設定變更另由 MainActivity 呼叫 scheduler 重排。
 */
class VoiceScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        when (intent?.action) {
            VoiceScheduleScheduler.ACTION_RUN_START -> {
                runStart(appContext)
                reschedule(appContext)
            }
            VoiceScheduleScheduler.ACTION_RUN_STOP -> {
                runStop(appContext)
                reschedule(appContext)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
            -> reschedule(appContext)
        }
    }

    private fun runStart(context: Context) {
        if (ServiceStateRepository.running.value) return
        val serviceIntent = Intent(context, VoiceForegroundService::class.java)
        val started = runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }.isSuccess
        if (!started) showFallbackNotification(context)
    }

    private fun runStop(context: Context) {
        if (!ServiceStateRepository.running.value) return
        runCatching {
            context.stopService(Intent(context, VoiceForegroundService::class.java))
        }
    }

    private fun reschedule(context: Context) {
        val settings = runBlocking {
            withTimeoutOrNull(RESCHEDULE_TIMEOUT_MS) { SettingsRepository(context).current() }
        } ?: return
        VoiceScheduleScheduler(context).reschedule(settings)
    }

    /** 背景啟動被系統攔下時的最後手段：點一下通知即啟動（使用者互動豁免）。 */
    private fun showFallbackNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "定時啟動提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "排程時間到但系統限制背景啟動時，提醒使用者點一下啟動語音助理"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val startIntent = PendingIntent.getForegroundService(
            context,
            REQUEST_START,
            Intent(context, VoiceForegroundService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            FALLBACK_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("語音助理排程時間到了")
                .setContentText("系統限制了背景啟動，點「啟動」開始聆聽。")
                .setContentIntent(openIntent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_notification, "啟動", startIntent)
                .build(),
        )
    }

    companion object {
        // Inlined string keeps this receiver safe on API 26–30 where the API 31 constant is absent.
        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        private const val CHANNEL_ID = "voice_schedule_fallback"
        private const val FALLBACK_NOTIFICATION_ID = 2401
        private const val REQUEST_START = 242
        private const val REQUEST_OPEN = 243
        private const val RESCHEDULE_TIMEOUT_MS = 3_000L
    }
}
