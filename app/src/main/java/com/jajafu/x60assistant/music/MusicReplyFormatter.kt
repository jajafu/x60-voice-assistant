package com.jajafu.x60assistant.music

/** The player is paused during a voice session, so the spoken reply may describe what will play next. */
object MusicReplyFormatter {
    fun confirmedPlayback(command: MusicCommand, title: String?, artist: String?): MusicActionResult {
        val track = title?.trim()?.takeIf(String::isNotEmpty)?.let { cleanTitle ->
            val cleanArtist = artist?.trim()?.takeIf(String::isNotEmpty)
            if (cleanArtist == null) "《$cleanTitle》" else "${cleanArtist}的《$cleanTitle》"
        }
        val immediate = when (command) {
            MusicCommand.Play, is MusicCommand.SearchAndPlay ->
                if (track == null) "好的，音樂已開始播放。" else "好的，正在播放$track。"
            MusicCommand.PlayFavorites ->
                if (track == null) "好的，正在播放喜愛歌曲。" else "好的，正在播放喜愛歌曲：$track。"
            MusicCommand.Next ->
                if (track == null) "好的，已切到下一首。" else "好的，下一首是$track。"
            MusicCommand.Previous ->
                if (track == null) "好的，已切到上一首。" else "好的，上一首是$track。"
            MusicCommand.Pause, MusicCommand.Open, MusicCommand.SaveCurrent, MusicCommand.RemoveCurrent -> error("No confirmed playback for $command")
        }
        val deferred = when (command) {
            MusicCommand.Play, is MusicCommand.SearchAndPlay ->
                if (track == null) "好的，準備播放音樂。" else "好的，準備播放$track。"
            MusicCommand.PlayFavorites ->
                if (track == null) "好的，準備播放喜愛歌曲。" else "好的，準備播放喜愛歌曲：$track。"
            MusicCommand.Next ->
                if (track == null) "好的，已切到下一首，稍後播放。" else "好的，下一首是$track，稍後播放。"
            MusicCommand.Previous ->
                if (track == null) "好的，已切到上一首，稍後播放。" else "好的，上一首是$track，稍後播放。"
            MusicCommand.Pause, MusicCommand.Open, MusicCommand.SaveCurrent, MusicCommand.RemoveCurrent -> error("No deferred playback for $command")
        }
        return MusicActionResult(immediate, deferred, confirmedPlayback = true)
    }
}
