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
        playingIndex(sessions)?.let(sessions::get)

    /**
     * 被選中 session 的索引，而非只回套件名。
     *
     * 同一套件可能有多個 session（例如一個已暫停、一個在播）；只回套件名，
     * 呼叫端再用套件名取第一個 token 會拿到錯誤的那一個。回索引可讓呼叫端
     * 直接對同一位置的 framework session 取 token，保證暫停與恢復的是同一台。
     */
    fun playingIndex(sessions: List<ActiveSessionSnapshot>): Int? =
        sessions.indexOfFirst { it.state == PlaybackState.STATE_PLAYING }.takeIf { it >= 0 }
            ?: sessions.indexOfFirst { it.state == PlaybackState.STATE_BUFFERING }.takeIf { it >= 0 }

    /**
     * 是否有其他正在播放的 session。
     *
     * 呼叫端僅在已持有的 fallback 為 PAUSED 時使用，因此任何 PLAYING／BUFFERING
     * 必然屬於另一個 session／播放器；此時不得自動恢復舊的那一台，避免兩台疊播。
     */
    fun hasPlaying(sessions: List<ActiveSessionSnapshot>): Boolean =
        sessions.any { it.state == PlaybackState.STATE_PLAYING || it.state == PlaybackState.STATE_BUFFERING }
}
