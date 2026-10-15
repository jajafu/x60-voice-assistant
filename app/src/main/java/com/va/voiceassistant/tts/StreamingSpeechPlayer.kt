package com.va.voiceassistant.tts

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/** One response owns its queue and consumer; cancellation stops both before another response starts. */
class StreamingSpeechPlayer(private val capacity: Int = 12) {
    init { require(capacity > 0) }

    suspend fun collect(
        chunks: Flow<String>,
        speak: (suspend (String) -> Unit)?,
        onText: (String) -> Unit = {},
        onQueue: (Int, Int) -> Unit = { _, _ -> },
        onSpeechFailure: (Throwable) -> Unit = {},
    ): String = coroutineScope {
        // Permits bound pending sentences and let the producer suspend without dropping text.
        val slots = Semaphore(capacity)
        val sentences = Channel<String>(Channel.UNLIMITED)
        val depth = AtomicInteger()
        val highWater = AtomicInteger()
        val consumer = if (speak == null) null else launch {
            for (sentence in sentences) {
                val remaining = depth.decrementAndGet()
                slots.release()
                onQueue(remaining, highWater.get())
                try {
                    speak(sentence)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    runCatching { onSpeechFailure(error) }
                    throw error
                }
            }
        }
        suspend fun enqueue(sentence: String) {
            slots.acquire()
            val queued = depth.incrementAndGet()
            highWater.accumulateAndGet(queued, ::maxOf)
            onQueue(queued, highWater.get())
            sentences.send(sentence)
        }
        val answer = StringBuilder()
        val segmenter = SpeechSegmenter()
        try {
            chunks.collect { chunk ->
                answer.append(chunk)
                onText(answer.toString())
                if (speak != null) segmenter.accept(chunk).forEach { enqueue(it) }
            }
            if (speak != null) segmenter.finish(success = true).forEach { enqueue(it) }
            sentences.close()
            consumer?.join()
            answer.toString()
        } finally {
            sentences.cancel()
            consumer?.cancel()
            onQueue(0, highWater.get())
        }
    }
}
