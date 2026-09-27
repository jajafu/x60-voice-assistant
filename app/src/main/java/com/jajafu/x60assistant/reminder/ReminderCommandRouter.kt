package com.jajafu.x60assistant.reminder

import java.time.Duration
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class ReminderRequest(
    val message: String,
    val triggerAtEpochMillis: Long,
    val confirmationTime: String,
    val recurrence: ReminderRecurrence? = null,
)

sealed interface ReminderCommand {
    data class Schedule(val request: ReminderRequest) : ReminderCommand
    data class Invalid(val response: String) : ReminderCommand
}

/** Parses a deliberately small, deterministic set of Traditional-Chinese reminder phrases. */
object ReminderCommandRouter {
    private val daily = Regex(
        "^(?:(?:請|麻煩)?(?:你)?(?:在)?)?每天(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:的時候)?(?:提醒我|叫我|提醒)(.+)$",
    )
    private val reminderFirstDaily = Regex(
        "^(?:請|麻煩)?(?:提醒我|叫我)(?:在)?每天(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:要)?(.+)$",
    )
    private val weekly = Regex(
        "^(?:(?:請|麻煩)?(?:你)?(?:在)?)?每(?:個)?(?:星期|禮拜|週|周)((?:星期|禮拜|週|周)?[一二三四五六日天1-7](?:(?:、|和|及|與)?(?:星期|禮拜|週|周)?[一二三四五六日天1-7])*)(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:的時候)?(?:提醒我|叫我|提醒)(.+)$",
    )
    private val reminderFirstWeekly = Regex(
        "^(?:請|麻煩)?(?:提醒我|叫我)(?:在)?每(?:個)?(?:星期|禮拜|週|周)((?:星期|禮拜|週|周)?[一二三四五六日天1-7](?:(?:、|和|及|與)?(?:星期|禮拜|週|周)?[一二三四五六日天1-7])*)(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:要)?(.+)$",
    )
    private val compoundRelative = Regex(
        "^(?:(?:請|麻煩)?(?:你)?(?:在)?)?([0-9零〇一二兩三四五六七八九十百]+)(?:個)?(?:小時|鐘頭)(?:又)?([0-9零〇一二兩三四五六七八九十百]+)(?:分鐘?|分)後(?:再)?(?:提醒我|叫我|提醒)(.+)$",
    )
    private val compoundRelativeReminderFirst = Regex(
        "^(?:請|麻煩)?(?:提醒我|叫我)([0-9零〇一二兩三四五六七八九十百]+)(?:個)?(?:小時|鐘頭)(?:又)?([0-9零〇一二兩三四五六七八九十百]+)(?:分鐘?|分)後(.+)$",
    )
    private val relative = Regex(
        "^(?:(?:請|麻煩)?(?:你)?(?:在)?)?(半|[0-9零〇一二兩三四五六七八九十百]+)(?:個)?(秒鐘?|分鐘?|分|小時|鐘頭)後(?:再)?(?:提醒我|叫我|提醒)(.+)$",
    )
    private val relativeReminderFirst = Regex(
        "^(?:請|麻煩)?(?:提醒我|叫我)(半|[0-9零〇一二兩三四五六七八九十百]+)(?:個)?(秒鐘?|分鐘?|分|小時|鐘頭)後(.+)$",
    )
    private val absolute = Regex(
        "^(?:(?:請|麻煩)?(?:你)?(?:在)?)?(今天|明天|後天)?(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:的時候)?(?:提醒我|叫我|提醒)(.+)$",
    )
    private val reminderFirstAbsolute = Regex(
        "^(?:請|麻煩)?(?:提醒我|叫我)(今天|明天|後天)?(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:要)?(.+)$",
    )
    private val weekdayAbsolute = Regex(
        "^(?:(?:請|麻煩)?(?:你)?(?:在)?)?(?:星期|禮拜|週)([一二三四五六日天1-7])(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:的時候)?(?:提醒我|叫我|提醒)(.+)$",
    )
    private val reminderFirstWeekdayAbsolute = Regex(
        "^(?:請|麻煩)?(?:提醒我|叫我)(?:在)?(?:星期|禮拜|週)([一二三四五六日天1-7])(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:要)?(.+)$",
    )
    private val monthDayAbsolute = Regex(
        "^(?:(?:請|麻煩)?(?:你)?(?:在)?)?([0-9零〇一二兩三四五六七八九十]{1,3})月([0-9零〇一二兩三四五六七八九十]{1,3})(?:日|號)?(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:的時候)?(?:提醒我|叫我|提醒)(.+)$",
    )
    private val reminderFirstMonthDayAbsolute = Regex(
        "^(?:請|麻煩)?(?:提醒我|叫我)(?:在)?([0-9零〇一二兩三四五六七八九十]{1,3})月([0-9零〇一二兩三四五六七八九十]{1,3})(?:日|號)?(凌晨|早上|上午|中午|下午|晚上)?([0-9零〇一二兩三四五六七八九十兩]{1,3})(?:[:：點時]([0-9零〇一二兩三四五六七八九十兩半]{1,3})?分?)?(?:要)?(.+)$",
    )

    fun parse(transcript: String, now: ZonedDateTime = ZonedDateTime.now()): ReminderCommand? {
        val text = transcript.trim()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[，,。！？!?]"), "")
            .replace("鬧鐘", "提醒")
            .replace("小時候", "小時後")
            .replace("鐘頭候", "鐘頭後")
            .replace("分鐘候", "分鐘後")
            .replace("分候", "分後")
            .replace("點鐘", "點")
            .replace("時鐘", "時")
            .replace("點整", "點")
            .replace("時整", "時")
        parseRecurring(text, now)?.let { return it }
        parseRelative(text, now)?.let { return it }
        parseWeekdayAbsolute(text, now)?.let { return it }
        parseMonthDayAbsolute(text, now)?.let { return it }
        return parseAbsolute(text, now)
    }

    private fun parseRecurring(text: String, now: ZonedDateTime): ReminderCommand? {
        val dailyMatch = daily.matchEntire(text) ?: reminderFirstDaily.matchEntire(text)
        if (dailyMatch != null) {
            val schedule = recurringSchedule(
                frequency = ReminderFrequency.DAILY,
                period = dailyMatch.groupValues[1],
                hourText = dailyMatch.groupValues[2],
                minuteText = dailyMatch.groupValues[3],
                now = now,
            ) ?: return ReminderCommand.Invalid("我沒有聽懂每天提醒的時間，請再說一次。")
            val message = cleanMessage(dailyMatch.groupValues[4])
            if (message.isBlank()) return ReminderCommand.Invalid("請告訴我要提醒你做什麼。")
            return ReminderCommand.Schedule(
                ReminderRequest(
                    message = message,
                    triggerAtEpochMillis = schedule.triggerAtEpochMillis,
                    confirmationTime = "每天 ${formatClock(schedule.recurrence.hour, schedule.recurrence.minute)}",
                    recurrence = schedule.recurrence,
                ),
            )
        }

        val weeklyMatch = weekly.matchEntire(text) ?: reminderFirstWeekly.matchEntire(text) ?: return null
        val weekdays = parseDaysOfWeek(weeklyMatch.groupValues[1])
        if (weekdays.isEmpty()) return ReminderCommand.Invalid("我沒有聽懂每週要在星期幾提醒。")
        val schedule = recurringSchedule(
            frequency = ReminderFrequency.WEEKLY,
            daysOfWeek = weekdays,
            period = weeklyMatch.groupValues[2],
            hourText = weeklyMatch.groupValues[3],
            minuteText = weeklyMatch.groupValues[4],
            now = now,
        ) ?: return ReminderCommand.Invalid("我沒有聽懂每週提醒的時間，請再說一次。")
        val message = cleanMessage(weeklyMatch.groupValues[5])
        if (message.isBlank()) return ReminderCommand.Invalid("請告訴我要提醒你做什麼。")
        return ReminderCommand.Schedule(
            ReminderRequest(
                message = message,
                triggerAtEpochMillis = schedule.triggerAtEpochMillis,
                confirmationTime = "${schedule.recurrence.label()} " +
                    formatClock(schedule.recurrence.hour, schedule.recurrence.minute),
                recurrence = schedule.recurrence,
            ),
        )
    }

    private data class RecurringSchedule(
        val recurrence: ReminderRecurrence,
        val triggerAtEpochMillis: Long,
    )

    private fun recurringSchedule(
        frequency: ReminderFrequency,
        daysOfWeek: List<Int> = emptyList(),
        period: String,
        hourText: String,
        minuteText: String,
        now: ZonedDateTime,
    ): RecurringSchedule? {
        val clock = parseClock(period, hourText, minuteText) ?: return null
        val rawHour = parseNumber(hourText) ?: return null
        val candidateHours = when {
            period.isNotBlank() -> listOf(clock.first)
            rawHour in 1..11 -> listOf(rawHour, rawHour + 12)
            rawHour == 12 -> listOf(0, 12)
            else -> listOf(clock.first)
        }
        return candidateHours.map { hour ->
            val recurrence = ReminderRecurrence(
                frequency = frequency,
                daysOfWeek = daysOfWeek,
                hour = hour,
                minute = clock.second,
            )
            RecurringSchedule(
                recurrence = recurrence,
                triggerAtEpochMillis = ReminderRecurrenceCalculator.nextTriggerAtEpochMillis(
                    recurrence,
                    now.toInstant().toEpochMilli(),
                    now.zone,
                ),
            )
        }.minByOrNull(RecurringSchedule::triggerAtEpochMillis)
    }

    private fun parseWeekdayAbsolute(text: String, now: ZonedDateTime): ReminderCommand? {
        val match = weekdayAbsolute.matchEntire(text)
            ?: reminderFirstWeekdayAbsolute.matchEntire(text)
            ?: return null
        val dayOfWeek = parseDayOfWeek(match.groupValues[1])
            ?: return ReminderCommand.Invalid("我沒有聽懂星期幾，請再說一次。")
        val clock = parseClock(match.groupValues[2], match.groupValues[3], match.groupValues[4])
            ?: return ReminderCommand.Invalid("我沒有聽懂提醒時間，請再說一次。")
        val message = cleanMessage(match.groupValues[5])
        if (message.isBlank()) return ReminderCommand.Invalid("請告訴我要提醒你做什麼。")

        val daysAhead = (dayOfWeek.value - now.dayOfWeek.value + 7) % 7
        var targetDate = now.toLocalDate().plusDays(daysAhead.toLong())
        var trigger = targetDate.atTime(clock.first, clock.second).atZone(now.zone)
        if (trigger <= now) {
            targetDate = targetDate.plusWeeks(1)
            trigger = targetDate.atTime(clock.first, clock.second).atZone(now.zone)
        }
        return ReminderCommand.Schedule(
            ReminderRequest(
                message = message,
                triggerAtEpochMillis = trigger.toInstant().toEpochMilli(),
                confirmationTime = "${weekdayLabel(dayOfWeek)} ${formatClock(trigger)}",
            ),
        )
    }

    private fun parseMonthDayAbsolute(text: String, now: ZonedDateTime): ReminderCommand? {
        val match = monthDayAbsolute.matchEntire(text)
            ?: reminderFirstMonthDayAbsolute.matchEntire(text)
            ?: return null
        val month = parseNumber(match.groupValues[1])
            ?: return ReminderCommand.Invalid("我沒有聽懂月份，請再說一次。")
        val day = parseNumber(match.groupValues[2])
            ?: return ReminderCommand.Invalid("我沒有聽懂日期，請再說一次。")
        val clock = parseClock(match.groupValues[3], match.groupValues[4], match.groupValues[5])
            ?: return ReminderCommand.Invalid("我沒有聽懂提醒時間，請再說一次。")
        val message = cleanMessage(match.groupValues[6])
        if (message.isBlank()) return ReminderCommand.Invalid("請告訴我要提醒你做什麼。")
        if (month !in 1..12 || day !in 1..31) {
            return ReminderCommand.Invalid("日期不正確，請再說一次。")
        }

        val trigger = nextMonthDay(month, day, clock.first, clock.second, now)
            ?: return ReminderCommand.Invalid("日期不正確，請再說一次。")
        return ReminderCommand.Schedule(
            ReminderRequest(
                message = message,
                triggerAtEpochMillis = trigger.toInstant().toEpochMilli(),
                confirmationTime = "${trigger.monthValue}月${trigger.dayOfMonth}日 ${formatClock(trigger)}",
            ),
        )
    }

    private fun parseRelative(text: String, now: ZonedDateTime): ReminderCommand? {
        parseCompoundRelative(text, now)?.let { return it }
        val match = relative.matchEntire(text) ?: relativeReminderFirst.matchEntire(text) ?: return null
        val amountText = match.groupValues[1]
        val unit = match.groupValues[2]
        val message = cleanMessage(match.groupValues[3])
        if (message.isBlank()) return ReminderCommand.Invalid("請告訴我要提醒你做什麼。")

        val duration = when {
            amountText == "半" && unit in setOf("小時", "鐘頭") -> Duration.ofMinutes(30)
            amountText == "半" -> return ReminderCommand.Invalid("我只支援半小時，請換個說法。")
            else -> {
                val amount = parseNumber(amountText)
                    ?: return ReminderCommand.Invalid("我沒有聽懂提醒時間，請再說一次。")
                when (unit) {
                    "秒", "秒鐘" -> Duration.ofSeconds(amount.toLong())
                    "分", "分鐘" -> Duration.ofMinutes(amount.toLong())
                    else -> Duration.ofHours(amount.toLong())
                }
            }
        }
        if (duration.isZero || duration.isNegative) {
            return ReminderCommand.Invalid("提醒時間必須晚於現在。")
        }
        if (duration > Duration.ofDays(365)) {
            return ReminderCommand.Invalid("目前只支援一年內的提醒。")
        }
        val trigger = now.plus(duration)
        val label = when {
            amountText == "半" -> "半小時後"
            unit.startsWith("秒") -> "${duration.seconds}秒後"
            unit in setOf("分", "分鐘") -> "${duration.toMinutes()}分鐘後"
            else -> "${duration.toHours()}小時後"
        }
        return ReminderCommand.Schedule(ReminderRequest(message, trigger.toInstant().toEpochMilli(), label))
    }

    private fun parseCompoundRelative(text: String, now: ZonedDateTime): ReminderCommand? {
        val match = compoundRelative.matchEntire(text)
            ?: compoundRelativeReminderFirst.matchEntire(text)
            ?: return null
        val hours = parseNumber(match.groupValues[1])
            ?: return ReminderCommand.Invalid("我沒有聽懂提醒時間，請再說一次。")
        val minutes = parseNumber(match.groupValues[2])
            ?: return ReminderCommand.Invalid("我沒有聽懂提醒時間，請再說一次。")
        val message = cleanMessage(match.groupValues[3])
        if (message.isBlank()) return ReminderCommand.Invalid("請告訴我要提醒你做什麼。")
        if (minutes !in 0..59) return ReminderCommand.Invalid("複合時間的分鐘必須介於0到59。")
        val duration = Duration.ofHours(hours.toLong()).plusMinutes(minutes.toLong())
        if (duration.isZero || duration.isNegative) {
            return ReminderCommand.Invalid("提醒時間必須晚於現在。")
        }
        if (duration > Duration.ofDays(365)) {
            return ReminderCommand.Invalid("目前只支援一年內的提醒。")
        }
        val trigger = now.plus(duration)
        return ReminderCommand.Schedule(
            ReminderRequest(
                message = message,
                triggerAtEpochMillis = trigger.toInstant().toEpochMilli(),
                confirmationTime = "${hours}小時${minutes}分鐘後",
            ),
        )
    }

    private fun parseAbsolute(text: String, now: ZonedDateTime): ReminderCommand? {
        val match = absolute.matchEntire(text) ?: reminderFirstAbsolute.matchEntire(text) ?: return null
        val dayWord = match.groupValues[1]
        val period = match.groupValues[2]
        var hour = parseNumber(match.groupValues[3])
            ?: return ReminderCommand.Invalid("我沒有聽懂幾點，請再說一次。")
        val minuteText = match.groupValues[4]
        val minute = when {
            minuteText.isBlank() -> 0
            minuteText == "半" -> 30
            else -> parseNumber(minuteText) ?: return ReminderCommand.Invalid("我沒有聽懂幾分，請再說一次。")
        }
        val message = cleanMessage(match.groupValues[5])
        if (message.isBlank()) return ReminderCommand.Invalid("請告訴我要提醒你做什麼。")
        if (minute !in 0..59) return ReminderCommand.Invalid("分鐘必須介於0到59。")

        hour = when (period) {
            "下午", "晚上" -> if (hour in 1..11) hour + 12 else hour
            "中午" -> if (hour in 1..10) hour + 12 else hour
            "凌晨", "早上", "上午" -> if (hour == 12) 0 else hour
            else -> hour
        }
        if (hour !in 0..23) return ReminderCommand.Invalid("小時必須介於0到23。")

        val dayOffset = when (dayWord) {
            "明天" -> 1L
            "後天" -> 2L
            else -> 0L
        }
        val candidateHours = when {
            period.isNotBlank() -> listOf(hour)
            hour in 1..11 -> listOf(hour, hour + 12)
            hour == 12 -> listOf(0, 12)
            else -> listOf(hour)
        }
        val targetDate = now.toLocalDate().plusDays(dayOffset)
        var trigger = candidateHours
            .map { candidateHour -> targetDate.atTime(candidateHour, minute).atZone(now.zone) }
            .filter { it > now }
            .minOrNull()
        if (trigger == null) {
            if (dayWord == "今天") return ReminderCommand.Invalid("今天這個時間已經過了，請指定新的時間。")
            if (dayWord.isBlank()) {
                trigger = targetDate.plusDays(1).atTime(candidateHours.first(), minute).atZone(now.zone)
            } else {
                return ReminderCommand.Invalid("指定的提醒時間已經過了，請指定新的時間。")
            }
        }
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), trigger.toLocalDate())
        val dayLabel = when (days) {
            0L -> "今天"
            1L -> "明天"
            2L -> "後天"
            else -> trigger.toLocalDate().toString()
        }
        val minuteLabel = minute.toString().padStart(2, '0')
        return ReminderCommand.Schedule(
            ReminderRequest(message, trigger.toInstant().toEpochMilli(), "$dayLabel ${trigger.hour}:$minuteLabel"),
        )
    }

    private fun cleanMessage(value: String): String = value.trim().removePrefix("要").trim()

    private fun parseClock(period: String, hourText: String, minuteText: String): Pair<Int, Int>? {
        var hour = parseNumber(hourText) ?: return null
        val minute = when {
            minuteText.isBlank() -> 0
            minuteText == "半" -> 30
            else -> parseNumber(minuteText) ?: return null
        }
        if (minute !in 0..59) return null
        hour = when (period) {
            "下午", "晚上" -> if (hour in 1..11) hour + 12 else hour
            "中午" -> if (hour in 1..10) hour + 12 else hour
            "凌晨", "早上", "上午" -> if (hour == 12) 0 else hour
            else -> if (hour == 12) 0 else hour
        }
        return if (hour in 0..23) hour to minute else null
    }

    private fun parseDayOfWeek(value: String): DayOfWeek? = when (value) {
        "一", "1" -> DayOfWeek.MONDAY
        "二", "2" -> DayOfWeek.TUESDAY
        "三", "3" -> DayOfWeek.WEDNESDAY
        "四", "4" -> DayOfWeek.THURSDAY
        "五", "5" -> DayOfWeek.FRIDAY
        "六", "6" -> DayOfWeek.SATURDAY
        "日", "天", "7" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "星期一"
        DayOfWeek.TUESDAY -> "星期二"
        DayOfWeek.WEDNESDAY -> "星期三"
        DayOfWeek.THURSDAY -> "星期四"
        DayOfWeek.FRIDAY -> "星期五"
        DayOfWeek.SATURDAY -> "星期六"
        DayOfWeek.SUNDAY -> "星期日"
    }

    private fun parseDaysOfWeek(value: String): List<Int> = value.mapNotNull { char ->
        parseDayOfWeek(char.toString())?.value
    }.distinct().sorted()

    private fun nextMonthDay(
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        now: ZonedDateTime,
    ): ZonedDateTime? {
        for (year in now.year..now.year + 4) {
            val date = try {
                LocalDate.of(year, month, day)
            } catch (_: DateTimeException) {
                continue
            }
            val candidate = date.atTime(hour, minute).atZone(now.zone)
            if (candidate > now) return candidate
        }
        return null
    }

    private fun formatClock(value: ZonedDateTime): String =
        "${value.hour}:${value.minute.toString().padStart(2, '0')}"

    private fun formatClock(hour: Int, minute: Int): String =
        "$hour:${minute.toString().padStart(2, '0')}"

    private fun parseNumber(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        if (value.isBlank()) return null
        val normalized = value.replace('兩', '二').replace('〇', '零')
        val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (normalized.all { it in digits }) {
            return normalized.fold(0) { total, char -> total * 10 + digits.getValue(char) }
        }
        var total = 0
        var current = 0
        normalized.forEach { char ->
            when (char) {
                '十' -> {
                    total += (if (current == 0) 1 else current) * 10
                    current = 0
                }
                '百' -> {
                    total += (if (current == 0) 1 else current) * 100
                    current = 0
                }
                else -> current = digits[char] ?: return null
            }
        }
        return total + current
    }
}
