package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.data.AssistantSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudAudioSettingsValidatorTest {
    @Test
    fun `disabled cloud audio does not require credentials`() {
        assertNull(CloudAudioSettingsValidator.validate(AssistantSettings(), false, allowHttp = false))
    }

    @Test
    fun `enabled cloud audio requires key and base url`() {
        val enabled = AssistantSettings(cloudTtsEnabled = true)
        assertEquals(
            "雲端語音已開啟，但尚未設定 API Key。",
            CloudAudioSettingsValidator.validateTts(enabled, false, allowHttp = false),
        )
        assertEquals(
            "雲端語音已開啟，但尚未設定 Base URL。",
            CloudAudioSettingsValidator.validateTts(enabled, true, allowHttp = false),
        )
    }

    @Test
    fun `enabled cloud audio validates audio endpoint`() {
        val settings = AssistantSettings(
            baseUrl = "http://example.com/v1",
            cloudAsrEnabled = true,
        )
        val error = CloudAudioSettingsValidator.validateAsr(settings, true, allowHttp = false)
        assertEquals("雲端語音 Base URL 無效：此版本不允許該 HTTP Host", error)
    }

    @Test
    fun `configured cloud audio accepts shared v1 base url`() {
        val settings = AssistantSettings(
            baseUrl = "https://api.example.com/v1",
            cloudTtsEnabled = true,
            cloudAsrEnabled = true,
        )
        assertNull(CloudAudioSettingsValidator.validate(settings, true, allowHttp = false))
    }
}
