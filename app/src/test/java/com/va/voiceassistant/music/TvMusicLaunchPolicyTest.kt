package com.va.voiceassistant.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMusicLaunchPolicyTest {

    @Test
    fun `電視上播放類指令要先把 MusicCabin 叫到前景`() {
        assertTrue(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.Play))
        assertTrue(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.PlayFavorites))
        assertTrue(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.SearchAndPlay("晴天")))
        assertTrue(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.PlayArtist("周杰倫")))
    }

    @Test
    fun `電視上非播放起始指令不需要叫前景`() {
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.Pause))
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.Next))
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.Previous))
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.VolumeUp))
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.SaveCurrent))
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = true, MusicCommand.NowPlaying))
    }

    @Test
    fun `手機上一律不需要叫前景`() {
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = false, MusicCommand.Play))
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = false, MusicCommand.PlayFavorites))
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = false, MusicCommand.SearchAndPlay("晴天")))
        assertFalse(TvMusicLaunchPolicy.shouldBringToForeground(isTv = false, MusicCommand.PlayArtist("周杰倫")))
    }
}
