package com.va.voiceassistant.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.va.voiceassistant.R

enum class MusicAppLaunchMode {
    DIRECT,
    NOTIFICATION,
    NOTIFICATIONS_DISABLED,
    NOT_FOUND,
    FAILED,
}

/** Tracks whether launching another app is a user-visible foreground transition. */
object AssistantUiVisibility {
    @Volatile
    var isVisible: Boolean = false
        private set

    fun onStart() {
        isVisible = true
    }

    fun onStop() {
        isVisible = false
    }
}

/**
 * Opens the selected player directly only while the assistant Activity is visible.
 * Background and lock-screen requests use a notification PendingIntent so Android can
 * attribute the launch to an explicit user tap.
 */
class MusicAppLauncher(
    context: Context,
    private val musicPackageName: String,
    private val musicAppName: String,
) {
    private val appContext = context.applicationContext

    fun openOrNotify(): MusicAppLaunchMode {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(musicPackageName)
            ?: return MusicAppLaunchMode.NOT_FOUND
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        if (AssistantUiVisibility.isVisible) {
            val opened = runCatching { appContext.startActivity(launchIntent) }.isSuccess
            if (opened) return MusicAppLaunchMode.DIRECT
        }
        return postLaunchNotification(launchIntent)
    }

    private fun postLaunchNotification(launchIntent: Intent): MusicAppLaunchMode {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return MusicAppLaunchMode.NOTIFICATIONS_DISABLED
        return runCatching {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "開啟音樂 App", NotificationManager.IMPORTANCE_HIGH),
            )
            val pending = PendingIntent.getActivity(
                appContext,
                musicPackageName.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("開啟$musicAppName")
                    .setContentText("點一下完成首次設定，之後小助手就能播放音樂")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build(),
            )
            MusicAppLaunchMode.NOTIFICATION
        }.getOrDefault(MusicAppLaunchMode.FAILED)
    }

    companion object {
        private const val CHANNEL_ID = "music_app_open"
        private const val NOTIFICATION_ID = 2301
    }
}

object MusicAppLaunchReplyFormatter {
    fun explicitOpen(mode: MusicAppLaunchMode, appName: String): MusicActionResult =
        MusicActionResult(message(mode, appName, retryPlayback = false))

    fun playbackSetup(mode: MusicAppLaunchMode, appName: String): MusicActionResult =
        MusicActionResult(message(mode, appName, retryPlayback = true))

    private fun message(mode: MusicAppLaunchMode, appName: String, retryPlayback: Boolean): String = when (mode) {
        MusicAppLaunchMode.DIRECT -> if (retryPlayback) {
            "我已打開$appName。請依畫面完成首次授權並先播放一首歌，完成後再叫我播放音樂。"
        } else {
            "已為你打開$appName。"
        }
        MusicAppLaunchMode.NOTIFICATION ->
            "請點擊通知開啟$appName，完成首次授權並先播放一首歌。"
        MusicAppLaunchMode.NOTIFICATIONS_DISABLED ->
            "小助手的通知權限未開啟，請先到系統設定開啟通知，再讓我打開$appName。"
        MusicAppLaunchMode.NOT_FOUND ->
            "找不到$appName，請到設定重新選擇音樂 App。"
        MusicAppLaunchMode.FAILED ->
            "目前無法開啟$appName，請先從桌面開啟並完成首次設定。"
    }
}
