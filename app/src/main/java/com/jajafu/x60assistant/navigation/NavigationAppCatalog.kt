package com.jajafu.x60assistant.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

/**
 * 地圖 App 開啟目的地的 URI 策略。
 *
 * 各家地圖對同一個目的地字串的落點不同，這是 2026-09-30 在 vivo X60（Android 13）實測的結果，
 * 不是文件推論：
 *
 * - [HTTPS_DIRECTIONS]：`https://www.google.com/maps/dir/?api=1&destination=<地點>` 直達路線預覽頁，
 *   畫面已帶好起點、終點、時間與距離，只剩「開始」需要使用者點一次。
 * - [GEO_QUERY]：`geo:0,0?q=<地點>` 只會停在搜尋結果頁，使用者還要自己選地點再點路線。
 *   兩家地圖共存時，`geo:` 未指定套件會開系統選擇器對話框，因此 [NavigationAppLauncher] 必須帶套件。
 *
 * `google.navigation:q=<lat>,<lon>` 實測時印出 `Activity not started, its current task has been
 * brought to the front`，無法確認目的地是否被帶入，因此不採用。
 */
enum class NavigationUriStrategy {
    /** 走 Google 官方 Directions URL，直達路線預覽頁。 */
    HTTPS_DIRECTIONS,

    /** 以地名字串讓地圖自行搜尋，會停在搜尋結果頁。 */
    GEO_QUERY,
}

data class NavigationAppOption(
    val appName: String,
    val packageName: String,
    val uriStrategy: NavigationUriStrategy,
) {
    /**
     * 組出帶指定套件的 Intent。
     *
     * 固定帶套件是必要的：未指定時 `geo:` 在高德與 Google 地圖並存的手機上會開系統選擇器，
     * 使用者說完話還要多按一次才能選 App。
     */
    fun buildIntent(destination: String): Intent {
        val uri = when (uriStrategy) {
            NavigationUriStrategy.HTTPS_DIRECTIONS -> GOOGLE_DIRECTIONS_BASE.toUri()
                .buildUpon()
                .appendQueryParameter("api", "1")
                .appendQueryParameter("destination", destination)
                .build()

            NavigationUriStrategy.GEO_QUERY -> "geo:0,0".toUri()
                .buildUpon()
                .appendQueryParameter("q", destination)
                .build()
        }
        return Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
    }

    companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val GOOGLE_DIRECTIONS_BASE = "https://www.google.com/maps/dir/"
    }
}

/** 只列出實際已安裝、且小助手已實測能開到目的地畫面的地圖 App。 */
class NavigationAppCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun availableApps(): List<NavigationAppOption> = supportedApps().filter { isInstalled(it.packageName) }

    /** 全部已知的支援項目，包含尚未安裝者，供設定頁顯示「目前未偵測到」。 */
    fun supportedApps(): List<NavigationAppOption> = listOf(
        NavigationAppOption(
            appName = "Google 地圖",
            packageName = NavigationAppOption.GOOGLE_MAPS_PACKAGE,
            uriStrategy = NavigationUriStrategy.HTTPS_DIRECTIONS,
        ),
    )

    fun find(pPackageName: String): NavigationAppOption? =
        availableApps().firstOrNull { it.packageName == pPackageName }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    }.getOrNull() != null
}
