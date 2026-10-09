package com.fnosari.bluetoothhid.activities.keyboard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.activities.keyboard.components.SpecialKeysBar
import com.fnosari.bluetoothhid.services.HIDState
import com.fnosari.bluetoothhid.ui.components.ConnectionBanner
import com.fnosari.bluetoothhid.ui.components.LayoutChip
import com.fnosari.bluetoothhid.ui.components.connectionState
import com.fnosari.bluetoothhid.ui.components.currentLayout
import com.fnosari.bluetoothhid.ui.layouts.BasicLayoutWithTopBar
import com.fnosari.bluetoothhid.utils.TextDiff
import com.fnosari.bluetoothhid.utils.rememberHIDService

@Composable
fun KeyboardScreen(onBack: () -> Unit) {
    val service = rememberHIDService()
    val state = service.connectionState()
    val layout = service.currentLayout()
    var live by rememberSaveable { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var sticky by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // live text mirrors the host: start from empty on each (re)connection
    val connected = state is HIDState.Connected
    LaunchedEffect(connected) { if (live) text = "" }

    fun onTextChange(new: String) {
        if (live && (service == null || !connected)) return   // nothing would reach the host
        if (live && service != null) {
            val edit = TextDiff.diff(text, new)
            val stroke = if (sticky != 0 && edit.deleteCount == 0 && edit.insert.length == 1) {
                layout.strokesFor(edit.insert[0])?.lastOrNull()
            } else null
            if (stroke != null) {
                service.sendStroke(stroke, sticky)
                sticky = 0
            } else {
                service.backspace(edit.deleteCount)
                if (edit.insert.isNotEmpty()) service.typeText(edit.insert, track = false)
            }
        }
        text = new
    }

    BasicLayoutWithTopBar(title = stringResource(R.string.keyboard_activity_name), leftAction = onBack) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ConnectionBanner(state)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SegmentedButton(
                        selected = live, onClick = { live = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text(stringResource(R.string.keyboard_live)) }
                    SegmentedButton(
                        selected = !live, onClick = { live = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text(stringResource(R.string.keyboard_block)) }
                }
                LayoutChip(layout) { service?.setLayoutForCurrentHost(it) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = ::onTextChange,
                placeholder = {
                    Text(stringResource(if (live) R.string.keyboard_hint_live else R.string.keyboard_hint_block))
                },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .focusRequester(focus)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (!live) {
                    Button(
                        onClick = { service?.typeText(text) },
                        enabled = state is HIDState.Connected && text.isNotEmpty()
                    ) { Text(stringResource(R.string.keyboard_send)) }
                }
                TextButton(onClick = { text = "" }) { Text(stringResource(R.string.keyboard_clear)) }
            }
            SpecialKeysBar(
                sticky = sticky,
                layout = layout,
                onToggleSticky = { sticky = sticky xor it },
                onKey = { usage -> service?.sendKey(usage, sticky); sticky = 0 },
                onShortcut = { usage, mods -> service?.sendKey(usage, mods) },
                onConsumer = { service?.sendConsumer(it) }
            )
        }
    }
}
