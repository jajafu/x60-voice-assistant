package com.va.voiceassistant.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationHistoryTest {
    @Test
    fun `keeps only configured recent turns in memory`() {
        val history = ConversationHistory(2)
        history.add("一", "答一")
        history.add("二", "答二")
        history.add("三", "答三")
        val messages = history.messages("規則", "四")
        assertEquals(listOf("規則", "二", "答二", "三", "答三", "四"), messages.map { it.content })
    }

    /** 喚醒詞開新一輪時會呼叫 clear()，之後的請求不得再帶到任何舊輪次。 */
    @Test
    fun `clearing drops every retained turn before the next topic`() {
        val history = ConversationHistory(3)
        history.add("舊問題一", "舊回答一")
        history.add("舊問題二", "舊回答二")
        history.clear()
        history.add("新問題", "新回答")
        val contents = history.messages("規則", "再問一次").map { it.content }
        assertEquals(listOf("規則", "新問題", "新回答", "再問一次"), contents)
    }
}
