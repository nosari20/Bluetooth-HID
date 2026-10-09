package com.fnosari.bluetoothhid.activities.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyboardLayout
import com.fnosari.bluetoothhid.connectivity.hid.layouts.TextTyper
import com.fnosari.bluetoothhid.services.TypingProgress
import com.fnosari.bluetoothhid.ui.components.LayoutChip

/**
 * Bottom panel showing scanned text before typing. Unmappable characters
 * for [layout] are shown in red.
 */
@Composable
fun PreviewSheet(
    text: String,
    layout: KeyboardLayout,
    connected: Boolean,
    typing: TypingProgress?,
    onPickLayout: (String) -> Unit,
    onType: () -> Unit,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typed = remember(text, layout) { TextTyper.type(text, layout) }
    val error = MaterialTheme.colorScheme.error
    val annotated = remember(typed, error) {
        val bad = typed.unmappable.toHashSet()
        buildAnnotatedString {
            typed.text.forEachIndexed { i, c ->
                if (i in bad) withStyle(SpanStyle(color = error)) { append(c) } else append(c)
            }
        }
    }

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.preview_chars, typed.text.length), style = MaterialTheme.typography.labelMedium)
            Text(
                annotated,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())
            )
            if (typed.unmappable.isNotEmpty()) {
                Text(
                    stringResource(R.string.preview_unmappable, typed.unmappable.size),
                    color = error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            LayoutChip(layout, onPickLayout)
            if (typing != null) {
                LinearProgressIndicator(
                    progress = { if (typing.total == 0) 0f else typing.sent.toFloat() / typing.total },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.typing_progress, typing.sent, typing.total), Modifier.weight(1f))
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.typing_cancel)) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onType, enabled = connected) { Text(stringResource(R.string.preview_type)) }
                    OutlinedButton(onClick = onCopy) { Text(stringResource(R.string.preview_copy)) }
                    TextButton(onClick = onRescan) { Text(stringResource(R.string.preview_rescan)) }
                }
            }
        }
    }
}
