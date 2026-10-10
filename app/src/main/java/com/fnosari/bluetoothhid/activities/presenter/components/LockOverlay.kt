package com.fnosari.bluetoothhid.activities.presenter.components

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.utils.UnlockGate
import kotlinx.coroutines.delay

private const val UNLOCK_WINDOW_MS = 3000L

/**
 * Black full-screen lock (YouTube style). Swallows every touch and the back
 * gesture; volume keys keep working because the activity handles them.
 * Tap anywhere → Unlock button for 3 s → tap it to unlock.
 */
@Composable
fun LockOverlay(onUnlock: () -> Unit) {
    val gate = remember { UnlockGate(UNLOCK_WINDOW_MS) }
    var armed by remember { mutableStateOf(false) }
    var armCount by remember { mutableIntStateOf(0) }

    BackHandler { }  // never leave the presenter by accident while locked

    // every tap restarts the 3 s window
    LaunchedEffect(armCount) {
        if (armCount > 0) {
            armed = true
            delay(UNLOCK_WINDOW_MS)
            armed = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    gate.tapBackground(SystemClock.uptimeMillis())
                    armCount++
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
            Text(
                stringResource(R.string.presenter_locked),
                color = Color.Gray,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.presenter_locked_hint),
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            if (armed) {
                FilledTonalButton(onClick = {
                    if (gate.tapUnlock(SystemClock.uptimeMillis())) onUnlock() else armed = false
                }) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = null)
                    Text(stringResource(R.string.presenter_unlock), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
