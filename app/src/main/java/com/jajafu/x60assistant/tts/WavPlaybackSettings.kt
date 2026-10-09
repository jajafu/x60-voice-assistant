package com.jajafu.x60assistant.tts

/**
 * 雲端 WAV 播放前的可測試決策。
 *
 * 從 [CloudTtsEngine] 抽出，使音量與語速／音調的取捨能在 JVM 測試驗證，
 * 不必啟動真實 `MediaPlayer`。
 */
object WavPlaybackSettings {
    /**
     * 播放音量。
     *
     * PCM 增益已套用時音量維持 1.0，避免二次放大。只有非 16-bit PCM（增益無法套用）
     * 才退回使用者設定值，此時 `MediaPlayer` 的音量上限 1.0 會讓 1.0 以上的設定不生效，
     * 屬已知格式限制。
     */
    fun volume(ttsVolume: Float, gainApplied: Boolean): Float =
        if (gainApplied) 1.0f else ttsVolume.coerceIn(0.0f, 1.0f)

    /**
     * 語速與音調。
     *
     * 回傳 `null` 代表不套用任何播放參數，維持解碼器原速原調；`MediaPlayer` 對部分
     * 格式不支援 `setPlaybackParams`，此時應略過而不是讓整句失敗。
     */
    fun playbackParamsOrNull(speechRate: Float, pitch: Float): FloatArray? {
        if (speechRate == 1.0f && pitch == 1.0f) return null
        return floatArrayOf(speechRate, pitch)
    }
}
