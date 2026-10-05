package com.jajafu.x60assistant.llm

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmTranscriptCorrectorTest {
    @Test
    fun `navigate json with surrounding noise parses destination`() {
        val raw = "好的：{\"corrected\":\"導航到長庚醫院\",\"intent\":\"navigate\"," +
            "\"destination\":\"長庚醫院\",\"query\":null}謝謝"
        val result = LlmTranscriptCorrector.parseJson(raw)
        assertEquals("導航到長庚醫院", result!!.corrected)
        assertEquals(CorrectedIntent.NAVIGATE, result.intent)
        assertEquals("長庚醫院", result.destination)
    }

    @Test
    fun `music json accepts song alias key`() {
        val raw = "{\"corrected\":\"播放晴天\",\"intent\":\"music_search\",\"query\":null,\"song\":\"晴天\"}"
        val result = LlmTranscriptCorrector.parseJson(raw)
        assertEquals(CorrectedIntent.MUSIC_SEARCH, result!!.intent)
        assertEquals("晴天", result.query)
    }

    @Test
    fun `non json returns null`() {
        assertNull(LlmTranscriptCorrector.parseJson("今天天氣真好"))
    }

    @Test
    fun `navigate without destination returns null`() {
        assertNull(
            LlmTranscriptCorrector.parseJson(
                "{\"corrected\":\"導航\",\"intent\":\"navigate\",\"destination\":\"去\",\"query\":null}",
            ),
        )
    }

    @Test
    fun `correction request uses zero temperature and small token budget`() {
        val request = LlmTranscriptCorrector.buildRequest("倒航到長根醫院", "abc123")
        assertEquals(0.0, request.temperature, 0.0)
        assertEquals(LlmTranscriptCorrector.CORRECTION_MAX_TOKENS, request.maxOutputTokens)
        assertEquals("abc123", request.requestId)
        assertEquals("倒航到長根醫院", request.messages.last().content)
    }

    @Test
    fun `correct collects provider chunks before parsing`() = runTest {
        val provider = object : LlmProvider {
            override suspend fun healthCheck() = ProviderHealth(true, "test", latencyMs = 0, message = "")
            override fun streamChat(request: ChatRequest) = flowOf(
                LlmChunk("{\"corrected\":\"導航到長庚"),
                LlmChunk("醫院\",\"intent\":\"navigate\",\"destination\":\"長庚醫院\",\"query\":null}"),
            )
            override fun cancel() = Unit
            override fun close() = Unit
        }
        val result = LlmTranscriptCorrector(provider).correct("倒航到長根醫院", "req1")
        assertEquals(CorrectedIntent.NAVIGATE, result!!.intent)
        assertEquals("長庚醫院", result.destination)
    }
}
