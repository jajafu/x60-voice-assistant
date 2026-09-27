package com.jajafu.x60assistant.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

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
}
