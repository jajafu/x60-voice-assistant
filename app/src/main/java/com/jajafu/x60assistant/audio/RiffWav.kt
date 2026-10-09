package com.jajafu.x60assistant.audio

import java.util.Locale

/**
 * 最小 RIFF/WAVE 檔頭判斷。
 *
 * 雲端 TTS 以 `response_format=wav` 向 Provider 要求 WAV。若 Provider 改回傳 MP3、
 * 錯誤 JSON 或其他容器，Android `MediaPlayer` 只會給出無法解讀的 `1/-2147483648`，
 * 使用者與維護者都看不出實際收到什麼。送出播放前先驗證檔頭，才能回報明確原因。
 */
object RiffWav {
    private const val HEADER_SIZE = 12

    /** 只檢查 `RIFF`／`WAVE` 開頭，不解析 `fmt ` 與 `data` chunk。 */
    fun isWave(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_SIZE && matches(bytes, 0, "RIFF") && matches(bytes, 8, "WAVE")

    /**
     * 只取檔頭前 [limit] bytes 的十六進位，供錯誤訊息描述實際收到的內容。
     * 不含音訊本體，也不得用於記錄實際音訊。
     */
    fun signature(bytes: ByteArray, limit: Int = HEADER_SIZE): String =
        bytes.take(limit.coerceIn(0, bytes.size)).joinToString(separator = " ") {
            String.format(Locale.ROOT, "%02X", it.toInt() and 0xff)
        }

    private fun matches(bytes: ByteArray, offset: Int, value: String): Boolean =
        offset + value.length <= bytes.size &&
            value.indices.all { bytes[offset + it].toInt() == value[it].code }
}
