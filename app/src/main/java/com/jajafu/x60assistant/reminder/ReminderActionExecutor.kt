package com.jajafu.x60assistant.reminder

import android.content.Context
import java.util.UUID

data class ReminderActionResult(val message: String)

fun interface ReminderActionExecutor {
    suspend fun execute(command: ReminderCommand): ReminderActionResult
}

class AndroidReminderActionExecutor(context: Context) : ReminderActionExecutor {
    private val appContext = context.applicationContext
    private val store = ReminderStore(appContext)
    private val scheduler = ReminderScheduler(appContext)

    override suspend fun execute(command: ReminderCommand): ReminderActionResult = when (command) {
        is ReminderCommand.Invalid -> ReminderActionResult(command.response)
        is ReminderCommand.Schedule -> {
            val request = command.request
            val reminder = ScheduledReminder(
                id = UUID.randomUUID().toString(),
                message = request.message,
                triggerAtEpochMillis = request.triggerAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
            )
            store.add(reminder)
            try {
                when (scheduler.schedule(reminder)) {
                    ReminderScheduleMode.EXACT -> ReminderActionResult(
                        "好的，${request.confirmationTime}提醒你${request.message}。",
                    )
                    ReminderScheduleMode.APPROXIMATE -> ReminderActionResult(
                        "已設定${request.confirmationTime}提醒你${request.message}，但還沒開啟精準鬧鐘權限，時間可能延遲。請點通知授權。",
                    )
                }
            } catch (error: Exception) {
                store.remove(reminder.id)
                ReminderActionResult("提醒設定失敗，請稍後再試一次。")
            }
        }
    }
}
