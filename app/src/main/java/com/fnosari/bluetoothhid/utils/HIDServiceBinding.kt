package com.fnosari.bluetoothhid.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fnosari.bluetoothhid.services.HIDForegroundService

/**
 * Binds to [HIDForegroundService] for the lifetime of the composition.
 * Does not create the service (MainActivity starts it); returns null until bound.
 */
@Composable
fun rememberHIDService(): HIDForegroundService? {
    val context = LocalContext.current
    var service by remember { mutableStateOf<HIDForegroundService?>(null) }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as HIDForegroundService.LocalBinder).service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        context.bindService(Intent(context, HIDForegroundService::class.java), connection, 0)
        onDispose {
            context.unbindService(connection)
            service = null
        }
    }
    return service
}
