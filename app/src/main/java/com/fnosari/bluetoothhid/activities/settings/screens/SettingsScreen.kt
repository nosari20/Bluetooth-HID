package com.fnosari.bluetoothhid.activities.settings.screens

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.connectivity.hid.layouts.Layouts
import com.fnosari.bluetoothhid.model.TypingSpeed
import com.fnosari.bluetoothhid.persistance.preferences.DataStorePreferencesProvider
import com.fnosari.bluetoothhid.persistance.preferences.Preferences
import com.fnosari.bluetoothhid.ui.components.LayoutPickerDialog
import com.fnosari.bluetoothhid.ui.components.layoutLabel
import com.fnosari.bluetoothhid.ui.layouts.BasicLayoutWithTopBar
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { DataStorePreferencesProvider(context, scope) }
    val localeManager = remember { context.getSystemService(LocaleManager::class.java) }

    var speed by remember { mutableStateOf(TypingSpeed.fromName(prefs.readString(Preferences.TYPING_SPEED, TypingSpeed.NORMAL.name))) }
    var defaultLayout by remember {
        mutableStateOf(prefs.readString(Preferences.DEFAULT_LAYOUT, Layouts.defaultIdFor(Locale.getDefault().language)))
    }
    var autoType by remember { mutableStateOf(prefs.readBoolean(Preferences.AUTO_TYPE, false)) }
    var enterAtEnd by remember { mutableStateOf(prefs.readBoolean(Preferences.ENTER_AT_END, false)) }
    var autoReconnect by remember { mutableStateOf(prefs.readBoolean(Preferences.AUTO_RECONNECT, true)) }
    var presenterVibrate by remember { mutableStateOf(prefs.readBoolean(Preferences.PRESENTER_VIBRATE, true)) }
    var sensitivity by remember { mutableFloatStateOf(prefs.readDouble(Preferences.MOUSE_SENSITIVITY, 1.0).toFloat()) }
    var language by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags()) }
    var pickLayout by remember { mutableStateOf(false) }

    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }

    BasicLayoutWithTopBar(title = stringResource(R.string.settings_activity_name), leftAction = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.settings_typing))
            Text(stringResource(R.string.settings_speed), Modifier.padding(horizontal = 16.dp))
            for ((s, label) in listOf(
                TypingSpeed.FAST to R.string.speed_fast,
                TypingSpeed.NORMAL to R.string.speed_normal,
                TypingSpeed.SAFE to R.string.speed_safe
            )) {
                RadioRow(stringResource(label), selected = speed == s) {
                    speed = s
                    prefs.write(Preferences.TYPING_SPEED, s.name)
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_default_layout)) },
                supportingContent = { Text(stringResource(layoutLabel(defaultLayout))) },
                modifier = Modifier.clickable { pickLayout = true }
            )
            SwitchRow(stringResource(R.string.settings_auto_type), autoType) {
                autoType = it; prefs.write(Preferences.AUTO_TYPE, it)
            }
            SwitchRow(stringResource(R.string.settings_enter_at_end), enterAtEnd) {
                enterAtEnd = it; prefs.write(Preferences.ENTER_AT_END, it)
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_connection))
            SwitchRow(stringResource(R.string.settings_auto_reconnect), autoReconnect) {
                autoReconnect = it; prefs.write(Preferences.AUTO_RECONNECT, it)
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_mouse))
            Text(stringResource(R.string.settings_mouse_sensitivity), Modifier.padding(horizontal = 16.dp))
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                onValueChangeFinished = { prefs.write(Preferences.MOUSE_SENSITIVITY, sensitivity.toDouble()) },
                valueRange = 0.5f..3f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            HorizontalDivider()
            Section(stringResource(R.string.settings_presenter))
            SwitchRow(stringResource(R.string.settings_presenter_vibrate), presenterVibrate) {
                presenterVibrate = it; prefs.write(Preferences.PRESENTER_VIBRATE, it)
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_language))
            for ((tag, label) in listOf("" to R.string.language_system, "en" to R.string.language_en, "fr" to R.string.language_fr)) {
                RadioRow(stringResource(label), selected = language == tag) {
                    language = tag
                    localeManager.applicationLocales =
                        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                }
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_about))
            Text(stringResource(R.string.settings_version, version), Modifier.padding(16.dp))
        }
    }

    if (pickLayout) {
        LayoutPickerDialog(
            currentId = defaultLayout,
            onPick = { defaultLayout = it; prefs.write(Preferences.DEFAULT_LAYOUT, it); pickLayout = false },
            onDismiss = { pickLayout = false }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) }
    )
}
