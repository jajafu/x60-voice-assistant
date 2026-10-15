package com.va.voiceassistant.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AndroidKeystoreSecretStore(
    context: Context,
    private val scope: Scope = Scope.LLM,
    private val codec: AesGcmCodec = AesGcmCodec(),
) : SecretStore {
    private val secretDirectory = File(context.filesDir, "secrets")
    private val secretFile = File(secretDirectory, scope.fileName)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    override fun saveApiKey(value: CharArray) {
        require(value.isNotEmpty())
        val plaintext = value.concatToString().toByteArray(Charsets.UTF_8)
        try {
            val encrypted = codec.encrypt(getOrCreateKey(), plaintext, scope.associatedData)
            val document = SecretDocument(
                version = FORMAT_VERSION,
                keyAlias = scope.keyAlias,
                iv = Base64.encodeToString(encrypted.iv, Base64.NO_WRAP),
                ciphertext = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP),
            )
            secretDirectory.mkdirs()
            val temporary = File(secretDirectory, scope.tempFileName)
            temporary.writeText(json.encodeToString(document), Charsets.UTF_8)
            check(temporary.renameTo(secretFile) || temporary.copyTo(secretFile, overwrite = true).also { temporary.delete() }.exists())
        } finally {
            plaintext.fill(0)
            value.fill('\u0000')
        }
    }

    override fun readApiKey(): CharArray? {
        if (!secretFile.isFile) return null
        val document = json.decodeFromString<SecretDocument>(secretFile.readText(Charsets.UTF_8))
        require(document.version == FORMAT_VERSION && document.keyAlias == scope.keyAlias)
        val plaintext = codec.decrypt(
            getOrCreateKey(),
            EncryptedPayload(
                Base64.decode(document.iv, Base64.NO_WRAP),
                Base64.decode(document.ciphertext, Base64.NO_WRAP),
            ),
            scope.associatedData,
        )
        return try {
            plaintext.toString(Charsets.UTF_8).toCharArray()
        } finally {
            plaintext.fill(0)
        }
    }

    override fun deleteApiKey() {
        if (secretFile.exists()) secretFile.delete()
    }

    override fun maskedApiKey(): String {
        val key = runCatching { readApiKey() }.getOrNull() ?: return "未設定"
        return try {
            val suffix = key.takeLast(4).joinToString("")
            "••••$suffix"
        } finally {
            key.fill('\u0000')
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(scope.keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    scope.keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    @Serializable
    private data class SecretDocument(
        val version: Int,
        val keyAlias: String,
        val iv: String,
        val ciphertext: String,
    )

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val FORMAT_VERSION = 1
    }

    enum class Scope(
        val fileName: String,
        val tempFileName: String,
        val keyAlias: String,
        val associatedData: ByteArray,
    ) {
        // These values are scoped to the renamed app (com.va.voiceassistant).
        // Changing applicationId installs as a new app, so keys stored under the
        // old app are not carried over and must be re-entered.
        LLM(
            fileName = "llm_api_key.v1.json",
            tempFileName = "llm_api_key.tmp",
            keyAlias = "voiceassistant-llm-api-key-v1",
            associatedData = "com.va.voiceassistant:llm-api-key:v1".toByteArray(),
        ),
        AUDIO(
            fileName = "audio_api_key.v1.json",
            tempFileName = "audio_api_key.tmp",
            keyAlias = "voiceassistant-audio-api-key-v1",
            associatedData = "com.va.voiceassistant:audio-api-key:v1".toByteArray(),
        ),
    }
}
