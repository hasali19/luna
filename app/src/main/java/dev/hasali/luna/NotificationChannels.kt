package dev.hasali.luna

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

object NotificationChannels {

    const val APP_UPDATES = "app_updates"

    fun createAll(context: Context) {
        createUpdateNotificationChannel(context)
    }

    private fun createUpdateNotificationChannel(context: Context) {
        val channel =
            NotificationChannelCompat.Builder(APP_UPDATES, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("App updates")
                .setShowBadge(true)
                .build()
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.createNotificationChannel(channel)
    }
}
