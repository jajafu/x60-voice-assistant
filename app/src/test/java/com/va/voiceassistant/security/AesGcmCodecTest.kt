package com.va.voiceassistant.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class AesGcmCodecTest {
    @Test
    fun `encrypt decrypt round trip does not expose plaintext`() {
        val codec = AesGcmCodec()
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val plaintext = "sk-private-example-1234".toByteArray()
        val aad = "test".toByteArray()

        val encrypted = codec.encrypt(key, plaintext, aad)

        assertFalse(encrypted.ciphertext.toList().containsAll(plaintext.toList()))
        assertArrayEquals(plaintext, codec.decrypt(key, encrypted, aad))
    }
}
