package com.fnosari.bluetoothhid.activities.presenter.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.connectivity.hid.HIDConsumer
import com.fnosari.bluetoothhid.connectivity.hid.PresenterAction
import com.fnosari.bluetoothhid.services.HIDForegroundService
import com.fnosari.bluetoothhid.services.HIDState
import com.fnosari.bluetoothhid.ui.components.ConnectionBanner
import com.fnosari.bluetoothhid.ui.components.connectionState
import com.fnosari.bluetoothhid.ui.layouts.BasicLayoutWithTopBar
import com.fnosari.bluetoothhid.utils.rememberHIDService

@Composable
fun PresenterScreen(
    onBack: () -> Unit,
    onService: (HIDForegroundService?) -> Unit,
    onAction: (PresenterAction) -> Unit
) {
    val service = rememberHIDService()
    val state = service.connectionState()
    val enabled = state is HIDState.Connected
    SideEffect { onService(service) }

    fun send(action: PresenterAction) = onAction(action)

    BasicLayoutWithTopBar(title = stringResource(R.string.presenter_activity_name), leftAction = onBack) {
        Column(Modifier.fillMaxSize()) {
            ConnectionBanner(state)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).padding(16.dp)
            ) {
                Zone(
                    label = stringResource(R.string.presenter_previous),
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { send(PresenterAction.PREVIOUS) }
                Zone(
                    label = stringResource(R.string.presenter_next),
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    enabled = enabled,
                    modifier = Modifier.weight(2f)
                ) { send(PresenterAction.NEXT) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { send(PresenterAction.PLAY_VIDEO) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.presenter_play_video), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = { service?.sendConsumer(HIDConsumer.PLAY_PAUSE) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayCircle, contentDescription = null)
                        Text(stringResource(R.string.presenter_media), Modifier.padding(start = 8.dp))
                    }
                }
                Text(
                    stringResource(R.string.presenter_volume_hint),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Zone(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        color = if (enabled) container else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp))
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
