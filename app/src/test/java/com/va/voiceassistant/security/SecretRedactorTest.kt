package com.va.voiceassistant.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretRedactorTest {
    @Test
    fun `redacts authorization and long keys`() {
        val redacted = SecretRedactor.redact("Authorization: Bearer sk-abcdefghijklmnopqrstuvwxyz")
        assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `removes private query and user info from url`() {
        assertEquals(
            "https://private.example/v1",
            SecretRedactor.maskUrl("https://user:pass@private.example/v1?token=secret#part"),
        )
    }
}
