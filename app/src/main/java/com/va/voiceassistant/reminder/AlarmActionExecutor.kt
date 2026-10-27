package com.va.voiceassistant.reminder

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AlarmActionExecutor {
    /** Temporarily silences an alarm so the user's command can be recognized. */
    fun pauseForVoice(): Boolean
    /** Resumes only the alarm that was temporarily silenced for a voice session. */
    fun resumeAfterVoice(): Boolean
    /** Permanently stops the active alarm. */
    fun stop(): Boolean
}

class AndroidAlarmActionExecutor(context: Context) : AlarmActionExecutor {
    private val appContext = context.applicationContext

    override fun pauseForVoice(): Boolean = sendIfRinging(ReminderAlarmService.ACTION_PAUSE_FOR_VOICE)

    override fun resumeAfterVoice(): Boolean = sendIfRinging(ReminderAlarmService.ACTION_RESUME_AFTER_VOICE)

    override fun stop(): Boolean = sendIfRinging(ReminderAlarmService.ACTION_STOP)

    private fun sendIfRinging(action: String): Boolean {
        if (!ReminderAlarmState.isRinging) return false
        return runCatching {
            appContext.startService(
                Intent(appContext, ReminderAlarmService::class.java).apply { this.action = action },
            )
        }.isSuccess
    }
}

/**
 * 鬧鐘響鈴狀態。[isRinging] 保留給服務內與 Executor 的同步檢查；
 * UI（提醒頁的「停止鈴聲」橫幅）收集 [ringing] 觀察變化。
 */
object ReminderAlarmState {
    private val _ringing = MutableStateFlow(false)

    /** UI 收集用；值與 [isRinging] 恆一致。 */
    val ringing: StateFlow<Boolean> = _ringing.asStateFlow()

    var isRinging: Boolean
        get() = _ringing.value
        internal set(value) {
            _ringing.value = value
        }
}
