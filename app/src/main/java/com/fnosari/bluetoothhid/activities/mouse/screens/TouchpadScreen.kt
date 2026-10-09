package com.fnosari.bluetoothhid.activities.mouse.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.connectivity.hid.HIDMouseButtons
import com.fnosari.bluetoothhid.persistance.preferences.Preferences
import com.fnosari.bluetoothhid.ui.components.ConnectionBanner
import com.fnosari.bluetoothhid.ui.components.connectionState
import com.fnosari.bluetoothhid.ui.layouts.BasicLayoutWithTopBar
import com.fnosari.bluetoothhid.utils.PointerAccumulator
import com.fnosari.bluetoothhid.utils.ScrollAccumulator
import com.fnosari.bluetoothhid.utils.rememberHIDService
import kotlin.math.max

private interface TouchpadCallbacks {
    fun onMove(dx: Float, dy: Float)
    fun onScroll(dy: Float)
    fun onTap()
    fun onTwoFingerTap()
    fun onLongPress()
}

@Composable
fun TouchpadScreen(onBack: () -> Unit) {
    val service = rememberHIDService()
    val state = service.connectionState()
    val haptic = LocalHapticFeedback.current
    val sensitivity = service?.preferences?.readDouble(Preferences.MOUSE_SENSITIVITY, 1.0)?.toFloat() ?: 1f
    val pointer = remember { PointerAccumulator() }
    pointer.sensitivity = sensitivity
    val scroll = remember { ScrollAccumulator() }
    var dragLocked by remember { mutableStateOf(false) }
    val currentService by rememberUpdatedState(service)

    val callbacks = remember {
        object : TouchpadCallbacks {
            override fun onMove(dx: Float, dy: Float) {
                val (x, y) = pointer.move(dx, dy)
                if (x != 0 || y != 0) currentService?.mouseMove(x, y)
            }

            override fun onScroll(dy: Float) {
                val steps = scroll.scroll(dy)
                if (steps != 0) currentService?.mouseScroll(steps)
            }

            override fun onTap() {
                if (dragLocked) {
                    dragLocked = false
                    currentService?.setMouseButtons(0)
                } else {
                    currentService?.mouseClick(HIDMouseButtons.LEFT)
                }
            }

            override fun onTwoFingerTap() {
                currentService?.mouseClick(HIDMouseButtons.RIGHT)
            }

            override fun onLongPress() {
                dragLocked = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentService?.setMouseButtons(HIDMouseButtons.LEFT)
            }
        }
    }

    BasicLayoutWithTopBar(title = stringResource(R.string.mouse_activity_name), leftAction = onBack) {
        Column(Modifier.fillMaxSize()) {
            ConnectionBanner(state)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(
                        if (dragLocked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(24.dp)
                    )
                    .pointerInput(Unit) { awaitEachGesture { trackGesture(callbacks) } }
            ) {
                Text(
                    stringResource(R.string.mouse_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(24.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                HoldButton(stringResource(R.string.mouse_left), Modifier.weight(1f)) { down ->
                    currentService?.setMouseButtons(if (down) HIDMouseButtons.LEFT else 0)
                }
                HoldButton(stringResource(R.string.mouse_right), Modifier.weight(1f)) { down ->
                    currentService?.setMouseButtons(if (down) HIDMouseButtons.RIGHT else 0)
                }
            }
        }
    }
}

/** Button reporting press and release separately (needed for drag). */
@Composable
private fun HoldButton(label: String, modifier: Modifier, onChange: (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Surface(
        color = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(72.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    onChange(true)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    pressed = false
                    onChange(false)
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label) }
    }
}

/**
 * One touchpad gesture. Phase 1 (until slop, release, 2nd finger or
 * long-press timeout) decides tap / long press; phase 2 streams moves.
 */
private suspend fun AwaitPointerEventScope.trackGesture(cb: TouchpadCallbacks) {
    val down = awaitFirstDown(requireUnconsumed = false)
    val slop = viewConfiguration.touchSlop
    var maxPointers = 1
    var travel = 0f
    var released = false

    val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val ev = awaitPointerEvent()
            val pressed = ev.changes.count { it.pressed }
            maxPointers = max(maxPointers, pressed)
            ev.changes.forEach {
                travel += (it.position - it.previousPosition).getDistance()
                it.consume()
            }
            if (pressed == 0) { released = true; break }
            if (travel > slop || maxPointers > 1) break
        }
        true
    }

    if (released) {
        if (travel <= slop) cb.onTap()
        return
    }
    if (decided == null) cb.onLongPress()

    var lastUptime = down.uptimeMillis
    while (true) {
        val ev = awaitPointerEvent()
        val pressed = ev.changes.filter { it.pressed }
        lastUptime = ev.changes.maxOf { it.uptimeMillis }
        if (pressed.isEmpty()) break
        maxPointers = max(maxPointers, pressed.size)
        if (pressed.size == 1) {
            val d = pressed[0].position - pressed[0].previousPosition
            travel += d.getDistance()
            cb.onMove(d.x, d.y)
        } else {
            val dy = pressed.map { it.position.y - it.previousPosition.y }.average().toFloat()
            travel += kotlin.math.abs(dy)
            cb.onScroll(dy)
        }
        ev.changes.forEach { it.consume() }
    }
    if (maxPointers > 1 && travel <= slop * 2 && lastUptime - down.uptimeMillis < 300) cb.onTwoFingerTap()
}
