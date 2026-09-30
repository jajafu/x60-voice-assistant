package com.jajafu.x60assistant.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jajafu.x60assistant.R
import com.jajafu.x60assistant.music.AssistantUiVisibility

enum class NavigationLaunchMode {
    DIRECT,
    FALLBACK_SEARCH,
    NOTIFICATION,
    NOTIFICATIONS_DISABLED,
    NOT_FOUND,
    FAILED,
}

/**
 * 開啟地圖 App 的路線預覽頁。
 *
 * 只在使用者看得到小助手 App 時直接啟動 Activity；背景與鎖屏改用通知 PendingIntent，
 * 因為 target SDK 35 的背景啟動限制不允許前景服務直接拉起其他 App 的畫面。
 * 這與 [com.jajafu.x60assistant.music.MusicAppLauncher] 的處理一致。
 */
class NavigationAppLauncher(
    context: Context,
    private val navPackageName: String,
    private val navAppName: String,
) {
    private val appContext = context.applicationContext

    fun openRoutePreview(app: NavigationAppOption, destination: String): NavigationLaunchMode {
        val primary = app.buildIntent(destination)
        val direct = launch(primary)
        if (direct) return NavigationLaunchMode.DIRECT

        // 專屬 URI 策略失效時降級到地名字串搜尋，至少把地點帶到地圖畫面。
        if (app.uriStrategy == NavigationUriStrategy.HTTPS_DIRECTIONS) {
            val fallback = NavigationAppOption(
                appName = app.appName,
                packageName = app.packageName,
                uriStrategy = NavigationUriStrategy.GEO_QUERY,
            )
            if (launch(fallback.buildIntent(destination))) return NavigationLaunchMode.FALLBACK_SEARCH
        }

        return postLaunchNotification(primary)
    }

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (!AssistantUiVisibility.isVisible) return false
        return runCatching { appContext.startActivity(intent) }.isSuccess
    }

    private fun postLaunchNotification(launchIntent: Intent): NavigationLaunchMode {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return NavigationLaunchMode.NOTIFICATIONS_DISABLED
        return runCatching {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "開啟地圖路線", NotificationManager.IMPORTANCE_HIGH),
            )
            val pending = PendingIntent.getActivity(
                appContext,
                navPackageName.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("開啟${navAppName}")
                    .setContentText("點一下開啟路線預覽，確認後再開始導航")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build(),
            )
            NavigationLaunchMode.NOTIFICATION
        }.getOrDefault(NavigationLaunchMode.FAILED)
    }

    companion object {
        private const val CHANNEL_ID = "nav_app_open"
        private const val NOTIFICATION_ID = 2302
    }
}

object NavigationReplyFormatter {
    /**
     * 只說已開啟路線預覽並請使用者確認，不宣稱已在導航。
     *
     * Google 地圖實測會自行把「台北101」解析成「台北101購物中心」，未必是使用者要的點，
     * 因此回話不宣稱目的地已正確選定。
     */
    fun routePreview(mode: NavigationLaunchMode, appName: String, destination: String): String = when (mode) {
        NavigationLaunchMode.DIRECT ->
            "已開啟${appName}到${destination}的路線，請確認地點後點開始導航。"
        NavigationLaunchMode.FALLBACK_SEARCH ->
            "已在${appName}搜尋${destination}，請從搜尋結果選好地點再開始導航。"
        NavigationLaunchMode.NOTIFICATION ->
            "請點通知開啟${appName}到${destination}的路線，確認後再開始導航。"
        NavigationLaunchMode.NOTIFICATIONS_DISABLED ->
            "小助手的通知權限未開啟，請先到系統設定開啟通知，再讓我開啟路線。"
        NavigationLaunchMode.NOT_FOUND ->
            "找不到${appName}，請到設定重新選擇導航 App。"
        NavigationLaunchMode.FAILED ->
            "目前無法開啟${appName}，請先從桌面開啟地圖 App。"
    }
}
