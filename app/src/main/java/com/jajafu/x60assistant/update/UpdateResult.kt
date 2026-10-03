package com.jajafu.x60assistant.update

import java.io.File

enum class UpdateFailure {
    NETWORK,
    INVALID_RESPONSE,
    DOWNLOAD_FAILED,
    VERIFY_FAILED,
}

class UpdateException(
    val failure: UpdateFailure,
    message: String,
) : Exception(message)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult

    data class Available(val info: UpdateInfo) : UpdateCheckResult

    data class Failed(val failure: UpdateFailure, val detail: String = "") : UpdateCheckResult
}

sealed interface UpdateDownload {
    data class Progress(val bytesRead: Long, val totalBytes: Long) : UpdateDownload

    data class Done(val apkFile: File) : UpdateDownload

    data class Failed(val failure: UpdateFailure, val detail: String = "") : UpdateDownload
}

fun describeUpdateFailure(failure: UpdateFailure, detail: String): String {
    val cause = when (failure) {
        UpdateFailure.NETWORK -> "無法連線到更新伺服器"
        UpdateFailure.INVALID_RESPONSE -> "更新資訊無效"
        UpdateFailure.DOWNLOAD_FAILED -> "下載失敗"
        UpdateFailure.VERIFY_FAILED -> "SHA-256 驗證失敗，安裝檔已刪除"
    }
    return if (detail.isBlank()) cause else "$cause（$detail）"
}
