package com.fnosari.bluetoothhid.activities.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.activities.keyboard.screens.KeyboardScreen
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class KeyboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BluetoothHIDTheme { KeyboardScreen(onBack = ::finish) } }
    }
}
