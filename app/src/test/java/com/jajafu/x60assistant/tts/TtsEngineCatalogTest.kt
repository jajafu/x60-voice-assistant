package com.jajafu.x60assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * voice 清單的排序與顯示不依賴真實 TTS 引擎或 Context，因此可以在 JVM 直接驗證。
 * 排序必須與 [AndroidTtsEngine] 的自動選擇一致：離線中文優先，zh-TW 再優先。
 */
class TtsEngineCatalogTest {
    private fun voice(name: String, locale: String, network: Boolean) =
        TtsVoiceOption(name = name, label = name, locale = locale, requiresNetwork = network)

    @Test
    fun `offline traditional chinese sorts before every other voice`() {
        val ordered = TtsEngineCatalog.preferredOrder(
            listOf(
                voice("en-US", "en-US", network = false),
                voice("zh-CN-net", "zh-CN", network = true),
                voice("zh-CN", "zh-CN", network = false),
                voice("zh-TW", "zh-TW", network = false),
            ),
        )
        assertEquals(listOf("zh-TW", "zh-CN", "zh-CN-net", "en-US"), ordered.map { it.name })
    }

    @Test
    fun `non chinese voices keep their relative order by label`() {
        val ordered = TtsEngineCatalog.preferredOrder(
            listOf(
                voice("en-GB", "en-GB", network = false),
                voice("en-US", "en-US", network = false),
            ),
        )
        assertEquals(listOf("en-GB", "en-US"), ordered.map { it.name })
    }

    @Test
    fun `display label always states the locale and whether network is needed`() {
        assertEquals(
            "zh-TW-voice（zh-TW・離線）",
            voice("zh-TW-voice", "zh-TW", network = false).displayLabel(),
        )
        assertEquals(
            "remote（zh-CN・需要網路）",
            voice("remote", "zh-CN", network = true).displayLabel(),
        )
    }

    @Test
    fun `unknown locale does not produce a blank label`() {
        val label = voice("mystery", "", network = false).displayLabel()
        assertTrue(label.contains("未知語言"))
    }
}
