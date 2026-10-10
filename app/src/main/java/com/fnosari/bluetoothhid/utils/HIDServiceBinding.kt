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
import com.fnosari.bluetoothhid.services.HIDService

/**
 * Starts (if needed) and binds [HIDService] for the lifetime of the composition;
 * returns null until bound. Called from visible screens only, so starting is allowed.
 */
@Composable
fun rememberHIDService(): HIDService? {
    val context = LocalContext.current
    var service by remember { mutableStateOf<HIDService?>(null) }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as HIDService.LocalBinder).service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        HIDService.start(context)
        context.bindService(Intent(context, HIDService::class.java), connection, 0)
        onDispose {
            context.unbindService(connection)
            service = null
        }
    }
    return service
}
