package com.jajafu.x60assistant.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUrlTest {
    @Test
    fun `permits IPv4 loopback only when debug HTTP is enabled`() {
        assertEquals(
            "http://127.0.0.1:8080/v1/chat/completions",
            ProviderUrl.normalize("http://127.0.0.1:8080/v1", "/chat/completions", allowHttp = true),
        )
    }

    @Test
    fun `normalizes v1 and endpoint without duplication`() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            ProviderUrl.normalize("https://example.com/v1/", "/v1/chat/completions"),
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            ProviderUrl.normalize("https://example.com/v1/chat/completions", "/chat/completions"),
        )
    }

    @Test
    fun `rejects unsafe schemes and unapproved http hosts`() {
        assertTrue(runCatching { ProviderUrl.normalize("file:///secret", "/chat/completions") }.isFailure)
        assertTrue(runCatching { ProviderUrl.normalize("http://example.com", "/chat/completions", allowHttp = true) }.isFailure)
    }
}
