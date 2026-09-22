package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.data.AssistantSettings
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleLlmProviderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `streams chunks and stops at done`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"。\"}}]}\n\n" +
                        "data: [DONE]\n\n",
                ),
        )
        val provider = provider()
        val chunks = provider.streamChat(
            ChatRequest(listOf(ChatMessage("user", "測試")), 32, 0.3, "test"),
        ).toList()

        assertEquals(listOf("你好", "。"), chunks.map { it.text })
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertFalse(recorded.getHeader("Authorization").isNullOrBlank())
        provider.close()
    }

    @Test
    fun `health check maps auth failure without retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val provider = provider()

        val health = provider.healthCheck()

        assertFalse(health.success)
        assertEquals(401, health.httpStatus)
        assertEquals(1, server.requestCount)
        provider.close()
    }

    @Test
    fun `health check retries two server failures then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(
            MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"好\"}}]}"),
        )
        val provider = provider()

        val health = provider.healthCheck()

        assertEquals(true, health.success)
        assertEquals(3, server.requestCount)
        provider.close()
    }

    private fun provider() = OpenAiCompatibleLlmProvider(
        settings = AssistantSettings(
            baseUrl = server.url("/v1").toString(),
            model = "test-model",
        ),
        apiKeyProvider = { "sk-unit-test-only".toCharArray() },
        allowHttp = true,
    )
}
