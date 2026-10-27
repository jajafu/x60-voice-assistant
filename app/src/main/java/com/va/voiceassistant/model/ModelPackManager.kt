package com.va.voiceassistant.model

import android.content.Context
import com.va.voiceassistant.vad.SherpaVadEngine

data class ModelPackStatus(
    val id: String,
    val installed: Boolean,
    val valid: Boolean,
    val reason: String? = null,
    val bytes: Long = 0,
)

class ModelPackManager(private val context: Context) {
    fun verifyVadAsset(): ModelPackStatus {
        val actual = runCatching {
            Sha256.digest(context.assets.open(SherpaVadEngine.MODEL_ASSET_PATH))
        }.getOrElse {
            return ModelPackStatus("silero-vad", installed = false, valid = false, reason = "missing")
        }
        return ModelPackStatus(
            id = "silero-vad",
            installed = true,
            valid = actual == SherpaVadEngine.MODEL_SHA256,
            reason = if (actual == SherpaVadEngine.MODEL_SHA256) null else "sha256",
            bytes = context.assets.openFd(SherpaVadEngine.MODEL_ASSET_PATH).length,
        )
    }
}
