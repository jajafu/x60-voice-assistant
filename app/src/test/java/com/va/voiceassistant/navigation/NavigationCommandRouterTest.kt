package com.va.voiceassistant.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationCommandRouterTest {
    private fun destinationOf(transcript: String, wakePhrase: String = "小助手"): String? =
        (NavigationCommandRouter.parse(transcript, wakePhrase) as? NavigationCommand.Navigate)?.destination

    @Test
    fun `parses common traditional chinese navigation phrases`() {
        assertEquals("台北101", destinationOf("導航到台北101"))
        assertEquals("台北101", destinationOf("帶我去台北101"))
        assertEquals("台北101", destinationOf("開地圖到台北101"))
        assertEquals("台北101", destinationOf("我要去台北101"))
    }

    @Test
    fun `parses simplified chinese variants`() {
        assertEquals("台北101", destinationOf("导航到台北101"))
        assertEquals("台北101", destinationOf("带我去台北101"))
        assertEquals("台北101", destinationOf("开地图到台北101"))
    }

    @Test
    fun `strips polite prefix and punctuation`() {
        assertEquals("台北101", destinationOf("請幫我導航到台北101"))
        assertEquals("台北101", destinationOf("導航到台北101。"))
        assertEquals("台北101", destinationOf("小助手，導航到台北101！"))
        assertEquals("台北101", destinationOf("小助手導航到台北101"))
    }

    @Test
    fun `accepts a bare navigate prefix followed by a destination`() {
        assertEquals("台北101", destinationOf("導航台北101"))
    }

    @Test
    fun `ignores app names in the request`() {
        assertEquals("台北101", destinationOf("用Google地圖導航到台北101"))
        assertEquals("台北101", destinationOf("Google Maps導航到台北101"))
    }

    @Test
    fun `does not trigger without a destination`() {
        // 喚醒後的半句或誤字不該開啟地圖。
        assertNull(NavigationCommandRouter.parse("導航"))
        assertNull(NavigationCommandRouter.parse("導航到"))
        assertNull(NavigationCommandRouter.parse("帶我去"))
    }

    @Test
    fun `does not trigger on questions or negations`() {
        assertNull(NavigationCommandRouter.parse("怎麼導航到台北101"))
        assertNull(NavigationCommandRouter.parse("為什麼要去台北101"))
        assertNull(NavigationCommandRouter.parse("導航到台北101嗎"))
        assertNull(NavigationCommandRouter.parse("不要導航到台北101"))
        assertNull(NavigationCommandRouter.parse("不用帶我去台北101"))
    }

    @Test
    fun `does not trigger on unrelated requests`() {
        assertNull(NavigationCommandRouter.parse("這是什麼歌"))
        assertNull(NavigationCommandRouter.parse("幫我設定一個九點的提醒"))
        assertNull(NavigationCommandRouter.parse("台北101有多高"))
    }

    @Test
    fun `rejects a single character destination as a likely asr artifact`() {
        assertNull(NavigationCommandRouter.parse("導航到樓"))
    }

    @Test
    fun `rejects blank input`() {
        assertNull(NavigationCommandRouter.parse("   "))
    }
}
