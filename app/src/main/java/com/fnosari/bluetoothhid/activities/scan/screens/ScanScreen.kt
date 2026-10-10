package com.fnosari.bluetoothhid.activities.scan.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.activities.scan.components.CameraPreview
import com.fnosari.bluetoothhid.activities.scan.components.PreviewSheet
import com.fnosari.bluetoothhid.persistance.preferences.Preferences
import com.fnosari.bluetoothhid.services.HIDState
import com.fnosari.bluetoothhid.services.TypingEvent
import com.fnosari.bluetoothhid.ui.components.ConnectionBanner
import com.fnosari.bluetoothhid.ui.components.connectionState
import com.fnosari.bluetoothhid.ui.components.currentLayout
import com.fnosari.bluetoothhid.ui.components.typingProgress
import com.fnosari.bluetoothhid.ui.layouts.BasicLayoutWithTopBar
import com.fnosari.bluetoothhid.utils.rememberHIDService
import kotlinx.coroutines.launch

@Composable
fun ScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val service = rememberHIDService()
    val state = service.connectionState()
    val layout = service.currentLayout()
    val typing = service.typingProgress()
    var scanned by remember { mutableStateOf<String?>(null) }

    var cameraGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    LaunchedEffect(Unit) { if (!cameraGranted) cameraLauncher.launch(Manifest.permission.CAMERA) }

    val msgDone = stringResource(R.string.typing_done)
    val msgCancelled = stringResource(R.string.typing_cancelled)
    val msgResume = stringResource(R.string.typing_resume)
    val resources = context.resources

    LaunchedEffect(service) {
        service?.events?.collect { event ->
            when (event) {
                TypingEvent.Completed -> snackbar.showSnackbar(msgDone)
                TypingEvent.Cancelled -> snackbar.showSnackbar(msgCancelled)
                is TypingEvent.Interrupted -> scope.launch {
                    val r = snackbar.showSnackbar(
                        resources.getString(R.string.typing_interrupted, event.atChar + 1),
                        actionLabel = msgResume
                    )
                    if (r == SnackbarResult.ActionPerformed) {
                        service.typeText(event.text.substring(event.atChar))
                    }
                }
            }
        }
    }

    fun type(text: String) {
        val enter = service?.preferences?.readBoolean(Preferences.ENTER_AT_END, false) ?: false
        service?.typeText(text, enterAtEnd = enter)
    }

    BasicLayoutWithTopBar(title = stringResource(R.string.scan_activity_name), leftAction = onBack) {
        Box(Modifier.fillMaxSize()) {
            if (cameraGranted) {
                CameraPreview(
                    enabled = scanned == null,
                    onCode = { code ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scanned = code
                        val auto = service?.preferences?.readBoolean(Preferences.AUTO_TYPE, false) ?: false
                        if (auto && state is HIDState.Connected) type(code)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text(stringResource(R.string.scan_camera_permission))
                    Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.permissions_grant))
                    }
                }
            }

            Column(Modifier.align(Alignment.TopCenter)) {
                ConnectionBanner(state)
                if (scanned == null && cameraGranted) {
                    Text(
                        stringResource(R.string.scan_hint),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            val text = scanned
            if (text != null) {
                PreviewSheet(
                    text = text,
                    layout = layout,
                    connected = state is HIDState.Connected,
                    typing = typing,
                    onPickLayout = { service?.setLayoutForCurrentHost(it) },
                    onType = { type(text) },
                    onCancel = { service?.cancelTyping() },
                    onCopy = {
                        val clip = ClipData.newPlainText("scan", text)
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    },
                    onRescan = { scanned = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 260.dp))
        }
    }
}
