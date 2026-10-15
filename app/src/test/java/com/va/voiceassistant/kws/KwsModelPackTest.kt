package com.va.voiceassistant.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KwsModelPackTest {
    @Test
    fun `model pack is pinned to expected official archive`() {
        assertEquals(
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20",
            KwsModelPack.ID,
        )
        assertEquals(64, KwsModelPack.ARCHIVE_SHA256.length)
        assertTrue(KwsModelPack.expectedFileHashes.values.all { it.length == 64 })
    }
}
