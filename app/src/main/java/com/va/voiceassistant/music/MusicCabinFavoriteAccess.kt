package com.va.voiceassistant.music

import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/** Grants Media3's trusted-controller status only after the user enables notification access. */
class MusicCabinFavoriteListenerService : NotificationListenerService()

object MusicCabinFavoriteAccess {
    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
