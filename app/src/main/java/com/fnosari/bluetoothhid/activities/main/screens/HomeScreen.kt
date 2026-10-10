package com.fnosari.bluetoothhid.activities.main.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.activities.keyboard.KeyboardActivity
import com.fnosari.bluetoothhid.activities.main.MainActivity
import com.fnosari.bluetoothhid.activities.mouse.MouseActivity
import com.fnosari.bluetoothhid.activities.presenter.PresenterActivity
import com.fnosari.bluetoothhid.activities.scan.ScanActivity
import com.fnosari.bluetoothhid.activities.settings.SettingsActivity
import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice.HIDDeviceExitCode
import com.fnosari.bluetoothhid.services.HIDState
import com.fnosari.bluetoothhid.ui.components.LayoutChip
import com.fnosari.bluetoothhid.ui.components.connectionState
import com.fnosari.bluetoothhid.ui.components.currentLayout
import com.fnosari.bluetoothhid.ui.components.errorLabel
import com.fnosari.bluetoothhid.ui.layouts.BasicLayoutWithTopBar
import com.fnosari.bluetoothhid.utils.rememberHIDService

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val service = rememberHIDService()
    val state = service.connectionState()
    val layout = service.currentLayout()
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        service?.retry()
    }

    BasicLayoutWithTopBar(
        title = stringResource(R.string.app_name),
        rightActions = {
            IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_activity_name))
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val text = when (state) {
                        HIDState.Starting -> stringResource(R.string.state_starting)
                        HIDState.Idle -> stringResource(R.string.state_idle)
                        is HIDState.Connecting -> stringResource(R.string.state_connecting, state.host.name)
                        is HIDState.Connected -> stringResource(R.string.state_connected, state.host.name)
                        is HIDState.Unavailable -> stringResource(errorLabel(state.code))
                    }
                    Text(text, style = MaterialTheme.typography.titleMedium)
                    if (state is HIDState.Connected) {
                        LayoutChip(layout) { service?.setLayoutForCurrentHost(it) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (state) {
                            is HIDState.Connected -> OutlinedButton(onClick = { service?.disconnect() }) {
                                Text(stringResource(R.string.home_disconnect))
                            }
                            is HIDState.Unavailable -> if (state.code == HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED) {
                                Button(onClick = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
                                    Text(stringResource(R.string.action_enable_bluetooth))
                                }
                            } else {
                                Button(onClick = { service?.retry() }) { Text(stringResource(R.string.action_retry)) }
                            }
                            else -> {}
                        }
                        Button(onClick = { navController.navigate(MainActivity.Routes.DEVICES.name) }) {
                            Icon(Icons.Rounded.Devices, contentDescription = null)
                            Text(stringResource(R.string.home_devices), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile(Icons.Rounded.QrCodeScanner, stringResource(R.string.home_tile_scan), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, ScanActivity::class.java))
                }
                Tile(Icons.Rounded.Keyboard, stringResource(R.string.home_tile_keyboard), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, KeyboardActivity::class.java))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile(Icons.Rounded.Mouse, stringResource(R.string.home_tile_mouse), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, MouseActivity::class.java))
                }
                Tile(Icons.Rounded.Slideshow, stringResource(R.string.home_tile_presenter), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, PresenterActivity::class.java))
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.aspectRatio(1f)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
