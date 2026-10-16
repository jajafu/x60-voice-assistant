package com.va.voiceassistant.model

import android.content.Context
import com.va.voiceassistant.asr.AsrModelPack
import com.va.voiceassistant.asr.AsrModelPacks
import com.va.voiceassistant.vad.SherpaVadEngine
import java.io.File
import java.io.IOException

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

    fun deleteAsrPack(pack: AsrModelPack) = deleteAsrPackFiles(context.filesDir, pack)
}

internal fun deleteAsrPackFiles(filesDir: File, pack: AsrModelPack) {
    val root = File(filesDir, "model-packs")
    val files = listOf(
        File(root, "asr/${pack.id}"),
        File(root, "asr/${pack.id}.staging"),
        File(root, "asr/${pack.id}.backup"),
        File(root, "downloads/${pack.archiveName}.part"),
        File(root, "downloads/${pack.archiveName}"),
    )
    files.forEach { file ->
        if (file.exists() && !file.deleteRecursively()) {
            throw IOException("無法刪除模型檔案：${file.name}")
        }
    }
}
