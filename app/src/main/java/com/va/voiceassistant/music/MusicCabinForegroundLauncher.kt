package com.va.voiceassistant.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay

/**
 * 電視情境下，把 MusicCabin 的電視畫面（leanback）帶到前景。
 *
 * 為什麼需要：電視比手機更積極清掉背景 App，MusicCabin 被清掉後收不到語音廣播，
 * 使用者因而回報「沒先手動開 MusicCabin 就無法播放」。MusicCabin 的電視畫面
 * （[TV_ACTIVITY]）一顯示，就會自動啟動它的播放服務，之後送出的語音指令才有對象。
 *
 * 背景啟動限制：Android 10+ 原則上不准背景 App 啟動別的畫面。小助手在前景，
 * 或已取得「顯示在其他應用程式上層」（SYSTEM_ALERT_WINDOW）時才確定可開；
 * 兩者皆無時仍先嘗試（部分電視不執行此限制），開不起來也不阻擋後續播放指令。
 */
class MusicCabinForegroundLauncher(
    context: Context,
    private val musicPackageName: String,
) {
    private val appContext = context.applicationContext

    /** 把 MusicCabin 電視畫面叫到前景；回傳是否成功送出啟動。 */
    suspend fun bringToForeground(): Boolean {
        val launched = launchTvActivity() || launchByLeanback() || launchByPackage()
        if (launched) delay(FOREGROUND_SETTLE_MS)
        return launched
    }

    /** 明確指定 MusicCabin 的電視 Activity，最精準。 */
    private fun launchTvActivity(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(musicPackageName, TV_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        return start(intent)
    }

    /** 退路一：電視桌面入口（leanback launcher）。 */
    private fun launchByLeanback(): Boolean {
        val intent = appContext.packageManager.getLeanbackLaunchIntentForPackage(musicPackageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(intent)
    }

    /** 退路二：一般啟動入口。 */
    private fun launchByPackage(): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(musicPackageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(intent)
    }

    private fun start(intent: Intent): Boolean =
        runCatching { appContext.startActivity(intent) }.isSuccess

    companion object {
        /**
         * MusicCabin 的電視 Activity 類名，與預設套件 `com.jajafu.musiccabin` 綁定。
         * 使用者改選其他音樂 App 時，這條明確指向的路徑會對不上並自然失效，
         * 落到 [launchByLeanback] → [launchByPackage] 兩層退路，不會讓播放失敗。
         */
        const val TV_ACTIVITY = "com.metrolist.music.tv.TvActivity"
        private const val FOREGROUND_SETTLE_MS = 1_200L
    }
}
