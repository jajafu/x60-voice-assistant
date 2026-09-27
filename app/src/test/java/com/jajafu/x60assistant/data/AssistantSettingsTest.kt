package com.jajafu.x60assistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSettingsTest {
    @Test
    fun `older model selections migrate to bundled X-ASR`() {
        assertEquals(AssistantSettings.DEFAULT_ASR_MODEL_ID, AssistantSettings(asrModelId = "old-model").normalized().asrModelId)
    }

    @Test
    fun `follow up accepts zero and clamps outside range`() {
        assertEquals(0, AssistantSettings(followUpSeconds = 0).normalized().followUpSeconds)
        assertEquals(0, AssistantSettings(followUpSeconds = -1).normalized().followUpSeconds)
        assertEquals(30, AssistantSettings(followUpSeconds = 31).normalized().followUpSeconds)
    }

    @Test
    fun `context turns accepts zero so questions can be sent without history`() {
        assertEquals(0, AssistantSettings(contextTurns = 0).normalized().contextTurns)
        assertEquals(AssistantSettings.CONTEXT_TURNS_RANGE.first, AssistantSettings(contextTurns = -1).normalized().contextTurns)
        assertEquals(AssistantSettings.CONTEXT_TURNS_RANGE.last, AssistantSettings(contextTurns = 13).normalized().contextTurns)
    }

    /**
     * issue #9：設定頁顯示的上下限改用 [AssistantSettings] 的共用常數，這裡確認常數與
     * `normalized()` 的夾限一致，避免畫面驗證通過但存檔時被改掉。
     */
    @Test
    fun `declared ranges match normalized clamping at both bounds`() {
        AssistantSettings(
            temperature = AssistantSettings.TEMPERATURE_RANGE.endInclusive,
            maxOutputTokens = AssistantSettings.MAX_OUTPUT_TOKENS_RANGE.last,
            connectTimeoutSeconds = AssistantSettings.CONNECT_TIMEOUT_SECONDS_RANGE.last,
            readTimeoutSeconds = AssistantSettings.READ_TIMEOUT_SECONDS_RANGE.first,
            contextTurns = AssistantSettings.CONTEXT_TURNS_RANGE.first,
            followUpSeconds = AssistantSettings.FOLLOW_UP_SECONDS_RANGE.first,
            waitForSpeechMs = AssistantSettings.WAIT_FOR_SPEECH_MS_RANGE.first,
            minSpeechMs = AssistantSettings.MIN_SPEECH_MS_RANGE.last,
            trailingSilenceMs = AssistantSettings.TRAILING_SILENCE_MS_RANGE.first,
            maxUtteranceMs = AssistantSettings.MAX_UTTERANCE_MS_RANGE.last,
        ).let { atBounds ->
            assertEquals(atBounds, atBounds.normalized())
        }

        AssistantSettings(
            temperature = AssistantSettings.TEMPERATURE_RANGE.endInclusive + 0.1,
            maxOutputTokens = AssistantSettings.MAX_OUTPUT_TOKENS_RANGE.last + 1,
            connectTimeoutSeconds = AssistantSettings.CONNECT_TIMEOUT_SECONDS_RANGE.last + 1,
            readTimeoutSeconds = AssistantSettings.READ_TIMEOUT_SECONDS_RANGE.first - 1,
            contextTurns = AssistantSettings.CONTEXT_TURNS_RANGE.last + 1,
            followUpSeconds = AssistantSettings.FOLLOW_UP_SECONDS_RANGE.last + 1,
            waitForSpeechMs = AssistantSettings.WAIT_FOR_SPEECH_MS_RANGE.first - 1,
            minSpeechMs = AssistantSettings.MIN_SPEECH_MS_RANGE.last + 1,
            trailingSilenceMs = AssistantSettings.TRAILING_SILENCE_MS_RANGE.first - 1,
            maxUtteranceMs = AssistantSettings.MAX_UTTERANCE_MS_RANGE.first - 1,
        ).let { outside ->
            assertEquals(AssistantSettings.TEMPERATURE_RANGE.endInclusive, outside.normalized().temperature, 0.0)
            assertEquals(AssistantSettings.MAX_OUTPUT_TOKENS_RANGE.last, outside.normalized().maxOutputTokens)
            assertEquals(AssistantSettings.CONNECT_TIMEOUT_SECONDS_RANGE.last, outside.normalized().connectTimeoutSeconds)
            assertEquals(AssistantSettings.READ_TIMEOUT_SECONDS_RANGE.first, outside.normalized().readTimeoutSeconds)
            assertEquals(AssistantSettings.CONTEXT_TURNS_RANGE.last, outside.normalized().contextTurns)
            assertEquals(AssistantSettings.FOLLOW_UP_SECONDS_RANGE.last, outside.normalized().followUpSeconds)
            assertEquals(AssistantSettings.WAIT_FOR_SPEECH_MS_RANGE.first, outside.normalized().waitForSpeechMs)
            assertEquals(AssistantSettings.MIN_SPEECH_MS_RANGE.last, outside.normalized().minSpeechMs)
            assertEquals(AssistantSettings.TRAILING_SILENCE_MS_RANGE.first, outside.normalized().trailingSilenceMs)
            assertEquals(AssistantSettings.MAX_UTTERANCE_MS_RANGE.first, outside.normalized().maxUtteranceMs)
        }
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
