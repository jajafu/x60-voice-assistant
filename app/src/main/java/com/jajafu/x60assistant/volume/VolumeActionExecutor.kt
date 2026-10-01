package com.jajafu.x60assistant.volume

/** 音量指令的可驗證結果；[applied] 為 false 代表沒有實際改動音量。 */
data class VolumeActionResult(
    val message: String,
    val applied: Boolean,
)

/**
 * 執行本機音量指令的介面。
 *
 * 與 [com.jajafu.x60assistant.music.MusicActionExecutor] 同理，抽成介面是為了讓
 * `VoiceOrchestrator` 的路由可以在 JVM 測試中替換，不必依賴 Android 的 AudioManager。
 */
interface VolumeActionExecutor {
    suspend fun execute(command: VolumeCommand): VolumeActionResult
}