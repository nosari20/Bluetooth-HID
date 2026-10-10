package com.fnosari.bluetoothhid.activities.presenter

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.activities.presenter.screens.PresenterScreen
import androidx.lifecycle.lifecycleScope
import com.fnosari.bluetoothhid.connectivity.hid.PresenterAction
import com.fnosari.bluetoothhid.connectivity.hid.PresenterKeys
import com.fnosari.bluetoothhid.persistance.preferences.DataStorePreferencesProvider
import com.fnosari.bluetoothhid.persistance.preferences.Preferences
import com.fnosari.bluetoothhid.services.HIDService
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme
import com.fnosari.bluetoothhid.utils.SlideVibrator

/**
 * Presentation remote. Volume keys drive slides while this screen is open,
 * and the screen stays on for the whole talk.
 */
class PresenterActivity : ComponentActivity() {

    private var service: HIDService? = null
    private lateinit var vibrator: SlideVibrator
    private var vibrate = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vibrator = SlideVibrator(this)
        setContent {
            BluetoothHIDTheme {
                PresenterScreen(onBack = ::finish, onService = { service = it }, onAction = ::perform)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vibrate = DataStorePreferencesProvider(this, lifecycleScope)
            .readBoolean(Preferences.PRESENTER_VIBRATE, true)
    }

    /** Sends [action] to the host and confirms it with a vibration. */
    private fun perform(action: PresenterAction) {
        val s = service ?: return
        s.sendStroke(PresenterKeys.strokeFor(action, s.layout.value.id))
        if (vibrate) vibrator.pulse()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = PresenterKeys.actionForKeyCode(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) perform(action)
        return true // consume: the phone volume must not change
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (PresenterKeys.actionForKeyCode(keyCode) != null) return true
        return super.onKeyUp(keyCode, event)
    }
}
