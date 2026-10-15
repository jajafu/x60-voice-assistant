package com.va.voiceassistant.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderCommandRouterTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = ZonedDateTime.of(2026, 9, 24, 8, 10, 20, 0, zone)

    @Test
    fun `parses relative reminder`() {
        val command = ReminderCommandRouter.parse("50分鐘後提醒我吃藥", now) as ReminderCommand.Schedule

        assertEquals("吃藥", command.request.message)
        assertEquals("50分鐘後", command.request.confirmationTime)
        assertEquals(now.plusMinutes(50).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `parses two hour reminder and tolerates hou homophone`() {
        listOf("2小時後提醒我收衣服", "2小時候提醒我收衣服", "兩個小時後提醒我收衣服").forEach { phrase ->
            val command = ReminderCommandRouter.parse(phrase, now) as ReminderCommand.Schedule
            assertEquals("收衣服", command.request.message)
            assertEquals("2小時後", command.request.confirmationTime)
            assertEquals(now.plusHours(2).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
        }
    }

    @Test
    fun `parses compound hour and minute reminder`() {
        listOf(
            "1小時20分後提醒我關瓦斯",
            "一個小時又二十分鐘後提醒我關瓦斯",
            "提醒我1小時20分後關瓦斯",
        ).forEach { phrase ->
            val command = ReminderCommandRouter.parse(phrase, now) as ReminderCommand.Schedule
            assertEquals("關瓦斯", command.request.message)
            assertEquals("1小時20分鐘後", command.request.confirmationTime)
            assertEquals(now.plusHours(1).plusMinutes(20).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
        }
    }

    @Test
    fun `parses absolute reminder`() {
        val command = ReminderCommandRouter.parse("9：00提醒我倒垃圾", now) as ReminderCommand.Schedule

        assertEquals("倒垃圾", command.request.message)
        assertEquals("今天 9:00", command.request.confirmationTime)
        assertEquals(now.withHour(9).withMinute(0).withSecond(0).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `parses common oclock reminder phrase`() {
        val command = ReminderCommandRouter.parse("十二點鐘提醒我收衣服", now) as ReminderCommand.Schedule

        assertEquals("收衣服", command.request.message)
        assertEquals("今天 12:00", command.request.confirmationTime)
        assertEquals(now.withHour(12).withMinute(0).withSecond(0).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `parses exact hour variants`() {
        listOf(
            "十二點整提醒我收衣服",
            "十二時鐘提醒我收衣服",
            "十二時整提醒我收衣服",
            "十二點鐘的時候提醒我收衣服",
        ).forEach { phrase ->
            val command = ReminderCommandRouter.parse(phrase, now) as ReminderCommand.Schedule
            assertEquals("收衣服", command.request.message)
            assertEquals(now.withHour(12).withMinute(0).withSecond(0).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
        }
    }

    @Test
    fun `bare hour chooses next matching time later today`() {
        val command = ReminderCommandRouter.parse("8點提醒我出門", now) as ReminderCommand.Schedule

        assertEquals("今天 20:00", command.request.confirmationTime)
        assertEquals(now.withHour(20).withMinute(0).withSecond(0).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `explicit past today is rejected`() {
        val command = ReminderCommandRouter.parse("今天早上8點提醒我出門", now)

        assertTrue(command is ReminderCommand.Invalid)
    }

    @Test
    fun `bare six at five pm chooses six pm today`() {
        val lateAfternoon = now.withHour(17)
        val command = ReminderCommandRouter.parse("六點鐘提醒我收衣服", lateAfternoon) as ReminderCommand.Schedule

        assertEquals("今天 18:00", command.request.confirmationTime)
        assertEquals(lateAfternoon.withHour(18).withMinute(0).withSecond(0).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `bare six at nine pm chooses six am tomorrow`() {
        val evening = now.withHour(21)
        val command = ReminderCommandRouter.parse("六點鐘提醒我收衣服", evening) as ReminderCommand.Schedule

        assertEquals("明天 6:00", command.request.confirmationTime)
        assertEquals(evening.plusDays(1).withHour(6).withMinute(0).withSecond(0).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `parses chinese numerals and period`() {
        val command = ReminderCommandRouter.parse("明天下午三點半提醒我開會", now) as ReminderCommand.Schedule

        assertEquals("明天 15:30", command.request.confirmationTime)
        assertEquals(now.plusDays(1).withHour(15).withMinute(30).withSecond(0).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `parses next weekday reminder`() {
        val command = ReminderCommandRouter.parse("星期一早上8點提醒我帶手機", now) as ReminderCommand.Schedule
        val expected = ZonedDateTime.of(2026, 9, 28, 8, 0, 0, 0, zone)

        assertEquals("帶手機", command.request.message)
        assertEquals("星期一 8:00", command.request.confirmationTime)
        assertEquals(expected.toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `weekday reminder rolls to following week after the time has passed`() {
        val mondayMorning = ZonedDateTime.of(2026, 9, 28, 8, 10, 0, 0, zone)
        val command = ReminderCommandRouter.parse("週一早上8點叫我帶手機", mondayMorning) as ReminderCommand.Schedule
        val expected = ZonedDateTime.of(2026, 10, 5, 8, 0, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `parses month and day reminder`() {
        listOf(
            "10月6日早上八點提醒我帶手機",
            "十月六號早上8點提醒我帶手機",
            "提醒我10月6日早上8點帶手機",
        ).forEach { phrase ->
            val command = ReminderCommandRouter.parse(phrase, now) as ReminderCommand.Schedule
            val expected = ZonedDateTime.of(2026, 10, 6, 8, 0, 0, 0, zone)

            assertEquals("帶手機", command.request.message)
            assertEquals("10月6日 8:00", command.request.confirmationTime)
            assertEquals(expected.toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
        }
    }

    @Test
    fun `month and day reminder rolls to next year after the date has passed`() {
        val afterDate = ZonedDateTime.of(2026, 10, 7, 8, 0, 0, 0, zone)
        val command = ReminderCommandRouter.parse("10月6日早上8點提醒我帶手機", afterDate) as ReminderCommand.Schedule
        val expected = ZonedDateTime.of(2027, 10, 6, 8, 0, 0, 0, zone)

        assertEquals(expected.toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `parses reminder-first relative phrase`() {
        val command = ReminderCommandRouter.parse("提醒我半小時後吃藥", now) as ReminderCommand.Schedule

        assertEquals(now.plusMinutes(30).toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
    }

    @Test
    fun `parses daily recurring reminder`() {
        val command = ReminderCommandRouter.parse("每天早上七點提醒我吃藥", now) as ReminderCommand.Schedule
        val expected = now.withHour(7).withMinute(0).withSecond(0).plusDays(1)

        assertEquals("吃藥", command.request.message)
        assertEquals("每天 7:00", command.request.confirmationTime)
        assertEquals(expected.toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
        assertEquals(
            ReminderRecurrence(ReminderFrequency.DAILY, hour = 7, minute = 0),
            command.request.recurrence,
        )
    }

    @Test
    fun `daily recurrence without period chooses nearest future clock`() {
        val evening = now.withHour(21)
        val command = ReminderCommandRouter.parse("每天六點提醒我收衣服", evening) as ReminderCommand.Schedule

        assertEquals(6, command.request.recurrence?.hour)
        assertEquals(
            evening.plusDays(1).withHour(6).withMinute(0).withSecond(0).toInstant().toEpochMilli(),
            command.request.triggerAtEpochMillis,
        )
    }

    @Test
    fun `daily recurrence without period can choose pm later today`() {
        val lateAfternoon = now.withHour(17)
        val command = ReminderCommandRouter.parse("每天六點提醒我收衣服", lateAfternoon) as ReminderCommand.Schedule

        assertEquals(18, command.request.recurrence?.hour)
        assertEquals(
            lateAfternoon.withHour(18).withMinute(0).withSecond(0).toInstant().toEpochMilli(),
            command.request.triggerAtEpochMillis,
        )
    }

    @Test
    fun `parses weekly single-day recurring reminder`() {
        val command = ReminderCommandRouter.parse("每週一晚上八點提醒我倒垃圾", now) as ReminderCommand.Schedule
        val expected = ZonedDateTime.of(2026, 9, 28, 20, 0, 0, 0, zone)

        assertEquals("倒垃圾", command.request.message)
        assertEquals("每週一 20:00", command.request.confirmationTime)
        assertEquals(expected.toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
        assertEquals(
            ReminderRecurrence(ReminderFrequency.WEEKLY, listOf(1), 20, 0),
            command.request.recurrence,
        )
    }

    @Test
    fun `parses weekly multi-day recurring reminder`() {
        val command = ReminderCommandRouter.parse("每週一、三、五下午六點提醒我運動", now) as ReminderCommand.Schedule
        val expected = ZonedDateTime.of(2026, 9, 25, 18, 0, 0, 0, zone)

        assertEquals("運動", command.request.message)
        assertEquals("每週一、三、五 18:00", command.request.confirmationTime)
        assertEquals(expected.toInstant().toEpochMilli(), command.request.triggerAtEpochMillis)
        assertEquals(listOf(1, 3, 5), command.request.recurrence?.daysOfWeek)
    }

    @Test
    fun `parses reminder-first recurring phrases`() {
        val daily = ReminderCommandRouter.parse("提醒我每天晚上九點吃藥", now) as ReminderCommand.Schedule
        val weekly = ReminderCommandRouter.parse("提醒我每週二和週四早上八點運動", now) as ReminderCommand.Schedule

        assertEquals(ReminderFrequency.DAILY, daily.request.recurrence?.frequency)
        assertEquals("吃藥", daily.request.message)
        assertEquals(ReminderFrequency.WEEKLY, weekly.request.recurrence?.frequency)
        assertEquals(listOf(2, 4), weekly.request.recurrence?.daysOfWeek)
        assertEquals("運動", weekly.request.message)
    }

    @Test
    fun `does not turn unrelated recurring phrase into a reminder`() {
        assertNull(ReminderCommandRouter.parse("每天早上七點天氣如何", now))
    }

    @Test
    fun `does not turn an unrelated future phrase into a reminder`() {
        assertNull(ReminderCommandRouter.parse("50分鐘後會下雨嗎", now))
    }
}
