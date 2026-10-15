package com.va.voiceassistant.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    @Test
    fun `joins data lines and ignores comments`() {
        val events = mutableListOf<String>()
        val parser = SseParser(events::add)
        parser.acceptLine(": keepalive")
        parser.acceptLine("event: message")
        parser.acceptLine("data: first")
        parser.acceptLine("data: second")
        parser.acceptLine("")
        parser.acceptLine("data: [DONE]")
        parser.finish()

        assertEquals(listOf("first\nsecond", "[DONE]"), events)
    }
}
