package com.fnosari.bluetoothhid.activities.presenter

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.activities.presenter.screens.PresenterScreen
import com.fnosari.bluetoothhid.connectivity.hid.PresenterKeys
import com.fnosari.bluetoothhid.services.HIDForegroundService
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

/**
 * Presentation remote. Volume keys drive slides while this screen is open,
 * and the screen stays on for the whole talk.
 */
class PresenterActivity : ComponentActivity() {

    private var service: HIDForegroundService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            BluetoothHIDTheme {
                PresenterScreen(onBack = ::finish, onService = { service = it })
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = PresenterKeys.actionForKeyCode(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) {
            service?.let { it.sendStroke(PresenterKeys.strokeFor(action, it.layout.value.id)) }
        }
        return true // consume: the phone volume must not change
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (PresenterKeys.actionForKeyCode(keyCode) != null) return true
        return super.onKeyUp(keyCode, event)
    }
}
