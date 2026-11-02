package com.va.voiceassistant.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * 裝置形態判定：手機與電視共用同一個 App，介面方向與版面依此分流。
 *
 * 判準（任一成立即視為電視）：
 * 1. UiModeManager 回報電視模式（UiModeManager#currentModeType）。
 * 2. 系統宣告 leanback 特徵（android.software.leanback，Android TV 標準特徵）。
 * 兩個來源互補：部分電視盒子只回報其一。
 */
object DeviceUiMode {

    /** 純邏輯（可單元測試）：輸入系統查到的形態與特徵，決定是否為電視。 */
    fun isTv(uiModeType: Int, hasLeanbackFeature: Boolean): Boolean =
        uiModeType == Configuration.UI_MODE_TYPE_TELEVISION || hasLeanbackFeature

    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        val uiModeType = uiModeManager?.currentModeType ?: Configuration.UI_MODE_TYPE_UNDEFINED
        val hasLeanback = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }.getOrDefault(false)
        return isTv(uiModeType, hasLeanback)
    }
}
