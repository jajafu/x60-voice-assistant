package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.data.AssistantSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiAudioSpeechClientTest {
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
    fun `posts speech request and returns audio bytes`() = runTest {
        val audio = byteArrayOf(0x49, 0x44, 0x33, 0x04)
        server.enqueue(MockResponse().setBody(Buffer().write(audio)).setHeader("Content-Type", "audio/mpeg"))
        val client = client()

        val result = client.synthesize("測試語音")

        assertTrue(result.contentEquals(audio))
        val recorded = server.takeRequest()
        assertEquals("/v1/audio/speech", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("gpt-4o-mini-tts", body["model"]!!.jsonPrimitive.content)
        assertEquals("測試語音", body["input"]!!.jsonPrimitive.content)
        assertEquals("alloy", body["voice"]!!.jsonPrimitive.content)
        client.close()
    }

    @Test
    fun `auth failure maps without retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val client = client()

        val error = runCatching { client.synthesize("測試") }.exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.AUTH_FAILED, error.code)
        assertEquals(1, server.requestCount)
        client.close()
    }

    @Test
    fun `blank and oversized input never reach the network`() = runTest {
        val client = client()

        val blankError = runCatching { client.synthesize("  ") }.exceptionOrNull() as LlmException
        val oversizedError =
            runCatching { client.synthesize("a".repeat(AssistantSettings.CLOUD_TTS_MAX_CHARS + 1)) }
                .exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.CONFIG_INVALID, blankError.code)
        assertEquals(LlmErrorCode.CONFIG_INVALID, oversizedError.code)
        assertEquals(0, server.requestCount)
        client.close()
    }

    private fun client(): OpenAiAudioSpeechClient = OpenAiAudioSpeechClient(
        settings = AssistantSettings(
            baseUrl = server.url("/v1/").toString(),
            cloudTtsModel = "gpt-4o-mini-tts",
            cloudTtsVoice = "alloy",
        ),
        apiKeyProvider = { "test-key".toCharArray() },
        allowHttp = true,
    )
}
