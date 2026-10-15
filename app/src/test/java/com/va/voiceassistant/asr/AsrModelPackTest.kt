package com.va.voiceassistant.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModelPackTest {
    @Test
    fun `official candidates have pinned archives and files`() {
        assertEquals(3, AsrModelPacks.all.size)
        AsrModelPacks.all.forEach { pack ->
            assertEquals(64, pack.archiveSha256.length)
            assertTrue(pack.sourceUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/"))
            assertTrue(pack.files.isNotEmpty())
            assertTrue(pack.files.values.all { it.length == 64 })
            assertTrue(pack.license.isNotBlank())
        }
    }
}
