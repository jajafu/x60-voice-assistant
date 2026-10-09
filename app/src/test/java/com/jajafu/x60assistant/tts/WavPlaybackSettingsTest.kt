package com.jajafu.x60assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WavPlaybackSettingsTest {
    @Test
    fun `applied gain keeps player volume neutral to avoid double amplification`() {
        assertEquals(1.0f, WavPlaybackSettings.volume(ttsVolume = 1.8f, gainApplied = true), 0.0f)
    }

    @Test
    fun `without gain the player volume is clamped to the supported range`() {
        assertEquals(0.6f, WavPlaybackSettings.volume(ttsVolume = 0.6f, gainApplied = false), 0.0f)
        assertEquals(1.0f, WavPlaybackSettings.volume(ttsVolume = 1.8f, gainApplied = false), 0.0f)
        assertEquals(0.0f, WavPlaybackSettings.volume(ttsVolume = -1.0f, gainApplied = false), 0.0f)
    }

    @Test
    fun `playback params are skipped when rate and pitch are both default`() {
        assertNull(WavPlaybackSettings.playbackParamsOrNull(speechRate = 1.0f, pitch = 1.0f))
    }

    @Test
    fun `playback params are applied when only one of them differs`() {
        val speedOnly = requireNotNull(WavPlaybackSettings.playbackParamsOrNull(speechRate = 1.5f, pitch = 1.0f))

        assertEquals(listOf(1.5f, 1.0f), speedOnly.toList())

        val pitchOnly = requireNotNull(WavPlaybackSettings.playbackParamsOrNull(speechRate = 1.0f, pitch = 1.2f))

        assertEquals(listOf(1.0f, 1.2f), pitchOnly.toList())
    }
}
