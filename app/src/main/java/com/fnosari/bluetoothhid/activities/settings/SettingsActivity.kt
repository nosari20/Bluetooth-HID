package com.fnosari.bluetoothhid.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.activities.settings.screens.SettingsScreen
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BluetoothHIDTheme { SettingsScreen(onBack = ::finish) } }
    }
}
