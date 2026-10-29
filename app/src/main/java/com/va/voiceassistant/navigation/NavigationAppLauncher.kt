package com.va.voiceassistant.navigation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.va.voiceassistant.music.AssistantUiVisibility

enum class NavigationLaunchMode {
    DIRECT,
    DIRECT_UNVERIFIED,
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
 * 說導航時一律直接嘗試啟動 Activity，不論小助手是否在前景：
 *
 * - 小助手在前景，或已取得「顯示在其他應用程式上層」（SYSTEM_ALERT_WINDOW）權限時，
 *   背景啟動限制明確允許，回 [NavigationLaunchMode.DIRECT]。
 * - 兩者皆無時仍先嘗試：部分車機系統不執行背景啟動限制，實際會成功；
 *   執行結果無法驗證，回 [NavigationLaunchMode.DIRECT_UNVERIFIED]，回話附上授權指引。
 * - 啟動失敗（拋出例外）時降級到地名字串搜尋，再失敗才交回待開啟的 Intent，
 *   由服務掛到常駐通知上的「開啟地圖」按鈕當最後備案：車機通知常被隱藏，不作主要路徑。
 */
class NavigationAppLauncher(
    context: Context,
    navPackageName: String,
    navAppName: String,
) {
    private val appContext = context.applicationContext

    fun openRoutePreview(app: NavigationAppOption, destination: String): NavigationLaunch {
        val primary = app.buildIntent(destination)
        if (launch(primary)) {
            val mode = if (canLaunchFromBackground()) {
                NavigationLaunchMode.DIRECT
            } else {
                NavigationLaunchMode.DIRECT_UNVERIFIED
            }
            return NavigationLaunch(mode, null)
        }

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

    /**
     * 小助手在前景，或已取得「顯示在其他應用程式上層」時，啟動結果可信；
     * 否則系統可能靜默攔截（不拋例外、畫面不出現），只能回未經確認的結果。
     */
    private fun canLaunchFromBackground(): Boolean =
        AssistantUiVisibility.isVisible || Settings.canDrawOverlays(appContext)

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
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
        NavigationLaunchMode.DIRECT_UNVERIFIED ->
            "已開啟${appName}到${destination}的路線；若畫面沒有跳出來，" +
                "請到設定允許小助手顯示在其他應用程式上層，之後就會直接開。"
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
