package com.jajafu.x60assistant.reminder

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

class ReminderPreferences(context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun soundUri(): Uri = preferences.getString(KEY_SOUND_URI, null)
        ?.takeIf(String::isNotBlank)
        ?.toUri()
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    fun setSoundUri(uri: Uri) {
        preferences.edit { putString(KEY_SOUND_URI, uri.toString()) }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    companion object {
        private const val PREFERENCES_NAME = "reminder_preferences"
        private const val KEY_SOUND_URI = "sound_uri"
    }
}
