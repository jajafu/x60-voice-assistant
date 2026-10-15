package com.va.voiceassistant.music

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveSessionPickerTest {
    @Test
    fun `playing session wins over paused and buffering ones`() {
        val sessions = listOf(
            ActiveSessionSnapshot("paused.app", PlaybackState.STATE_PAUSED),
            ActiveSessionSnapshot("buffering.app", PlaybackState.STATE_BUFFERING),
            ActiveSessionSnapshot("playing.app", PlaybackState.STATE_PLAYING),
        )

        assertEquals("playing.app", ActiveSessionPicker.playing(sessions)?.packageName)
    }

    @Test
    fun `buffering is used only when nothing is playing`() {
        val sessions = listOf(
            ActiveSessionSnapshot("paused.app", PlaybackState.STATE_PAUSED),
            ActiveSessionSnapshot("buffering.app", PlaybackState.STATE_BUFFERING),
        )

        assertEquals("buffering.app", ActiveSessionPicker.playing(sessions)?.packageName)
    }

    @Test
    fun `paused stopped or unknown sessions are left alone`() {
        val sessions = listOf(
            ActiveSessionSnapshot("paused.app", PlaybackState.STATE_PAUSED),
            ActiveSessionSnapshot("stopped.app", PlaybackState.STATE_STOPPED),
            ActiveSessionSnapshot("none.app", PlaybackState.STATE_NONE),
        )

        assertNull(ActiveSessionPicker.playing(sessions))
        assertNull(ActiveSessionPicker.playing(emptyList()))
    }

    @Test
    fun `hasPlaying is true only for playing or buffering`() {
        assertEquals(
            true,
            ActiveSessionPicker.hasPlaying(
                listOf(
                    ActiveSessionSnapshot("held.app", PlaybackState.STATE_PAUSED),
                    ActiveSessionSnapshot("other.app", PlaybackState.STATE_PLAYING),
                ),
            ),
        )
        assertEquals(
            true,
            ActiveSessionPicker.hasPlaying(
                listOf(ActiveSessionSnapshot("other.app", PlaybackState.STATE_BUFFERING)),
            ),
        )
        assertEquals(
            false,
            ActiveSessionPicker.hasPlaying(
                listOf(ActiveSessionSnapshot("held.app", PlaybackState.STATE_PAUSED)),
            ),
        )
        assertEquals(false, ActiveSessionPicker.hasPlaying(emptyList()))
    }

    @Test
    fun `playingIndex points at the playing session when one package has two sessions`() {
        val sessions = listOf(
            ActiveSessionSnapshot("same.app", PlaybackState.STATE_PAUSED),
            ActiveSessionSnapshot("same.app", PlaybackState.STATE_PLAYING),
        )

        assertEquals(1, ActiveSessionPicker.playingIndex(sessions))
        assertEquals(PlaybackState.STATE_PLAYING, ActiveSessionPicker.playing(sessions)?.state)
        assertNull(ActiveSessionPicker.playingIndex(emptyList()))
    }
}
