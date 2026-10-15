package com.jajafu.x60assistant.orchestration

import com.jajafu.x60assistant.music.MusicCommand
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
}
