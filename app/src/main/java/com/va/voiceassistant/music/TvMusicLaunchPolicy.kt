package com.va.voiceassistant.music

/**
 * 電視情境下，哪些語音指令要先把 MusicCabin 畫面帶到前景再送出。
 *
 * 背景：電視比手機更積極清掉背景 App，MusicCabin 被清掉後收不到語音廣播，
 * 造成使用者回報的「沒先開 MusicCabin 就無法播放」。播放類指令先把 MusicCabin
 * 的電視畫面叫到前景，它一顯示就會自動啟動播放服務，之後送出的語音指令才有對象接收。
 *
 * 只有「開始播放」類指令需要（使用者決定：每次叫播放音樂都打開）；暫停、切歌、
 * 音量、收藏等是對已在運作的播放器下指令，不需要重新把畫面叫出來。
 */
object TvMusicLaunchPolicy {
    fun shouldBringToForeground(isTv: Boolean, command: MusicCommand): Boolean =
        isTv && isPlaybackStart(command)

    private fun isPlaybackStart(command: MusicCommand): Boolean = when (command) {
        MusicCommand.Play,
        MusicCommand.PlayFavorites,
        is MusicCommand.SearchAndPlay,
        is MusicCommand.PlayArtist -> true
        else -> false
    }
}
