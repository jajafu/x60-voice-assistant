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
            assertTrue(pack.files.isNotEmpty())
            assertTrue(pack.files.values.all { it.length == 64 })
            assertTrue(pack.license.isNotBlank())
        }
    }

    @Test
    fun `legacy packs stay on github while xasr uses drive`() {
        listOf(AsrModelPacks.standard, AsrModelPacks.moonshine).forEach { pack ->
            assertTrue(pack.sourceUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/"))
        }
        assertTrue(AsrModelPacks.xAsr.sourceUrl.startsWith("https://drive.usercontent.google.com/download?id="))
        assertTrue(AsrModelPacks.xAsr.sourceUrl.contains(AsrModelPacks.X_ASR_DRIVE_FILE_ID))
    }
}
