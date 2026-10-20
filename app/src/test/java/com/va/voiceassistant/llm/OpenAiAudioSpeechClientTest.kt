package com.va.voiceassistant.llm

import com.va.voiceassistant.audio.TestWav
import com.va.voiceassistant.data.AssistantSettings
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

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
    fun `posts speech request and returns wav bytes`() = runTest {
        val audio = TestWav.pcmToWav(shortArrayOf(0, 1_000, -1_000))
        server.enqueue(MockResponse().setBody(Buffer().write(audio)).setHeader("Content-Type", "audio/wav"))
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
        assertEquals("wav", body["response_format"]!!.jsonPrimitive.content)
        client.close()
    }

    @Test
    fun `non wav response reports content type and header instead of failing at playback`() = runTest {
        // Provider 忽略 response_format=wav 時會回 MP3；MediaPlayer 只會給出不透明的
        // 1/-2147483648，必須在此指出實際收到的內容。
        val mp3 = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00)
        server.enqueue(MockResponse().setBody(Buffer().write(mp3)).setHeader("Content-Type", "audio/mpeg"))
        val client = client()

        val error = runCatching { client.synthesize("測試語音") }.exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.RESPONSE_INVALID, error.code)
        assertTrue(error.message.orEmpty().contains("audio/mpeg"))
        assertTrue(error.message.orEmpty().contains("49 44 33 04"))
        client.close()
    }

    @Test
    fun `json error body with success status is rejected before playback`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"error":{"message":"model not supported"}}""")
                .setHeader("Content-Type", "application/json"),
        )
        val client = client()

        val error = runCatching { client.synthesize("測試語音") }.exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.RESPONSE_INVALID, error.code)
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

    @Test
    fun `cancel interrupts an in-flight speech request`() {
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(byteArrayOf(1, 2, 3)))
                .setBodyDelay(500, TimeUnit.MILLISECONDS),
        )
        val client = client()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Throwable?> {
                runCatching { runBlocking { client.synthesize("測試") } }.exceptionOrNull()
            }
            server.takeRequest()
            client.cancel()
            val error = future.get(2, TimeUnit.SECONDS)
            assertTrue(error is LlmException)
            assertEquals(LlmErrorCode.NETWORK_UNAVAILABLE, (error as LlmException).code)
        } finally {
            client.close()
            executor.shutdownNow()
            TimeUnit.MILLISECONDS.sleep(700)
        }
    }

    private fun client(): OpenAiAudioSpeechClient = OpenAiAudioSpeechClient(
        settings = AssistantSettings(
            audioBaseUrl = server.url("/v1/").toString(),
            cloudTtsModel = "gpt-4o-mini-tts",
            cloudTtsVoice = "alloy",
        ),
        apiKeyProvider = { "test-key".toCharArray() },
        allowHttp = true,
    )
}
