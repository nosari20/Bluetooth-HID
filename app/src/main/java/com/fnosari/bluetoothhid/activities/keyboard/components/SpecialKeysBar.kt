package com.fnosari.bluetoothhid.activities.keyboard.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.connectivity.hid.HIDConsumer
import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyboardLayout

private data class KeyCap(val label: String, val usage: Int, val modifiers: Int = 0)

/** Sticky modifiers, navigation/function keys, shortcuts and media keys. */
@Composable
fun SpecialKeysBar(
    sticky: Int,
    layout: KeyboardLayout,
    onToggleSticky: (Int) -> Unit,
    onKey: (usage: Int) -> Unit,
    onShortcut: (usage: Int, modifiers: Int) -> Unit,
    onConsumer: (usage: Int) -> Unit
) {
    val isMac = layout.id == "fr-mac"
    fun letter(c: Char) = layout.strokesFor(c)?.firstOrNull()?.usage ?: HIDKeys.letter(c)
    val shortcutMod = if (isMac) HIDModifiers.L_GUI else HIDModifiers.L_CTRL
    val shortcutLabel = if (isMac) "⌘" else "Ctrl+"

    val modifiers = listOf(
        "Ctrl" to HIDModifiers.L_CTRL,
        "Shift" to HIDModifiers.L_SHIFT,
        "Alt" to HIDModifiers.L_ALT,
        "AltGr" to HIDModifiers.R_ALT,
        (if (isMac) "⌘" else "Win") to HIDModifiers.L_GUI
    )
    val nav = listOf(
        KeyCap("Esc", HIDKeys.ESCAPE), KeyCap("Tab", HIDKeys.TAB), KeyCap("↵", HIDKeys.ENTER),
        KeyCap("⌫", HIDKeys.BACKSPACE), KeyCap("Del", HIDKeys.DELETE),
        KeyCap("←", HIDKeys.LEFT), KeyCap("↑", HIDKeys.UP), KeyCap("↓", HIDKeys.DOWN), KeyCap("→", HIDKeys.RIGHT),
        KeyCap("Home", HIDKeys.HOME), KeyCap("End", HIDKeys.END),
        KeyCap("PgUp", HIDKeys.PAGE_UP), KeyCap("PgDn", HIDKeys.PAGE_DOWN),
        KeyCap("Ins", HIDKeys.INSERT), KeyCap("PrtSc", HIDKeys.PRINT_SCREEN), KeyCap("Menu", HIDKeys.MENU)
    )
    val functions = (1..12).map { KeyCap("F$it", HIDKeys.f(it)) }
    val shortcuts = listOf(
        KeyCap("${shortcutLabel}A", letter('a'), shortcutMod),
        KeyCap("${shortcutLabel}C", letter('c'), shortcutMod),
        KeyCap("${shortcutLabel}V", letter('v'), shortcutMod),
        KeyCap("${shortcutLabel}X", letter('x'), shortcutMod),
        KeyCap("${shortcutLabel}Z", letter('z'), shortcutMod),
        KeyCap(if (isMac) "⌘Tab" else "Alt+Tab", HIDKeys.TAB, if (isMac) HIDModifiers.L_GUI else HIDModifiers.L_ALT),
        KeyCap("Ctrl+Alt+Del", HIDKeys.DELETE, HIDModifiers.L_CTRL or HIDModifiers.L_ALT),
        KeyCap(if (isMac) "⌘" else "Win", 0, HIDModifiers.L_GUI)
    )
    val media = listOf<Pair<ImageVector, Int>>(
        Icons.AutoMirrored.Rounded.VolumeDown to HIDConsumer.VOLUME_DOWN,
        Icons.AutoMirrored.Rounded.VolumeUp to HIDConsumer.VOLUME_UP,
        Icons.AutoMirrored.Rounded.VolumeOff to HIDConsumer.MUTE,
        Icons.Rounded.SkipPrevious to HIDConsumer.PREVIOUS,
        Icons.Rounded.PlayArrow to HIDConsumer.PLAY_PAUSE,
        Icons.Rounded.SkipNext to HIDConsumer.NEXT
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        ScrollRow {
            for ((label, bit) in modifiers) {
                FilterChip(selected = sticky and bit != 0, onClick = { onToggleSticky(bit) }, label = { Text(label) })
            }
        }
        ScrollRow { for (k in nav) KeyButton(k.label) { onKey(k.usage) } }
        ScrollRow { for (k in functions) KeyButton(k.label) { onKey(k.usage) } }
        ScrollRow {
            for (k in shortcuts) KeyButton(k.label) { onShortcut(k.usage, k.modifiers) }
            for ((icon, usage) in media) {
                FilledTonalIconButton(onClick = { onConsumer(usage) }) { Icon(icon, contentDescription = null) }
            }
        }
    }
}

@Composable
private fun ScrollRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)
    ) { content() }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) { Text(label) }
}
