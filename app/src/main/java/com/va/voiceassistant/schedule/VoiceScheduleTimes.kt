package com.va.voiceassistant.schedule

/**
 * HH:MM 與「一天中的分鐘數」互轉（純邏輯，供設定 UI 與排程計算共用）。
 *
 * 解析刻意寬鬆：`06:30` 與 `0630` 都收——電視遙控器輸入冒號很費力，
 * 數字鍵盤連打四位數即可。只收 3–4 位數字；小時 0–23、分鐘 0–59，其餘回 null。
 */
object VoiceScheduleTimes {

    fun parse(value: String): Int? {
        val digits = value.filter { it.isDigit() }
        val (hourText, minuteText) = when (digits.length) {
            3 -> digits.take(1) to digits.drop(1)
            4 -> digits.take(2) to digits.drop(2)
            else -> return null
        }
        val hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
