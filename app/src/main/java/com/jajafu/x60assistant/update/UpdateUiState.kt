package com.jajafu.x60assistant.update

/** 診斷頁「App 版本與更新」區塊的畫面狀態，只存記憶體不落地。 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class UpToDate(val checkedVersionCode: Int) : UpdateUiState

    data class Available(val info: UpdateInfo) : UpdateUiState

    data class Downloading(val bytesRead: Long, val totalBytes: Long) : UpdateUiState

    data class ReadyToInstall(val versionName: String, val needPermissionHint: Boolean = false) : UpdateUiState

    data class Failed(val message: String) : UpdateUiState
}

fun formatUpdateProgress(bytesRead: Long, totalBytes: Long): String {
    if (totalBytes <= 0) return "已下載 ${bytesRead / 1024} KB"
    val percent = (bytesRead * 100 / totalBytes).coerceIn(0, 100)
    return "下載中 $percent%（${bytesRead / 1024} / ${totalBytes / 1024} KB）"
}
