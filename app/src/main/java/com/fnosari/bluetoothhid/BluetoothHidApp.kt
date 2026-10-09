package com.fnosari.bluetoothhid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

/**
 * Application entry point. Creates the notification channel used by the
 * HID foreground service.
 */
class BluetoothHidApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "hid_connection"
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
