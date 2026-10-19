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
}
