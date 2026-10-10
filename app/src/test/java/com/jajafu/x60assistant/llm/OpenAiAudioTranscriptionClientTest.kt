package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.asr.CloudSpeechRecognizerEngine
import com.jajafu.x60assistant.data.AssistantSettings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OpenAiAudioTranscriptionClientTest {
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
    fun `uploads wav multipart and returns transcript text`() {
        server.enqueue(MockResponse().setBody("{\"text\":\"今天天氣很好\"}"))
        val client = client(language = "zh")

        val wav = CloudSpeechRecognizerEngine.pcmToWav(ShortArray(1600) { 0 })
        val text = client.transcribe(wav)

        assertEquals("今天天氣很好", text)
        val recorded = server.takeRequest()
        assertEquals("/v1/audio/transcriptions", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("whisper-1"))
        assertTrue(body.contains("utterance.wav"))
        assertTrue(body.contains("name=\"language\""))
        client.close()
    }

    @Test
    fun `blank language is omitted and auth failure maps without retry`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val client = client(language = "")

        val error = runCatching {
            client.transcribe(CloudSpeechRecognizerEngine.pcmToWav(ShortArray(160) { 1 }))
        }.exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.AUTH_FAILED, error.code)
        assertEquals(1, server.requestCount)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(!body.contains("name=\"language\""))
        client.close()
    }

    @Test
    fun `header-only wav never reaches the network`() {
        val client = client(language = "")

        val error = runCatching {
            client.transcribe(CloudSpeechRecognizerEngine.pcmToWav(ShortArray(0)))
        }.exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.CONFIG_INVALID, error.code)
        assertEquals(0, server.requestCount)
        client.close()
    }

    @Test
    fun `response without a text field is an error rather than an empty transcript`() {
        // 靜默回空字串會被 Orchestrator 記成 ASR_NO_RESULT，使用者只會覺得自己沒說話，
        // 無法分辨 Provider 回應格式改變。
        server.enqueue(MockResponse().setBody("""{"segments":[]}"""))
        val client = client(language = "")

        val error = runCatching {
            client.transcribe(CloudSpeechRecognizerEngine.pcmToWav(ShortArray(160) { 1 }))
        }.exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.RESPONSE_INVALID, error.code)
        assertTrue(error.message.orEmpty().contains("text"))
        client.close()
    }

    @Test
    fun `blank text field is also rejected`() {
        server.enqueue(MockResponse().setBody("""{"text":"   "}"""))
        val client = client(language = "")

        val error = runCatching {
            client.transcribe(CloudSpeechRecognizerEngine.pcmToWav(ShortArray(160) { 1 }))
        }.exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.RESPONSE_INVALID, error.code)
        client.close()
    }

    @Test
    fun `non json body is rejected`() {
        server.enqueue(MockResponse().setBody("<html>gateway timeout</html>"))
        val client = client(language = "")

        val error = runCatching {
            client.transcribe(CloudSpeechRecognizerEngine.pcmToWav(ShortArray(160) { 1 }))
        }.exceptionOrNull() as LlmException

        assertEquals(LlmErrorCode.RESPONSE_INVALID, error.code)
        client.close()
    }

    @Test
    fun `cancel interrupts an in-flight transcription request`() {
        server.enqueue(
            MockResponse()
                .setBody("{\"text\":\"延遲\"}")
                .setBodyDelay(500, TimeUnit.MILLISECONDS),
        )
        val client = client(language = "")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Throwable?> {
                runCatching {
                    client.transcribe(CloudSpeechRecognizerEngine.pcmToWav(ShortArray(160) { 1 }))
                }.exceptionOrNull()
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

    private fun client(language: String): OpenAiAudioTranscriptionClient = OpenAiAudioTranscriptionClient(
        settings = AssistantSettings(
            audioBaseUrl = server.url("/v1/").toString(),
            cloudAsrModel = "whisper-1",
            cloudAsrLanguage = language,
        ),
        apiKeyProvider = { "test-key".toCharArray() },
        allowHttp = true,
    )
}
