package com.va.voiceassistant.reminder

import android.content.Context
import android.content.Intent

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

object ReminderAlarmState {
    @Volatile
    var isRinging: Boolean = false
        internal set
}
