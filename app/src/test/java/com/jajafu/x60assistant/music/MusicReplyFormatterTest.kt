package com.jajafu.x60assistant.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MusicReplyFormatterTest {
    @Test fun `current song reply uses only supplied player metadata`() {
        assertEquals("這首歌是周杰倫唱的晴天。", MusicReplyFormatter.currentTrack(" 晴天 ", " 周杰倫 "))
        assertEquals("這首歌叫晴天，但播放器沒有提供歌手。", MusicReplyFormatter.currentTrack("晴天", null))
        assertEquals("播放器沒有提供這首歌的歌名。", MusicReplyFormatter.currentTrack(null, "周杰倫"))
    }
    @Test
    fun `song request names the confirmed track naturally`() {
        val result = MusicReplyFormatter.confirmedPlayback(
            MusicCommand.SearchAndPlay("周杰倫 晴天"),
            "晴天",
            "周杰倫",
        )

        assertEquals("好的，正在播放周杰倫的《晴天》。", result.message)
        assertEquals("好的，準備播放周杰倫的《晴天》。", result.deferredMessage)
        assertFalse(result.message.contains("MusicCabin"))
    }

    @Test
    fun `skip replies match direction and defer playback during voice session`() {
        val next = MusicReplyFormatter.confirmedPlayback(MusicCommand.Next, "安靜", "周杰倫")
        val previous = MusicReplyFormatter.confirmedPlayback(MusicCommand.Previous, "晴天", "周杰倫")

        assertEquals("好的，下一首是周杰倫的《安靜》。", next.message)
        assertEquals("好的，下一首是周杰倫的《安靜》，稍後播放。", next.deferredMessage)
        assertEquals("好的，上一首是周杰倫的《晴天》。", previous.message)
        assertEquals("好的，上一首是周杰倫的《晴天》，稍後播放。", previous.deferredMessage)
    }

    @Test
    fun `play without track metadata does not invent a song name`() {
        val result = MusicReplyFormatter.confirmedPlayback(MusicCommand.Play, null, null)

        assertEquals("好的，音樂已開始播放。", result.message)
        assertEquals("好的，準備播放音樂。", result.deferredMessage)
    }

    @Test
    fun `favorite playback reply identifies the liked queue`() {
        val result = MusicReplyFormatter.confirmedPlayback(MusicCommand.PlayFavorites, "晴天", "周杰倫")
        assertEquals("好的，正在播放喜愛歌曲：周杰倫的《晴天》。", result.message)
        assertEquals("好的，準備播放喜愛歌曲：周杰倫的《晴天》。", result.deferredMessage)
    }

    @Test
    fun `artist request names the confirmed track naturally`() {
        val result = MusicReplyFormatter.confirmedPlayback(
            MusicCommand.PlayArtist("周杰倫"),
            "晴天",
            "周杰倫",
        )

        assertEquals("好的，正在播放周杰倫的《晴天》。", result.message)
        assertEquals("好的，準備播放周杰倫的《晴天》。", result.deferredMessage)
    }

    @Test
    fun `music app launch replies explain the safe foreground path`() {
        assertEquals(
            "已為你打開MusicCabin。",
            MusicAppLaunchReplyFormatter.explicitOpen(
                MusicAppLaunchMode.DIRECT,
                "MusicCabin",
            ).message,
        )
        assertEquals(
            "請點擊通知開啟MusicCabin，完成首次授權並先播放一首歌。",
            MusicAppLaunchReplyFormatter.playbackSetup(
                MusicAppLaunchMode.NOTIFICATION,
                "MusicCabin",
            ).message,
        )
    }
}
