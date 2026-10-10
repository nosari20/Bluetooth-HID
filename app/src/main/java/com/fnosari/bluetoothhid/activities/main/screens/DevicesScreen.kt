package com.fnosari.bluetoothhid.activities.main.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.model.Host
import com.fnosari.bluetoothhid.services.HIDState
import com.fnosari.bluetoothhid.ui.components.connectionState
import com.fnosari.bluetoothhid.ui.layouts.BasicLayoutWithTopBar
import com.fnosari.bluetoothhid.utils.rememberHIDService

@Composable
fun DevicesScreen(navController: NavController) {
    val service = rememberHIDService()
    val state = service.connectionState()
    var hosts by remember { mutableStateOf(emptyList<Host>()) }
    var showPairHelp by remember { mutableStateOf(false) }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showPairHelp = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hosts = service?.bondedHosts() ?: emptyList() }
    LaunchedEffect(service) { hosts = service?.bondedHosts() ?: emptyList() }

    val connectedAddress = (state as? HIDState.Connected)?.host?.address
    val connectingAddress = (state as? HIDState.Connecting)?.host?.address

    BasicLayoutWithTopBar(
        title = stringResource(R.string.devices_title),
        leftAction = { navController.popBackStack() }
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Button(
                    onClick = {
                        discoverable.launch(
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.devices_pair_new)) }
            }
            if (showPairHelp) item {
                Card { Text(stringResource(R.string.devices_pair_help), Modifier.padding(16.dp)) }
            }
            item {
                Text(
                    stringResource(R.string.devices_paired),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (hosts.isEmpty()) item { Text(stringResource(R.string.devices_empty)) }
            items(hosts, key = { it.address }) { host ->
                ListItem(
                    headlineContent = { Text(host.name) },
                    supportingContent = { Text(host.address) },
                    trailingContent = {
                        when (host.address) {
                            connectedAddress -> Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            connectingAddress -> CircularProgressIndicator(Modifier.size(24.dp))
                            else -> TextButton(onClick = { service?.connect(host.address) }) {
                                Text(stringResource(R.string.devices_connect))
                            }
                        }
                    }
                )
            }
        }
    }
}
