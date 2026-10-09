package com.fnosari.bluetoothhid.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HIDForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
