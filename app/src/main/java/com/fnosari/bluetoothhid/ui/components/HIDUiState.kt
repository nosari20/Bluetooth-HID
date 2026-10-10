package com.fnosari.bluetoothhid.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice.HIDDeviceExitCode
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyboardLayout
import com.fnosari.bluetoothhid.connectivity.hid.layouts.UsLayout
import com.fnosari.bluetoothhid.services.HIDService
import com.fnosari.bluetoothhid.services.HIDState
import com.fnosari.bluetoothhid.services.TypingProgress

@Composable
fun HIDService?.connectionState(): HIDState =
    this?.state?.collectAsStateWithLifecycle()?.value ?: HIDState.Starting

@Composable
fun HIDService?.currentLayout(): KeyboardLayout =
    this?.layout?.collectAsStateWithLifecycle()?.value ?: UsLayout

@Composable
fun HIDService?.typingProgress(): TypingProgress? =
    this?.typing?.collectAsStateWithLifecycle()?.value

@StringRes
fun layoutLabel(id: String): Int = when (id) {
    "fr-win" -> R.string.layout_fr_win
    "fr-linux" -> R.string.layout_fr_linux
    "fr-mac" -> R.string.layout_fr_mac
    else -> R.string.layout_us
}

@StringRes
fun errorLabel(code: HIDDeviceExitCode): Int = when (code) {
    HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_SUPPORTED -> R.string.error_bluetooth_not_supported
    HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED -> R.string.error_bluetooth_not_enabled
    HIDDeviceExitCode.ERROR_BLUETOOTH_CONNECT_PERMISSION_NOT_GRANTED -> R.string.error_permission
    HIDDeviceExitCode.ERROR_HID_PROFILE_NOT_SUPPORTED -> R.string.error_hid_not_supported
    HIDDeviceExitCode.ERROR_HID_REGISTER_FAILED -> R.string.error_hid_register_failed
    HIDDeviceExitCode.ERROR_HID_NOT_CONNECTED -> R.string.error_hid_not_connected
    HIDDeviceExitCode.ERROR_HID_SEND_FAILED -> R.string.error_hid_send_failed
}
