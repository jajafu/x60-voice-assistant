package com.jajafu.x60assistant.llm

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
}
