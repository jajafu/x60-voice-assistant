package com.jajafu.x60assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSegmenterTest {
    @Test
    fun `long buffered sentence is split within speech engine limits`() {
        val text = "長".repeat(5_000) + "。"
        val segmenter = SpeechSegmenter()
        val segments = segmenter.accept(text) + segmenter.finish(success = true)
        assertEquals(text, segments.joinToString(""))
        assertTrue(segments.all { it.length <= 96 })
    }

    @Test
    fun `keeps token chunks until sentences are complete and preserves order`() {
        val segmenter = SpeechSegmenter()
        assertTrue(segmenter.accept("第一句還").isEmpty())
        assertEquals(listOf("第一句還沒完。", "第二句！"), segmenter.accept("沒完。第二句！"))
    }

    @Test
    fun `does not flush partial text after network failure`() {
        val segmenter = SpeechSegmenter()
        segmenter.accept("不完整片段")
        assertTrue(segmenter.finish(success = false).isEmpty())
    }

    @Test
    fun `flushes final text after done`() {
        val segmenter = SpeechSegmenter()
        segmenter.accept("最後沒有標點")
        assertEquals(listOf("最後沒有標點"), segmenter.finish(success = true))
    }
}
