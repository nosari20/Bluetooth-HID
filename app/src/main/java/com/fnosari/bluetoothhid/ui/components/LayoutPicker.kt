package com.fnosari.bluetoothhid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyboardLayout
import com.fnosari.bluetoothhid.connectivity.hid.layouts.Layouts

/** Chip showing the host layout; tap opens a picker. */
@Composable
fun LayoutChip(layout: KeyboardLayout, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text(stringResource(layoutLabel(layout.id))) },
        leadingIcon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) }
    )
    if (open) {
        LayoutPickerDialog(layout.id, onPick = { onPick(it); open = false }, onDismiss = { open = false })
    }
}

@Composable
fun LayoutPickerDialog(currentId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_choose)) },
        text = {
            Column {
                for (l in Layouts.ALL) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(l.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = l.id == currentId, onClick = { onPick(l.id) })
                        Text(stringResource(layoutLabel(l.id)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
