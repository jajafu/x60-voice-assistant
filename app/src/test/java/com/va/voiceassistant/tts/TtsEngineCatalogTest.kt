package com.va.voiceassistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * voice 清單的篩選、排序與顯示不依賴真實 TTS 引擎，因此在 JVM 直接驗證。
 *
 * locale 樣本取自測試手機（歷史機型 vivo X60，Android 13）上 `com.vivo.aiservice` 的實際回報：引擎用的是
 * **以書寫系統標記**的 `zh-Hant`／`zh-Hans`，不是以地區標記的 `zh-TW`／`zh-CN`。
 */
class TtsEngineCatalogTest {
    private fun voice(name: String, locale: String, network: Boolean = false) =
        TtsVoiceOption(name = name, label = name, locale = locale, requiresNetwork = network)

    @Test
    fun `non chinese voices are removed from the settings list`() {
        val ordered = TtsEngineCatalog.preferredOrder(
            listOf(
                voice("af", "af"),
                voice("zh-Hant", "zh-Hant"),
                voice("ak", "ak"),
                voice("zh-Hans", "zh-Hans"),
                voice("am", "am"),
            ),
        )
        assertEquals(listOf("zh-Hant", "zh-Hans"), ordered.map { it.name })
    }

    @Test
    fun `traditional chinese sorts before simplified and offline before network`() {
        val ordered = TtsEngineCatalog.preferredOrder(
            listOf(
                voice("zh-Hans", "zh-Hans"),
                voice("zh-TW-net", "zh-TW", network = true),
                voice("zh-Hant", "zh-Hant"),
                voice("zh-TW", "zh-TW"),
            ),
        )
        assertEquals(listOf("zh-Hant", "zh-TW", "zh-TW-net", "zh-Hans"), ordered.map { it.name })
    }

    @Test
    fun `the whole list is shown when the engine has no chinese voice at all`() {
        val ordered = TtsEngineCatalog.preferredOrder(
            listOf(voice("en-US", "en-US"), voice("ja", "ja")),
        )
        assertEquals(listOf("en-US", "ja"), ordered.map { it.name })
    }

    @Test
    fun `script based and region based traditional tags are both recognised`() {
        assertTrue("zh-Hant".isTraditionalChineseLocale())
        assertTrue("zh-TW".isTraditionalChineseLocale())
        assertTrue("zh-HK".isTraditionalChineseLocale())
        assertFalse("zh-Hans".isTraditionalChineseLocale())
        assertFalse("zh-CN".isTraditionalChineseLocale())
    }

    @Test
    fun `chinese detection accepts every chinese tag and rejects lookalikes`() {
        listOf("zh", "zh-Hant", "zh-Hans", "zh-TW", "zh-CN", "zh-HK").forEach {
            assertTrue("$it 應視為中文", it.isChineseLocale())
        }
        listOf("af", "ak", "en-US", "ja", "az-Latn").forEach {
            assertFalse("$it 不應視為中文", it.isChineseLocale())
        }
    }

    @Test
    fun `display label always states the locale and whether network is needed`() {
        assertEquals(
            "zh-Hant（zh-Hant・離線）",
            voice("zh-Hant", "zh-Hant").displayLabel(),
        )
        assertEquals(
            "remote（zh-CN・需要網路）",
            voice("remote", "zh-CN", network = true).displayLabel(),
        )
    }

    @Test
    fun `unknown locale does not produce a blank label`() {
        assertTrue(voice("mystery", "").displayLabel().contains("未知語言"))
    }
}
