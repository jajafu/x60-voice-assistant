package com.jajafu.x60assistant.orchestration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualCloseCommandRouterTest {
    @Test
    fun `recognizes close-only phrases locally`() {
        listOf(
            "關閉",
            "請關閉",
            "幫我關掉",
            "停止",
            "關閉鬧鐘",
            "把音樂關掉",
            "停止播放",
        ).forEach { phrase ->
            assertTrue(phrase, ContextualCloseCommandRouter.matches(phrase))
        }
    }

    @Test
    fun `does not swallow unrelated close requests`() {
        listOf(
            "關閉藍牙",
            "如何關閉通知",
            "關閉之後會怎樣",
            "不要關閉音樂",
        ).forEach { phrase ->
            assertFalse(phrase, ContextualCloseCommandRouter.matches(phrase))
        }
    }
}
