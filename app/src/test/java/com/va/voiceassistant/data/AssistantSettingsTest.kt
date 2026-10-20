package com.va.voiceassistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSettingsTest {
    @Test
    fun `voice volume control is on by default and survives normalization`() {
        assertEquals(true, AssistantSettings.DEFAULT_VOICE_VOLUME_CONTROL)
        assertTrue(AssistantSettings().voiceVolumeControl)
        // normalized() 只夾數值範圍，不該把使用者關掉的開關打開。
        assertFalse(AssistantSettings(voiceVolumeControl = false).normalized().voiceVolumeControl)
    }

    @Test
    fun `older model selections migrate to bundled X-ASR`() {
        assertEquals(AssistantSettings.DEFAULT_ASR_MODEL_ID, AssistantSettings(asrModelId = "old-model").normalized().asrModelId)
    }

    @Test
    fun `llm transcript correction is enabled by default`() {
        assertEquals(true, AssistantSettings().llmCorrectionEnabled)
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
    /**
     * issue #14：TTS 音量預設 1.0（維持引擎預設值），範圍 0.0–2.0。
     * 0 是靜音，超過 1.0 可能破音，因此夾限必須兩端都成立。
     */
    @Test
    fun `tts volume defaults to the engine default and clamps both bounds`() {
        assertEquals(1.0f, AssistantSettings().ttsVolume, 0.0f)
        assertEquals(0.0f, AssistantSettings(ttsVolume = 0.0f).normalized().ttsVolume, 0.0f)
        assertEquals(2.0f, AssistantSettings(ttsVolume = 2.0f).normalized().ttsVolume, 0.0f)
        assertEquals(AssistantSettings.TTS_VOLUME_RANGE.start, AssistantSettings(ttsVolume = -1f).normalized().ttsVolume, 0.0f)
        assertEquals(
            AssistantSettings.TTS_VOLUME_RANGE.endInclusive,
            AssistantSettings(ttsVolume = 9f).normalized().ttsVolume,
            0.0f,
        )
    }

    @Test
    fun `tts engine and voice identifiers are length limited before persistence`() {
        val settings = AssistantSettings(ttsEngine = "a".repeat(500), ttsVoice = "b".repeat(500)).normalized()
        assertEquals(AssistantSettings.MAX_TTS_IDENTIFIER_LENGTH, settings.ttsEngine.length)
        assertEquals(AssistantSettings.MAX_TTS_IDENTIFIER_LENGTH, settings.ttsVoice.length)
    }

    @Test
    fun `cloud voice is off by default and uses audio credentials`() {
        val defaults = AssistantSettings()
        assertFalse(defaults.cloudTtsEnabled)
        assertEquals("gpt-4o-mini-tts", defaults.cloudTtsModel)
        assertEquals("alloy", defaults.cloudTtsVoice)
        // 沒有 Audio URL／Key 時即使開關打開也不該走雲端；兩者都具備才啟用。
        assertFalse(AssistantSettings(cloudTtsEnabled = true).cloudTtsReady(hasAudioApiKey = false))
        assertFalse(AssistantSettings(cloudTtsEnabled = true).cloudTtsReady(hasAudioApiKey = true))
        val configured = AssistantSettings(audioBaseUrl = "https://audio.example.com")
        assertTrue(configured.copy(cloudTtsEnabled = true).cloudTtsReady(hasAudioApiKey = true))
    }

    @Test
    fun `cloud model blanks fall back to defaults and identifiers are length limited`() {
        val settings = AssistantSettings(
            cloudTtsModel = "  ",
            cloudTtsVoice = "  ",
        ).normalized()
        assertEquals(AssistantSettings.DEFAULT_CLOUD_TTS_MODEL, settings.cloudTtsModel)
        assertEquals(AssistantSettings.DEFAULT_CLOUD_TTS_VOICE, settings.cloudTtsVoice)
        val long = AssistantSettings(
            cloudTtsModel = "m".repeat(500),
            cloudTtsVoice = "v".repeat(500),
        ).normalized()
        assertEquals(AssistantSettings.MAX_CLOUD_IDENTIFIER_LENGTH, long.cloudTtsModel.length)
        assertEquals(AssistantSettings.MAX_CLOUD_IDENTIFIER_LENGTH, long.cloudTtsVoice.length)
    }

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
