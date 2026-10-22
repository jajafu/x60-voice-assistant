package com.va.voiceassistant.orchestration

import com.va.voiceassistant.music.MusicCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicHoldPolicyTest {
    @Test
    fun `newly started playback is held through spoken reply and follow up`() {
        assertTrue(MusicHoldPolicy.shouldPauseAfterAction(
            MusicCommand.Play,
            musicAlreadyHeld = false,
        ))
        assertTrue(MusicHoldPolicy.shouldPauseAfterAction(
            MusicCommand.SearchAndPlay("晴天"),
            musicAlreadyHeld = false,
        ))
        assertTrue(MusicHoldPolicy.shouldPauseAfterAction(
            MusicCommand.PlayArtist("周杰倫"),
            musicAlreadyHeld = false,
        ))
    }

    @Test
    fun `existing hold survives non playback music actions`() {
        assertTrue(MusicHoldPolicy.shouldPauseAfterAction(
            MusicCommand.SaveCurrent,
            musicAlreadyHeld = true,
        ))
    }

    @Test
    fun `explicit pause and open do not schedule music to resume`() {
        assertFalse(MusicHoldPolicy.shouldPauseAfterAction(MusicCommand.Pause, true))
        assertFalse(MusicHoldPolicy.shouldPauseAfterAction(MusicCommand.Open, true))
    }

    @Test
    fun `volume commands never start a new music hold`() {
        assertFalse(MusicHoldPolicy.shouldPauseAfterAction(MusicCommand.VolumeUp, musicAlreadyHeld = false))
        assertFalse(MusicHoldPolicy.shouldPauseAfterAction(MusicCommand.VolumeDown, musicAlreadyHeld = false))
        assertFalse(MusicHoldPolicy.shouldPauseAfterAction(MusicCommand.VolumeSet(50), musicAlreadyHeld = false))
        assertFalse(MusicHoldPolicy.shouldPauseAfterAction(MusicCommand.VolumeGet, musicAlreadyHeld = false))
        // 這一輪已經為對話按住時維持按住，音量指令不會把它放開。
        assertTrue(MusicHoldPolicy.shouldPauseAfterAction(MusicCommand.VolumeDown, musicAlreadyHeld = true))
    }
}
