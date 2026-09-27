package com.jajafu.x60assistant.tts

import com.jajafu.x60assistant.data.AssistantSettings
import com.jajafu.x60assistant.llm.ChatMessage
import com.jajafu.x60assistant.llm.ChatRequest
import com.jajafu.x60assistant.llm.OpenAiCompatibleLlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingSpeechPlayerTest {
    @Test fun `more than twelve sentences are spoken in order with bounded buffering`() = runTest {
        val expected = (1..40).map { "第${it}句。" }
        val spoken = mutableListOf<String>()
        var highWater = 0
        val result = StreamingSpeechPlayer().collect(
            flowOf(expected.joinToString("")),
            speak = { delay(100); spoken += it },
            onQueue = { depth, high ->
                assertTrue(depth in 0..12)
                highWater = maxOf(highWater, high)
            },
        )
        assertEquals(expected.joinToString(""), result)
        assertEquals(expected, spoken)
        assertEquals(12, highWater)
    }

    @Test fun `speech starts before upstream finishes and applies backpressure`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var emitted = 0
        val spoken = mutableListOf<String>()
        val job = launch {
            StreamingSpeechPlayer(2).collect(flow {
                repeat(20) { emit("句子。" ); emitted++ }
            }, speak = { spoken += it; gate.await() })
        }
        runCurrent()
        assertEquals(1, spoken.size)
        assertTrue(emitted < 20)
        gate.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertEquals(20, spoken.size)
    }

    @Test fun `cancelling blocked speech also cancels producer and discards queued sentences`() = runTest {
        val spoken = mutableListOf<String>()
        var upstreamClosed = false
        val job = launch {
            StreamingSpeechPlayer(2).collect(flow {
                try { repeat(50) { emit("句子。") } } finally { upstreamClosed = true }
            }, speak = { spoken += it; CompletableDeferred<Unit>().await() })
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(upstreamClosed)
        assertEquals(1, spoken.size)
        StreamingSpeechPlayer().collect(flowOf("新回答。"), speak = { spoken += it })
        assertEquals(listOf("句子。", "新回答。"), spoken)
    }

    @Test fun `broken upstream does not flush incomplete sentence`() = runTest {
        val spoken = mutableListOf<String>()
        val result = runCatching {
            StreamingSpeechPlayer().collect(flow {
                emit("不完整的回答")
                throw IOException("disconnected")
            }, speak = { spoken += it })
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(spoken.isEmpty())
    }

    @Test fun `buffered music response can be collected without a speech consumer`() = runTest {
        val response = "無法替你播放音樂。".repeat(30)
        assertEquals(response, StreamingSpeechPlayer().collect(flowOf(response), speak = null))
    }

    @Test fun `speech failure cancels a provider blocked on the next SSE chunk`() = runBlocking {
        val server = MockWebServer()
        val firstChunk = ":${"x".repeat(2_048)}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\"回答。\"}}]}\n\n"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(firstChunk + "data: [DONE]\n\n")
                .throttleBody(firstChunk.toByteArray().size.toLong(), 3, TimeUnit.SECONDS),
        )
        server.start()
        val provider = OpenAiCompatibleLlmProvider(
            settings = AssistantSettings(
                baseUrl = server.url("/v1").toString(),
                model = "test-model",
                connectTimeoutSeconds = 3,
                readTimeoutSeconds = 30,
            ),
            apiKeyProvider = { "test-key".toCharArray() },
            clientBuilder = OkHttpClient.Builder().proxy(Proxy.NO_PROXY),
            allowHttp = true,
        )
        val speechStarted = CompletableDeferred<Unit>()
        val result = async(Dispatchers.Default) {
            runCatching {
                StreamingSpeechPlayer().collect(
                    chunks = provider.streamChat(
                        ChatRequest(listOf(ChatMessage("user", "測試")), 32, 0.0, "test"),
                    ).map { it.text },
                    speak = {
                        speechStarted.complete(Unit)
                        throw TtsException("injected TTS failure")
                    },
                    onSpeechFailure = { provider.cancel() },
                )
            }
        }
        try {
            withTimeout(5_000) { speechStarted.await() }
            val failure = withTimeout(1_500) { result.await() }.exceptionOrNull()
            assertTrue(failure is TtsException)
        } finally {
            provider.cancel()
            result.await()
            provider.close()
            server.shutdown()
        }
    }
}
