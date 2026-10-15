package com.va.voiceassistant.ui.diagnostics

import com.va.voiceassistant.data.AssistantSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 設定頁數字欄位的輸入規則。
 *
 * 這組測試鎖住 issue #9 的根因：欄位若無法保留輸入中的字串，使用者輸入 0 或修改前導數字時
 * 會被還原成上一個可解析的值。
 */
class NumberFieldPolicyTest {
    private val contextTurns = AssistantSettings.CONTEXT_TURNS_RANGE.let { it.first.toDouble()..it.last.toDouble() }
    private val contextTurnsLabel = "0–12"
    private val maxOutputTokens = AssistantSettings.MAX_OUTPUT_TOKENS_RANGE
        .let { it.first.toDouble()..it.last.toDouble() }

    @Test
    fun `keeps intermediate text that is not yet a number`() {
        listOf("", "-", "0", "0.", ".5", "-3", "12").forEach { text ->
            assertTrue(
                "應保留可作為輸入中間狀態的「$text」",
                NumberFieldPolicy.accepts(text, NumberFieldPolicy.Kind.INTEGER) ||
                    NumberFieldPolicy.accepts(text, NumberFieldPolicy.Kind.DECIMAL),
            )
        }
    }

    @Test
    fun `rejects text that is not a number`() {
        listOf("a", "1a", "1 2", "1.2.3", "+1", "1-", "１", "1234567890").forEach { text ->
            assertFalse(
                "應拒絕「$text」",
                NumberFieldPolicy.accepts(text, NumberFieldPolicy.Kind.INTEGER),
            )
        }
    }

    @Test
    fun `parsing keeps the text the user typed`() {
        // 舊實作把欄位文字當成數字處理，"06" 會被還原成 6；新實作以文字為準。
        assertEquals(6.0, NumberFieldPolicy.parseNumber("06", NumberFieldPolicy.Kind.INTEGER)!!, 0.0)
        assertNull(NumberFieldPolicy.parseNumber("", NumberFieldPolicy.Kind.INTEGER))
        assertNull(NumberFieldPolicy.parseNumber("-", NumberFieldPolicy.Kind.INTEGER))
        assertNull(NumberFieldPolicy.parseNumber("0.", NumberFieldPolicy.Kind.INTEGER))
        assertEquals(0.0, NumberFieldPolicy.parseNumber("0.", NumberFieldPolicy.Kind.DECIMAL)!!, 0.0)
        assertNull(NumberFieldPolicy.parseNumber(".", NumberFieldPolicy.Kind.DECIMAL))
    }

    @Test
    fun `filter rejects the edit and filter keeps the accepted edit`() {
        assertEquals("0", NumberFieldPolicy.filter("0", NumberFieldPolicy.Kind.INTEGER))
        assertNull(NumberFieldPolicy.filter("x", NumberFieldPolicy.Kind.INTEGER))
    }

    @Test
    fun `every value inside the range is accepted including both bounds`() {
        listOf(0, 1, 6, 11, 12).forEach { value ->
            assertNull(
                "Context Turns 應接受 $value",
                NumberFieldPolicy.errorMessage(value.toString(), NumberFieldPolicy.Kind.INTEGER, contextTurns, contextTurnsLabel),
            )
        }
    }

    @Test
    fun `values outside the range are rejected instead of silently clamped`() {
        assertEquals(
            "請輸入 0–12 範圍內的數值。",
            NumberFieldPolicy.errorMessage("13", NumberFieldPolicy.Kind.INTEGER, contextTurns, contextTurnsLabel),
        )
        assertEquals(
            "請輸入 0–12 範圍內的數值。",
            NumberFieldPolicy.errorMessage("-1", NumberFieldPolicy.Kind.INTEGER, contextTurns, contextTurnsLabel),
        )
    }

    @Test
    fun `blank and incomplete input report a message instead of falling back to the default`() {
        assertEquals(
            "請輸入數值。",
            NumberFieldPolicy.errorMessage("", NumberFieldPolicy.Kind.INTEGER, contextTurns, contextTurnsLabel),
        )
        assertEquals(
            "請輸入完整的數值。",
            NumberFieldPolicy.errorMessage("-", NumberFieldPolicy.Kind.INTEGER, contextTurns, contextTurnsLabel),
        )
        assertEquals(
            "只能輸入數字。",
            NumberFieldPolicy.errorMessage("1a", NumberFieldPolicy.Kind.INTEGER, contextTurns, contextTurnsLabel),
        )
    }

    @Test
    fun `ranges with a positive lower bound reject zero`() {
        val label = "${AssistantSettings.MAX_OUTPUT_TOKENS_RANGE.first}–${AssistantSettings.MAX_OUTPUT_TOKENS_RANGE.last}"
        assertEquals(
            "請輸入 $label 範圍內的數值。",
            NumberFieldPolicy.errorMessage("0", NumberFieldPolicy.Kind.INTEGER, maxOutputTokens, label),
        )
        assertNull(
            NumberFieldPolicy.errorMessage(
                AssistantSettings.MAX_OUTPUT_TOKENS_RANGE.first.toString(),
                NumberFieldPolicy.Kind.INTEGER,
                maxOutputTokens,
                label,
            ),
        )
    }
}
