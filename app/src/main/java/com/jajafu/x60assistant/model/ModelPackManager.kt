package com.jajafu.x60assistant.model

import android.content.Context
import com.jajafu.x60assistant.asr.AsrModelPack
import com.jajafu.x60assistant.asr.AsrModelPacks
import com.jajafu.x60assistant.vad.SherpaVadEngine
import java.io.File

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

    fun asrDirectory(pack: AsrModelPack): File =
        File(context.filesDir, "model-packs/asr/${pack.id}")

    fun verifyAsrPack(pack: AsrModelPack): ModelPackStatus {
        val directory = asrDirectory(pack)
        if (!directory.isDirectory) {
            return ModelPackStatus(pack.id, installed = false, valid = false, reason = "missing-directory")
        }
        var bytes = 0L
        for ((name, expectedHash) in pack.files) {
            val file = File(directory, name)
            if (!file.isFile) {
                return ModelPackStatus(pack.id, installed = false, valid = false, reason = "missing:$name")
            }
            bytes += file.length()
            val actual = runCatching { Sha256.digest(file.inputStream()) }.getOrElse {
                return ModelPackStatus(pack.id, installed = true, valid = false, reason = "read:$name")
            }
            if (!actual.equals(expectedHash, ignoreCase = true)) {
                return ModelPackStatus(pack.id, installed = true, valid = false, reason = "sha256:$name", bytes = bytes)
            }
        }
        return ModelPackStatus(pack.id, installed = true, valid = true, bytes = bytes)
    }

    fun allAsrStatuses(): List<ModelPackStatus> = AsrModelPacks.all.map(::verifyAsrPack)
}
