package com.va.voiceassistant.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class SystemSpeechRecognizerSupportTest {
    @Test
    fun `zh family languages count as chinese support`() {
        assertTrue(SystemSpeechRecognizerSupport.hasChinese(listOf("zh-TW", "en-US")))
        assertTrue(SystemSpeechRecognizerSupport.hasChinese(listOf("en-US", "zh-CN")))
        assertTrue(SystemSpeechRecognizerSupport.hasChinese(listOf("zh")))
        assertTrue(SystemSpeechRecognizerSupport.hasChinese(listOf("ZH-TW")))
    }

    @Test
    fun `matchChinese prefers traditional chinese and falls back to any zh`() {
        assertEquals("zh-TW", SystemSpeechRecognizerSupport.matchChinese(listOf("en-US", "zh-CN", "zh-TW")))
        assertEquals("zh-Hant", SystemSpeechRecognizerSupport.matchChinese(listOf("zh-Hant", "zh-CN")))
        assertEquals("zh-CN", SystemSpeechRecognizerSupport.matchChinese(listOf("en-US", "zh-CN")))
        assertEquals("zh", SystemSpeechRecognizerSupport.matchChinese(listOf("zh")))
        assertEquals(null, SystemSpeechRecognizerSupport.matchChinese(listOf("en-US", "  ")))
    }

    @Test
    fun `without zh languages the probe is negative`() {
        assertFalse(SystemSpeechRecognizerSupport.hasChinese(emptyList()))
        assertFalse(SystemSpeechRecognizerSupport.hasChinese(listOf("en-US")))
        assertFalse(SystemSpeechRecognizerSupport.hasChinese(listOf("ja-JP", "ko-KR")))
    }

    @Test
    fun `model id exposes the system recognizer identity`() {
        assertEquals("system/android-zh", SystemSpeechRecognizerSupport.MODEL_ID)
    }

    @Test
    fun `check support answer with chinese confirms the preferred language`() {
        val result = SystemSpeechRecognizerSupport.resolve(
            SystemSpeechRecognizerSupport.ProbeFacts(supportLanguages = listOf("en-US", "zh-CN", "zh-TW")),
        )
        assertEquals(SystemSpeechRecognizerSupport.SystemRecognitionProbe.Confirmed("zh-TW"), result)
    }

    @Test
    fun `check support answer without chinese is a definite no`() {
        val result = SystemSpeechRecognizerSupport.resolve(
            SystemSpeechRecognizerSupport.ProbeFacts(
                supportLanguages = listOf("en-US", "ja-JP"),
                languageDetails = listOf("zh-TW"),
            ),
        )
        assertEquals(
            SystemSpeechRecognizerSupport.SystemRecognitionProbe.Unavailable("chinese-unsupported"),
            result,
        )
    }

    @Test
    fun `explicit unsupported answer wins over a later language list`() {
        val result = SystemSpeechRecognizerSupport.resolve(
            SystemSpeechRecognizerSupport.ProbeFacts(
                supportUnsupported = true,
                languageDetails = listOf("zh-TW"),
            ),
        )
        assertEquals(
            SystemSpeechRecognizerSupport.SystemRecognitionProbe.Unavailable("chinese-unsupported"),
            result,
        )
    }

    @Test
    fun `language details are used when the check support api had no answer`() {
        val result = SystemSpeechRecognizerSupport.resolve(
            SystemSpeechRecognizerSupport.ProbeFacts(
                supportReason = "check-support-error-14",
                languageDetails = listOf("en-US", "zh-Hant"),
            ),
        )
        assertEquals(SystemSpeechRecognizerSupport.SystemRecognitionProbe.Confirmed("zh-Hant"), result)
    }

    @Test
    fun `both sources failing still trusts the system recognizer with an unverified reason`() {
        val result = SystemSpeechRecognizerSupport.resolve(
            SystemSpeechRecognizerSupport.ProbeFacts(supportReason = "check-support-error-5"),
        )
        assertEquals(
            SystemSpeechRecognizerSupport.SystemRecognitionProbe.Confirmed(
                "zh-TW",
                unverifiedReason = "check-support-error-5+language-details-unavailable",
            ),
            result,
        )
    }

    @Test
    fun `a source that answers without chinese still blocks the system recognizer`() {
        assertEquals(
            SystemSpeechRecognizerSupport.SystemRecognitionProbe.Unavailable("chinese-unsupported"),
            SystemSpeechRecognizerSupport.resolve(
                SystemSpeechRecognizerSupport.ProbeFacts(languageDetails = listOf("en-US")),
            ),
        )
    }

    @Test
    fun `older devices without the check support api fall back to the unverified default`() {
        assertEquals(
            SystemSpeechRecognizerSupport.SystemRecognitionProbe.Confirmed(
                "zh-TW",
                unverifiedReason = "language-details-unavailable",
            ),
            SystemSpeechRecognizerSupport.resolve(SystemSpeechRecognizerSupport.ProbeFacts()),
        )
    }
}
