package com.va.voiceassistant.schedule

import java.time.Instant
import java.time.ZoneId

/**
 * 每日定時啟停的純邏輯：有效性判斷與「下一次觸發時間」計算（可單元測試）。
 *
 * 有效性（使用者 2026-11-03 決議）：總開關開啟、兩個時間都在 0–1439、
 * 且啟動與停止**不同**時間；設同一時間視為無效，不排程。
 * 啟動與停止是各自獨立的每日鬧鐘，不互相配對，停止時間早於啟動時間
 * （如 22:00 啟動、06:00 停止）也合法。
 */
object VoiceSchedulePlanner {

    const val MINUTES_PER_DAY = 24 * 60

    fun isValid(
        enabled: Boolean,
        startMinutes: Int,
        stopMinutes: Int,
    ): Boolean = enabled &&
        startMinutes in 0 until MINUTES_PER_DAY &&
        stopMinutes in 0 until MINUTES_PER_DAY &&
        startMinutes != stopMinutes

    /**
     * [minutesOfDay] 在 [nowEpochMillis] 之後的下一次觸發時間（含今天）；
     * 當日該時間已過（含等於現在）就排明天。時區以 [zone] 為準，跨午夜與夏令時
     * 都用 ZonedDateTime 計算，不自行加減 24 小時毫秒數。
     */
    fun nextOccurrence(
        minutesOfDay: Int,
        nowEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
        var candidate = now.toLocalDate()
            .atStartOfDay(zone)
            .plusMinutes(minutesOfDay.toLong())
        if (!candidate.toInstant().isAfter(now.toInstant())) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }
}
