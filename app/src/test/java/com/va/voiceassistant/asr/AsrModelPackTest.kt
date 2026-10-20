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
    fun `all packs stay on the official sherpa-onnx release`() {
        AsrModelPacks.all.forEach { pack ->
            assertTrue(pack.sourceUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"))
            // 鎖定檔名，避免誤指到同一個 release 下的其他檔案；大小與 SHA256 由安裝流程校驗。
            assertTrue(pack.sourceUrl.endsWith("/${pack.archiveName}"))
        }
    }
}
