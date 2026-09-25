package com.jajafu.x60assistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSettingsTest {
    @Test
    fun `follow up accepts zero and clamps outside range`() {
        assertEquals(0, AssistantSettings(followUpSeconds = 0).normalized().followUpSeconds)
        assertEquals(0, AssistantSettings(followUpSeconds = -1).normalized().followUpSeconds)
        assertEquals(30, AssistantSettings(followUpSeconds = 31).normalized().followUpSeconds)
    }

    @Test
    fun `music app identifiers are normalized for persistence`() {
        val settings = AssistantSettings(
            musicAppName = "  Spotify  ",
            musicPackageName = " com.spotify.music ",
            musicServiceName = " com.spotify.music.MediaService ",
        ).normalized()

        assertEquals("Spotify", settings.musicAppName)
        assertEquals("com.spotify.music", settings.musicPackageName)
        assertEquals("com.spotify.music.MediaService", settings.musicServiceName)
    }

    @Test
    fun `wake acknowledgement defaults and trims to a short spoken response`() {
        assertEquals(
            AssistantSettings.DEFAULT_WAKE_ACKNOWLEDGEMENT,
            AssistantSettings(wakeAcknowledgement = "   ").normalized().wakeAcknowledgement,
        )
        assertEquals(
            "我在",
            AssistantSettings(wakeAcknowledgement = "  我在  ").normalized().wakeAcknowledgement,
        )
        assertEquals(
            AssistantSettings.MAX_WAKE_ACKNOWLEDGEMENT_LENGTH,
            AssistantSettings(wakeAcknowledgement = "嗄".repeat(30)).normalized()
                .wakeAcknowledgement.length,
        )
    }
}
