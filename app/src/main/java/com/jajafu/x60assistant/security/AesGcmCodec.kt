package com.jajafu.x60assistant.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray)

class AesGcmCodec(private val secureRandom: SecureRandom = SecureRandom()) {
    fun encrypt(key: SecretKey, plaintext: ByteArray, associatedData: ByteArray): EncryptedPayload {
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return EncryptedPayload(iv, cipher.doFinal(plaintext))
    }

    fun decrypt(key: SecretKey, payload: EncryptedPayload, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, payload.iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(payload.ciphertext)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
