package com.jajafu.x60assistant.navigation

import android.content.Context
import android.content.Intent
import com.jajafu.x60assistant.music.AssistantUiVisibility

enum class NavigationLaunchMode {
    DIRECT,
    FALLBACK_SEARCH,
    NOTIFICATION,
    NOTIFICATIONS_DISABLED,
    NOT_FOUND,
    FAILED,
}

/** 直接開啟的結果；背景失敗時帶回待開啟的 Intent，由服務掛到常駐通知。 */
data class NavigationLaunch(
    val mode: NavigationLaunchMode,
    val launchIntent: Intent?,
)

/**
 * 開啟地圖 App 的路線預覽頁。
 *
 * 只在使用者看得到小助手 App 時直接啟動 Activity；背景與鎖屏不直接啟動，
 * 因為 target SDK 35 的背景啟動限制不允許前景服務直接拉起其他 App 的畫面。
 * 背景時把待開啟的 Intent 交給呼叫端，由服務掛到常駐通知上的「開啟地圖」按鈕：
 * 點按鈕屬於使用者操作，系統允許開地圖；不再另發一則通知，車機上不會被埋掉。
 */
class NavigationAppLauncher(
    context: Context,
    navPackageName: String,
    navAppName: String,
) {
    private val appContext = context.applicationContext

    fun openRoutePreview(app: NavigationAppOption, destination: String): NavigationLaunch {
        val primary = app.buildIntent(destination)
        if (launch(primary)) return NavigationLaunch(NavigationLaunchMode.DIRECT, null)

        // 專屬 URI 策略失效時降級到地名字串搜尋，至少把地點帶到地圖畫面。
        if (app.uriStrategy == NavigationUriStrategy.HTTPS_DIRECTIONS) {
            val fallback = NavigationAppOption(
                appName = app.appName,
                packageName = app.packageName,
                uriStrategy = NavigationUriStrategy.GEO_QUERY,
            )
            if (launch(fallback.buildIntent(destination))) {
                return NavigationLaunch(NavigationLaunchMode.FALLBACK_SEARCH, null)
            }
        }

        val notificationsOn = appContext
            .getSystemService(android.app.NotificationManager::class.java)
            ?.areNotificationsEnabled() == true
        return if (notificationsOn) {
            NavigationLaunch(NavigationLaunchMode.NOTIFICATION, primary)
        } else {
            NavigationLaunch(NavigationLaunchMode.NOTIFICATIONS_DISABLED, primary)
        }
    }

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (!AssistantUiVisibility.isVisible) return false
        return runCatching { appContext.startActivity(intent) }.isSuccess
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
            "已備好${appName}到${destination}的路線，請點服務通知上的「開啟地圖」直接開啟。"
        NavigationLaunchMode.NOTIFICATIONS_DISABLED ->
            "小助手的通知權限未開啟，請先到系統設定開啟通知，再讓我開啟路線。"
        NavigationLaunchMode.NOT_FOUND ->
            "找不到${appName}，請到設定重新選擇導航 App。"
        NavigationLaunchMode.FAILED ->
            "目前無法開啟${appName}，請先從桌面開啟地圖 App。"
    }
}
