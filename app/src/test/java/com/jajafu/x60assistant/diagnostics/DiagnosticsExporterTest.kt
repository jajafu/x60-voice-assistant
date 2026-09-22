package com.jajafu.x60assistant.diagnostics

import com.jajafu.x60assistant.data.AssistantSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsExporterTest {
    @Test
    fun `omits transcripts keys and private URL path`() {
        val output = DiagnosticsExporter.build(
            M1DiagnosticsSnapshot(finalTranscript = "私人問題", transcriptLength = 4, transcriptHash = "abc"),
            DeviceDiagnostics("13", 33, "build", "vivo", "arm64", "8", "4", emptyList(), true, true, true, 80, 30.0, 0),
            AssistantSettings(baseUrl = "https://api.example.com/private/account?token=secret"),
            emptyList(),
        )
        assertTrue(output.contains("provider_host=https://api.example.com"))
        assertFalse(output.contains("私人問題"))
        assertFalse(output.contains("token=secret"))
    }
}
