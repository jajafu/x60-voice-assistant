package com.jajafu.x60assistant.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseCompilerTest {
    private val vocabulary = setOf("x", "iǎo", "zh", "ù", "sh", "ǒu", "n", "ǐ", "h", "ǎo")

    @Test
    fun `tokenizes tonal pinyin into model tokens`() {
        assertEquals(
            listOf("x", "iǎo", "zh", "ù", "sh", "ǒu"),
            WakePhraseCompiler.tokenizeRomanization("xiǎo zhù shǒu", vocabulary),
        )
    }

    @Test
    fun `rejects pronunciation missing from model vocabulary`() {
        assertNull(WakePhraseCompiler.tokenizeRomanization("wǒ shì ai", vocabulary))
    }
}
