package com.va.voiceassistant.kws

import android.content.res.AssetManager
import java.security.MessageDigest

object KwsModelPack {
    const val ID = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
    const val ARCHIVE_SHA256 = "68447f4fbc67e70eee3a93961f36e81e98f47aef73ce7e7ca00885c6cd3616a6"
    const val ASSET_DIR = "models/kws/$ID"
    const val ENCODER = "$ASSET_DIR/encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx"
    const val DECODER = "$ASSET_DIR/decoder-epoch-13-avg-2-chunk-16-left-64.onnx"
    const val JOINER = "$ASSET_DIR/joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx"
    const val TOKENS = "$ASSET_DIR/tokens.txt"
    const val KEYWORDS = "$ASSET_DIR/keywords.txt"
    const val KEYWORD = "小助手"
    const val DEFAULT_KEYWORD_SPEC = "x iǎo zh ù sh ǒu @小助手"
    const val KEYWORDS_SCORE = 1.5f
    const val KEYWORDS_THRESHOLD = 0.25f
    const val COOLDOWN_MS = 2_000L

    val expectedFileHashes = linkedMapOf(
        ENCODER to "408bbd740838c42d5bf6d1c5b80b3c88b616c7860b92d980328b5b068c76ae48",
        DECODER to "63a22dd60f40fff082ac3e09afa507f6787da36df76ded2fbe145fa233e22c21",
        JOINER to "190d4067b4cc20b72a42a1916e69d92052000fb7051a427ebb1bc72a69207dc1",
        TOKENS to "2d3f32311f9b692b964da3c90e830258d3e78e013cb0c992dbfb15cd5a1a71b0",
    )

    fun verify(assetManager: AssetManager): ModelVerificationResult {
        for ((path, expected) in expectedFileHashes) {
            val actual = runCatching { sha256(assetManager, path) }.getOrElse {
                return ModelVerificationResult(false, "missing:$path")
            }
            if (!actual.equals(expected, ignoreCase = true)) {
                return ModelVerificationResult(false, "sha256:$path")
            }
        }

        val keywordLine = runCatching {
            assetManager.open(KEYWORDS).bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
        }.getOrElse {
            return ModelVerificationResult(false, "missing:$KEYWORDS")
        }
        if (keywordLine != DEFAULT_KEYWORD_SPEC) {
            return ModelVerificationResult(false, "keywords-format")
        }
        return ModelVerificationResult(true, null)
    }

    private fun sha256(assetManager: AssetManager, path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        assetManager.open(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class ModelVerificationResult(val valid: Boolean, val reason: String?)
