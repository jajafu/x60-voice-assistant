package com.jajafu.x60assistant.music

import android.media.session.PlaybackState

/**
 * 車機 fallback 用的最小播放 session 描述。
 *
 * 方易通 7862 這類車機的原生播放器通常不公開 `MediaBrowserService`，
 * 設定頁列不出來、`MusicCabinController` 也連不上。此时改用
 * `MediaSessionManager` 查「實際在播的那個」來暫停。
 * 只帶套件名與狀態，不持有 framework 物件，所以可以在 JVM 測試。
 */
data class ActiveSessionSnapshot(
    val packageName: String,
    val state: Int,
)

/** 從使用中的 sessions 選一個下暫停：PLAYING 优先，其次 BUFFERING；其餘不動。 */
object ActiveSessionPicker {
    fun playing(sessions: List<ActiveSessionSnapshot>): ActiveSessionSnapshot? =
        sessions.firstOrNull { it.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.state == PlaybackState.STATE_BUFFERING }
}
