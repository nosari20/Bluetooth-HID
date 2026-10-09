# Bluetooth HID Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android app that turns the phone into a Bluetooth keyboard + mouse: scan QR → type text on host, live keyboard with special keys, touchpad mouse.

**Architecture:** App-agnostic HID framework in `connectivity/hid/` (descriptor, report builders, paced single-thread report queue, `BluetoothHidDevice` wrapper, keyboard layouts + text→report converter). A foreground service owns the HID connection; each feature is its own Activity (ClassLock convention) that binds to the service.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Navigation Compose, DataStore Preferences, CameraX, ML Kit barcode-scanning (bundled model), Android `BluetoothHidDevice` profile, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-09-bluetooth-hid-design.md`

## Global Constraints

- Package / applicationId: `com.fnosari.bluetoothhid`. minSdk 36, targetSdk 37, compileSdk release(37), Java 11.
- AGP built-in Kotlin: only `android.application` + `kotlin.compose` plugins. No KSP, no Hilt.
- Structure (ClassLock style): `activities/<name>/{XxxActivity.kt, screens/, components/}`, `connectivity/hid/`, `model/`, `persistance/preferences/` (spelling as in ClassLock), `services/`, `ui/layouts/`, `ui/theme/`, `utils/`.
- Framework classes use the ClassLock style: `companion object { private const val LOG_TAG = "..." }`, nested `XxxCallBack` interface, `XxxExitCode` enum, KDoc on public classes.
- No network, no analytics. No hard-coded user-visible text in Kotlin: every string in `values/strings.xml` (EN) **and** `values-fr/strings.xml` (FR), key for key.
- Host layouts: `us`, `fr-win`, `fr-linux`, `fr-mac`.
- Typing speed presets (delay between reports): Fast 6 ms, Normal 10 ms, Safe 25 ms. Retry `sendReport` failure 3× (wait 2× delay), then abort.
- Report IDs: 1 keyboard (8 B), 2 mouse (5 B), 3 consumer (2 B). Descriptor never changes after v1.
- Build check for every task that touches Android code: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (run from project root, PowerShell: `.\gradlew.bat`).
- Commits: Conventional Commits, end message with the session attribution trailer lines.

## Review Focus

1. **Repeated characters** (`aa`, `11`, `!!` in passwords) — must type every repeat; host must see a release between identical keys. → TextTyper test `repeatedCharsReleaseBetween` (Task 7).
2. **Case / AltGr transitions** (`aA@a`, `a@` on FR) — no char typed with the wrong modifier. → TextTyper test `modifierChangeInsertsRelease` (Task 7).
3. **QR payload line endings** (`\r\n`, lone `\r`, trailing newline) — exactly one Enter per line break. → TextTyper test `crlfBecomesSingleEnter` (Task 7).
4. **Link failure mid-typing** (`sendReport` returns false, host disconnects) — typing stops, all keys released, failure position reported so user can resume. → Queue test `failureAbortsReleasesAndReportsIndex` (Task 8).
5. **Cancel during long payload** (2 000+ chars) — stops promptly, never leaves a key or modifier held. → Queue test `cancelStopsAndReleases` (Task 8).

## File Map

```
app/src/main/java/com/fnosari/bluetoothhid/
├─ BluetoothHidApp.kt                         (Task 1) Application: notification channel
├─ activities/main/MainActivity.kt            (Task 13)
├─ activities/main/screens/HomeScreen.kt      (Task 13)
├─ activities/main/screens/DevicesScreen.kt   (Task 13)
├─ activities/main/components/PermissionsGate.kt (Task 13)
├─ activities/scan/ScanActivity.kt            (Task 14)
├─ activities/scan/screens/ScanScreen.kt      (Task 14)
├─ activities/scan/components/QrAnalyzer.kt   (Task 14)
├─ activities/scan/components/PreviewSheet.kt (Task 14)
├─ activities/keyboard/KeyboardActivity.kt    (Task 15)
├─ activities/keyboard/screens/KeyboardScreen.kt (Task 15)
├─ activities/keyboard/components/SpecialKeysBar.kt (Task 15)
├─ activities/mouse/MouseActivity.kt          (Task 16)
├─ activities/mouse/screens/TouchpadScreen.kt (Task 16)
├─ activities/settings/SettingsActivity.kt    (Task 17)
├─ activities/settings/screens/SettingsScreen.kt (Task 17)
├─ connectivity/hid/HIDKeys.kt                (Task 2) usage + modifier constants
├─ connectivity/hid/HIDReport.kt              (Task 2)
├─ connectivity/hid/HIDDescriptor.kt          (Task 3)
├─ connectivity/hid/layouts/KeyboardLayout.kt (Task 4)
├─ connectivity/hid/layouts/UsLayout.kt       (Task 4)
├─ connectivity/hid/layouts/FrAzertyLayout.kt (Task 5)
├─ connectivity/hid/layouts/FrMacLayout.kt    (Task 6)
├─ connectivity/hid/layouts/Layouts.kt        (Task 6) registry by id
├─ connectivity/hid/layouts/TextTyper.kt      (Task 7)
├─ connectivity/hid/HIDReportQueue.kt         (Task 8)
├─ connectivity/hid/HIDException.kt           (Task 11)
├─ connectivity/hid/HIDDevice.kt              (Task 11)
├─ model/Host.kt, model/TypingSpeed.kt        (Task 10)
├─ persistance/preferences/PreferencesProvider.kt, DataStorePreferencesProvider.kt, Preferences.kt (Task 10)
├─ services/HIDForegroundService.kt           (Task 12)
├─ services/HIDState.kt                       (Task 12)
├─ ui/layouts/BasicLayoutWithTopBar.kt        (Task 13)
├─ ui/components/HIDUiState.kt, ConnectionBanner.kt, LayoutPicker.kt (Task 13) shared by activities
├─ ui/theme/*                                 (existing)
├─ utils/TextDiff.kt                          (Task 9)
├─ utils/PointerMath.kt                       (Task 9)
└─ utils/HIDServiceBinding.kt                 (Task 12)
app/src/test/java/com/fnosari/bluetoothhid/   mirrors the above for pure-Kotlin units
```

---

### Task 1: Project setup (dependencies, manifest, package layout, strings)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-fr/strings.xml`, `app/src/main/res/xml/locales_config.xml`, `app/src/main/java/com/fnosari/bluetoothhid/BluetoothHidApp.kt`
- Move: `app/src/main/java/com/fnosari/bluetoothhid/MainActivity.kt` → `activities/main/MainActivity.kt`
- Delete: `app/src/test/java/com/fnosari/bluetoothhid/ExampleUnitTest.kt`, `app/src/androidTest/java/com/fnosari/bluetoothhid/ExampleInstrumentedTest.kt`

**Interfaces:**
- Produces: `BluetoothHidApp.NOTIFICATION_CHANNEL_ID = "hid_connection"`; activity classes registered in manifest: `.activities.main.MainActivity`, `.activities.scan.ScanActivity`, `.activities.keyboard.KeyboardActivity`, `.activities.mouse.MouseActivity`, `.activities.settings.SettingsActivity`; service `.services.HIDForegroundService`. Activities other than Main are created in later tasks — in this task create each as an empty stub (code below) so the manifest compiles.

- [ ] **Step 1: Add versions and libraries to `gradle/libs.versions.toml`**

Under `[versions]` add:
```toml
cameraX = "1.5.0"
mlkitBarcode = "17.3.0"
navigationCompose = "2.9.4"
datastore = "1.1.7"
```
Under `[libraries]` add:
```toml
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "cameraX" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "cameraX" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "cameraX" }
mlkit-barcode-scanning = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkitBarcode" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
```
If `material-icons-extended` does not resolve from the BOM, pin it: `version = "1.7.8"`.

- [ ] **Step 2: Add dependencies in `app/build.gradle.kts`**

In `dependencies { }` after `implementation(libs.androidx.lifecycle.runtime.ktx)` add:
```kotlin
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
```

- [ ] **Step 3: Replace `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="android.hardware.bluetooth" android:required="true" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />

    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".BluetoothHidApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:localeConfig="@xml/locales_config"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.BluetoothHID">
        <activity
            android:name=".activities.main.MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity
            android:name=".activities.scan.ScanActivity"
            android:exported="false"
            android:label="@string/scan_activity_name" />
        <activity
            android:name=".activities.keyboard.KeyboardActivity"
            android:exported="false"
            android:label="@string/keyboard_activity_name"
            android:windowSoftInputMode="adjustResize|stateAlwaysVisible" />
        <activity
            android:name=".activities.mouse.MouseActivity"
            android:exported="false"
            android:label="@string/mouse_activity_name" />
        <activity
            android:name=".activities.settings.SettingsActivity"
            android:exported="false"
            android:label="@string/settings_activity_name" />

        <service
            android:name=".services.HIDForegroundService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />
    </application>

</manifest>
```

- [ ] **Step 4: Create `app/src/main/res/xml/locales_config.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="fr" />
</locale-config>
```

- [ ] **Step 5: Write full string tables (all later tasks use these keys)**

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Bluetooth HID</string>
    <string name="scan_activity_name">Scan</string>
    <string name="keyboard_activity_name">Keyboard</string>
    <string name="mouse_activity_name">Mouse</string>
    <string name="settings_activity_name">Settings</string>
    <string name="go_back">Go back</string>

    <!-- Notification -->
    <string name="notification_channel_name">Connection</string>
    <string name="notification_idle">Waiting for a host</string>
    <string name="notification_connected">Connected to %1$s</string>
    <string name="notification_disconnect">Disconnect</string>
    <string name="notification_quit">Quit</string>

    <!-- Connection state -->
    <string name="state_starting">Starting…</string>
    <string name="state_idle">Not connected</string>
    <string name="state_connecting">Connecting to %1$s…</string>
    <string name="state_connected">Connected to %1$s</string>
    <string name="error_bluetooth_not_supported">This device has no Bluetooth.</string>
    <string name="error_bluetooth_not_enabled">Bluetooth is off.</string>
    <string name="error_permission">Bluetooth permission not granted.</string>
    <string name="error_hid_not_supported">This phone does not support the Bluetooth HID device profile.</string>
    <string name="error_hid_register_failed">Could not register as keyboard. Close other keyboard/mouse emulator apps and retry.</string>
    <string name="error_hid_not_connected">No host connected.</string>
    <string name="error_hid_send_failed">Sending to host failed.</string>
    <string name="action_enable_bluetooth">Turn on Bluetooth</string>
    <string name="action_retry">Retry</string>
    <string name="action_open_settings">Open app settings</string>

    <!-- Permissions -->
    <string name="permissions_title">Permissions needed</string>
    <string name="permissions_text">Bluetooth permission lets the app act as a keyboard and mouse for your computer. Notifications show the connection status.</string>
    <string name="permissions_grant">Grant</string>

    <!-- Home -->
    <string name="home_tile_scan">Scan &amp; type</string>
    <string name="home_tile_keyboard">Keyboard</string>
    <string name="home_tile_mouse">Mouse</string>
    <string name="home_tile_settings">Settings</string>
    <string name="home_devices">Hosts</string>
    <string name="home_disconnect">Disconnect</string>
    <string name="home_layout">Host layout: %1$s</string>
    <string name="banner_connect_host">Connect a host first</string>

    <!-- Devices -->
    <string name="devices_title">Hosts</string>
    <string name="devices_paired">Paired devices</string>
    <string name="devices_empty">No paired device yet.</string>
    <string name="devices_connect">Connect</string>
    <string name="devices_pair_new">Pair new host</string>
    <string name="devices_pair_help">Your phone is now visible for 2 minutes. On the computer open Bluetooth settings, choose “Add device” and select this phone. Windows: Settings › Bluetooth &amp; devices › Add device. macOS: System Settings › Bluetooth. Linux: Bluetooth settings.</string>

    <!-- Layouts -->
    <string name="layout_us">US (QWERTY)</string>
    <string name="layout_fr_win">French AZERTY (Windows)</string>
    <string name="layout_fr_linux">French AZERTY (Linux)</string>
    <string name="layout_fr_mac">French AZERTY (Mac)</string>
    <string name="layout_choose">Host keyboard layout</string>

    <!-- Scan -->
    <string name="scan_camera_permission">Camera permission is needed to scan codes.</string>
    <string name="scan_hint">Point the camera at a QR code or barcode</string>
    <string name="preview_title">Scanned text</string>
    <string name="preview_chars">%1$d characters</string>
    <string name="preview_unmappable">%1$d characters cannot be typed with this layout and will be skipped (in red).</string>
    <string name="preview_type">Type</string>
    <string name="preview_copy">Copy</string>
    <string name="preview_rescan">Rescan</string>
    <string name="preview_copied">Copied</string>
    <string name="typing_progress">Typing %1$d / %2$d</string>
    <string name="typing_cancel">Cancel</string>
    <string name="typing_done">Done</string>
    <string name="typing_cancelled">Typing cancelled</string>
    <string name="typing_interrupted">Typing interrupted at character %1$d</string>
    <string name="typing_resume">Resume</string>

    <!-- Keyboard -->
    <string name="keyboard_live">Live</string>
    <string name="keyboard_block">Block</string>
    <string name="keyboard_hint_live">Type here, keys go straight to the host</string>
    <string name="keyboard_hint_block">Write text, then press Send</string>
    <string name="keyboard_send">Send</string>
    <string name="keyboard_clear">Clear</string>

    <!-- Mouse -->
    <string name="mouse_left">Left</string>
    <string name="mouse_right">Right</string>
    <string name="mouse_hint">1 finger: move · tap: click · 2 fingers: scroll / right click · long press: drag</string>

    <!-- Settings -->
    <string name="settings_typing">Typing</string>
    <string name="settings_speed">Typing speed</string>
    <string name="speed_fast">Fast</string>
    <string name="speed_normal">Normal</string>
    <string name="speed_safe">Safe</string>
    <string name="settings_default_layout">Default layout for new hosts</string>
    <string name="settings_auto_type">Type automatically after scan</string>
    <string name="settings_enter_at_end">Press Enter after scanned text</string>
    <string name="settings_connection">Connection</string>
    <string name="settings_auto_reconnect">Reconnect last host on start</string>
    <string name="settings_mouse">Mouse</string>
    <string name="settings_mouse_sensitivity">Pointer sensitivity</string>
    <string name="settings_language">Language</string>
    <string name="language_system">System default</string>
    <string name="language_en">English</string>
    <string name="language_fr">Français</string>
    <string name="settings_about">About</string>
    <string name="settings_version">Version %1$s</string>
</resources>
```

`app/src/main/res/values-fr/strings.xml`:
```xml
<resources>
    <string name="app_name">Bluetooth HID</string>
    <string name="scan_activity_name">Scanner</string>
    <string name="keyboard_activity_name">Clavier</string>
    <string name="mouse_activity_name">Souris</string>
    <string name="settings_activity_name">Paramètres</string>
    <string name="go_back">Retour</string>

    <string name="notification_channel_name">Connexion</string>
    <string name="notification_idle">En attente d’un hôte</string>
    <string name="notification_connected">Connecté à %1$s</string>
    <string name="notification_disconnect">Déconnecter</string>
    <string name="notification_quit">Quitter</string>

    <string name="state_starting">Démarrage…</string>
    <string name="state_idle">Non connecté</string>
    <string name="state_connecting">Connexion à %1$s…</string>
    <string name="state_connected">Connecté à %1$s</string>
    <string name="error_bluetooth_not_supported">Cet appareil n’a pas de Bluetooth.</string>
    <string name="error_bluetooth_not_enabled">Le Bluetooth est désactivé.</string>
    <string name="error_permission">Autorisation Bluetooth refusée.</string>
    <string name="error_hid_not_supported">Ce téléphone ne prend pas en charge le profil Bluetooth HID.</string>
    <string name="error_hid_register_failed">Impossible de s’enregistrer comme clavier. Fermez les autres applis clavier/souris et réessayez.</string>
    <string name="error_hid_not_connected">Aucun hôte connecté.</string>
    <string name="error_hid_send_failed">L’envoi vers l’hôte a échoué.</string>
    <string name="action_enable_bluetooth">Activer le Bluetooth</string>
    <string name="action_retry">Réessayer</string>
    <string name="action_open_settings">Ouvrir les paramètres de l’appli</string>

    <string name="permissions_title">Autorisations nécessaires</string>
    <string name="permissions_text">L’autorisation Bluetooth permet à l’appli de servir de clavier et de souris pour votre ordinateur. Les notifications affichent l’état de la connexion.</string>
    <string name="permissions_grant">Autoriser</string>

    <string name="home_tile_scan">Scanner et taper</string>
    <string name="home_tile_keyboard">Clavier</string>
    <string name="home_tile_mouse">Souris</string>
    <string name="home_tile_settings">Paramètres</string>
    <string name="home_devices">Hôtes</string>
    <string name="home_disconnect">Déconnecter</string>
    <string name="home_layout">Disposition de l’hôte : %1$s</string>
    <string name="banner_connect_host">Connectez d’abord un hôte</string>

    <string name="devices_title">Hôtes</string>
    <string name="devices_paired">Appareils associés</string>
    <string name="devices_empty">Aucun appareil associé.</string>
    <string name="devices_connect">Connecter</string>
    <string name="devices_pair_new">Associer un nouvel hôte</string>
    <string name="devices_pair_help">Votre téléphone est visible pendant 2 minutes. Sur l’ordinateur, ouvrez les paramètres Bluetooth, choisissez « Ajouter un appareil » et sélectionnez ce téléphone. Windows : Paramètres › Bluetooth et appareils › Ajouter un appareil. macOS : Réglages Système › Bluetooth. Linux : paramètres Bluetooth.</string>

    <string name="layout_us">US (QWERTY)</string>
    <string name="layout_fr_win">Français AZERTY (Windows)</string>
    <string name="layout_fr_linux">Français AZERTY (Linux)</string>
    <string name="layout_fr_mac">Français AZERTY (Mac)</string>
    <string name="layout_choose">Disposition clavier de l’hôte</string>

    <string name="scan_camera_permission">L’autorisation caméra est nécessaire pour scanner.</string>
    <string name="scan_hint">Visez un QR code ou un code-barres</string>
    <string name="preview_title">Texte scanné</string>
    <string name="preview_chars">%1$d caractères</string>
    <string name="preview_unmappable">%1$d caractères ne peuvent pas être tapés avec cette disposition et seront ignorés (en rouge).</string>
    <string name="preview_type">Taper</string>
    <string name="preview_copy">Copier</string>
    <string name="preview_rescan">Rescanner</string>
    <string name="preview_copied">Copié</string>
    <string name="typing_progress">Saisie %1$d / %2$d</string>
    <string name="typing_cancel">Annuler</string>
    <string name="typing_done">Terminé</string>
    <string name="typing_cancelled">Saisie annulée</string>
    <string name="typing_interrupted">Saisie interrompue au caractère %1$d</string>
    <string name="typing_resume">Reprendre</string>

    <string name="keyboard_live">Direct</string>
    <string name="keyboard_block">Bloc</string>
    <string name="keyboard_hint_live">Tapez ici, les touches partent directement vers l’hôte</string>
    <string name="keyboard_hint_block">Écrivez le texte puis appuyez sur Envoyer</string>
    <string name="keyboard_send">Envoyer</string>
    <string name="keyboard_clear">Effacer</string>

    <string name="mouse_left">Gauche</string>
    <string name="mouse_right">Droit</string>
    <string name="mouse_hint">1 doigt : déplacer · appui : clic · 2 doigts : défiler / clic droit · appui long : glisser</string>

    <string name="settings_typing">Saisie</string>
    <string name="settings_speed">Vitesse de saisie</string>
    <string name="speed_fast">Rapide</string>
    <string name="speed_normal">Normale</string>
    <string name="speed_safe">Sûre</string>
    <string name="settings_default_layout">Disposition par défaut des nouveaux hôtes</string>
    <string name="settings_auto_type">Taper automatiquement après le scan</string>
    <string name="settings_enter_at_end">Appuyer sur Entrée après le texte scanné</string>
    <string name="settings_connection">Connexion</string>
    <string name="settings_auto_reconnect">Reconnecter le dernier hôte au démarrage</string>
    <string name="settings_mouse">Souris</string>
    <string name="settings_mouse_sensitivity">Sensibilité du pointeur</string>
    <string name="settings_language">Langue</string>
    <string name="language_system">Langue du système</string>
    <string name="language_en">English</string>
    <string name="language_fr">Français</string>
    <string name="settings_about">À propos</string>
    <string name="settings_version">Version %1$s</string>
</resources>
```

- [ ] **Step 6: Create `BluetoothHidApp.kt`**

```kotlin
package com.fnosari.bluetoothhid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

/**
 * Application entry point. Creates the notification channel used by the
 * HID foreground service.
 */
class BluetoothHidApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "hid_connection"
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
```

- [ ] **Step 7: Move MainActivity and create stubs**

Delete `app/src/main/java/com/fnosari/bluetoothhid/MainActivity.kt`, `app/src/test/java/com/fnosari/bluetoothhid/ExampleUnitTest.kt`, `app/src/androidTest/java/com/fnosari/bluetoothhid/ExampleInstrumentedTest.kt`.

Create five stubs, each in its package (`activities/main/MainActivity.kt`, `activities/scan/ScanActivity.kt`, `activities/keyboard/KeyboardActivity.kt`, `activities/mouse/MouseActivity.kt`, `activities/settings/SettingsActivity.kt`). Template (replace `main`/`MainActivity` per file):
```kotlin
package com.fnosari.bluetoothhid.activities.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BluetoothHIDTheme { } }
    }
}
```
Create the service stub `services/HIDForegroundService.kt`:
```kotlin
package com.fnosari.bluetoothhid.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HIDForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 8: Build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If a dependency fails to resolve, fix its version in the toml (check https://maven.google.com) and note it in the commit body.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "chore: add dependencies, manifest, activity skeleton and EN/FR strings"
```

---

### Task 2: HID usage constants and report builders

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDKeys.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDReport.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/HIDReportTest.kt`

**Interfaces:**
- Produces:
  - `object HIDKeys` with `Int` constants: `A`..`Z` via `letter(c: Char): Int`, `digit(d: Int): Int` (0..9), `ENTER=0x28, ESCAPE=0x29, BACKSPACE=0x2A, TAB=0x2B, SPACE=0x2C, MINUS=0x2D, EQUAL=0x2E, LEFT_BRACKET=0x2F, RIGHT_BRACKET=0x30, BACKSLASH=0x31, NON_US_HASH=0x32, SEMICOLON=0x33, QUOTE=0x34, GRAVE=0x35, COMMA=0x36, PERIOD=0x37, SLASH=0x38, CAPS_LOCK=0x39, F1=0x3A` (`f(n)` for F1–F12), `PRINT_SCREEN=0x46, INSERT=0x49, HOME=0x4A, PAGE_UP=0x4B, DELETE=0x4C, END=0x4D, PAGE_DOWN=0x4E, RIGHT=0x4F, LEFT=0x50, DOWN=0x51, UP=0x52, NON_US_BACKSLASH=0x64, MENU=0x65`.
  - `object HIDModifiers { L_CTRL=0x01, L_SHIFT=0x02, L_ALT=0x04, L_GUI=0x08, R_CTRL=0x10, R_SHIFT=0x20, R_ALT=0x40, R_GUI=0x80, NONE=0 }`
  - `object HIDConsumer { MUTE=0xE2, VOLUME_UP=0xE9, VOLUME_DOWN=0xEA, PLAY_PAUSE=0xCD, NEXT=0xB5, PREVIOUS=0xB6 }`
  - `object HIDMouseButtons { LEFT=1, RIGHT=2, MIDDLE=4 }`
  - `sealed class HIDReport { abstract val id: Int; abstract fun toBytes(): ByteArray }` with
    `data class Keyboard(val modifiers: Int = 0, val keys: List<Int> = emptyList())` (id 1, 8 bytes, `companion val RELEASE = Keyboard()`),
    `data class Mouse(val buttons: Int = 0, val dx: Int = 0, val dy: Int = 0, val wheel: Int = 0, val pan: Int = 0)` (id 2, 5 bytes, values clamped to −127..127),
    `data class Consumer(val usage: Int = 0)` (id 3, 2 bytes little-endian, `companion val RELEASE = Consumer()`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HIDReportTest {

    @Test
    fun keyboardReportLayout() {
        val r = HIDReport.Keyboard(HIDModifiers.L_SHIFT, listOf(HIDKeys.letter('a')))
        assertEquals(1, r.id)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0, 0, 0, 0, 0), r.toBytes())
    }

    @Test
    fun keyboardReleaseIsAllZero() {
        assertArrayEquals(ByteArray(8), HIDReport.Keyboard.RELEASE.toBytes())
    }

    @Test
    fun keyboardKeepsAtMostSixKeys() {
        val r = HIDReport.Keyboard(0, (4..11).toList())
        assertArrayEquals(byteArrayOf(0, 0, 4, 5, 6, 7, 8, 9), r.toBytes())
    }

    @Test
    fun mouseReportClampsAndSigns() {
        val r = HIDReport.Mouse(HIDMouseButtons.LEFT, dx = -5, dy = 300, wheel = -200, pan = 1)
        assertEquals(2, r.id)
        assertArrayEquals(byteArrayOf(1, -5, 127, -127, 1), r.toBytes())
    }

    @Test
    fun consumerReportLittleEndian() {
        val r = HIDReport.Consumer(HIDConsumer.VOLUME_UP)
        assertEquals(3, r.id)
        assertArrayEquals(byteArrayOf(0xE9.toByte(), 0x00), r.toBytes())
    }

    @Test
    fun keyHelpers() {
        assertEquals(0x04, HIDKeys.letter('a'))
        assertEquals(0x1D, HIDKeys.letter('Z'))
        assertEquals(0x1E, HIDKeys.digit(1))
        assertEquals(0x27, HIDKeys.digit(0))
        assertEquals(0x3A, HIDKeys.f(1))
        assertEquals(0x45, HIDKeys.f(12))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*HIDReportTest"`
Expected: compilation FAIL (`Unresolved reference: HIDReport`).

- [ ] **Step 3: Implement `HIDKeys.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

/**
 * USB HID usage IDs (Keyboard/Keypad page 0x07). Names follow the US key
 * position, whatever character the host layout prints for that key.
 */
object HIDKeys {
    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val MINUS = 0x2D
    const val EQUAL = 0x2E
    const val LEFT_BRACKET = 0x2F
    const val RIGHT_BRACKET = 0x30
    const val BACKSLASH = 0x31
    const val NON_US_HASH = 0x32
    const val SEMICOLON = 0x33
    const val QUOTE = 0x34
    const val GRAVE = 0x35
    const val COMMA = 0x36
    const val PERIOD = 0x37
    const val SLASH = 0x38
    const val CAPS_LOCK = 0x39
    const val F1 = 0x3A
    const val PRINT_SCREEN = 0x46
    const val INSERT = 0x49
    const val HOME = 0x4A
    const val PAGE_UP = 0x4B
    const val DELETE = 0x4C
    const val END = 0x4D
    const val PAGE_DOWN = 0x4E
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
    const val NON_US_BACKSLASH = 0x64
    const val MENU = 0x65

    /** Usage of the key at US position of letter [c] (case ignored). */
    fun letter(c: Char): Int = 0x04 + (c.lowercaseChar() - 'a')

    /** Usage of the top-row digit key [d] (0..9) at US position. */
    fun digit(d: Int): Int = if (d == 0) 0x27 else 0x1E + d - 1

    /** Usage of function key F[n] (1..12). */
    fun f(n: Int): Int = F1 + n - 1
}

/** Modifier bits of byte 0 of the keyboard report. */
object HIDModifiers {
    const val NONE = 0
    const val L_CTRL = 0x01
    const val L_SHIFT = 0x02
    const val L_ALT = 0x04
    const val L_GUI = 0x08
    const val R_CTRL = 0x10
    const val R_SHIFT = 0x20
    const val R_ALT = 0x40
    const val R_GUI = 0x80
}

/** Consumer page (0x0C) usages. */
object HIDConsumer {
    const val NEXT = 0xB5
    const val PREVIOUS = 0xB6
    const val PLAY_PAUSE = 0xCD
    const val MUTE = 0xE2
    const val VOLUME_UP = 0xE9
    const val VOLUME_DOWN = 0xEA
}

/** Mouse button bits. */
object HIDMouseButtons {
    const val LEFT = 1
    const val RIGHT = 2
    const val MIDDLE = 4
}
```

- [ ] **Step 4: Implement `HIDReport.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

/**
 * One input report, matching [HIDDescriptor]. [toBytes] returns the payload
 * without the report ID (BluetoothHidDevice.sendReport takes the ID apart).
 */
sealed class HIDReport {
    abstract val id: Int
    abstract fun toBytes(): ByteArray

    /** Report ID 1: modifiers, reserved, up to 6 pressed key usages. */
    data class Keyboard(val modifiers: Int = 0, val keys: List<Int> = emptyList()) : HIDReport() {
        override val id: Int get() = ID

        override fun toBytes(): ByteArray {
            val bytes = ByteArray(8)
            bytes[0] = modifiers.toByte()
            keys.take(6).forEachIndexed { i, k -> bytes[2 + i] = k.toByte() }
            return bytes
        }

        companion object {
            const val ID = 1
            val RELEASE = Keyboard()
        }
    }

    /** Report ID 2: buttons, relative X/Y, wheel, horizontal pan. */
    data class Mouse(
        val buttons: Int = 0,
        val dx: Int = 0,
        val dy: Int = 0,
        val wheel: Int = 0,
        val pan: Int = 0
    ) : HIDReport() {
        override val id: Int get() = ID

        override fun toBytes(): ByteArray = byteArrayOf(
            (buttons and 0x07).toByte(),
            clamp(dx).toByte(),
            clamp(dy).toByte(),
            clamp(wheel).toByte(),
            clamp(pan).toByte()
        )

        companion object {
            const val ID = 2
            fun clamp(v: Int): Int = v.coerceIn(-127, 127)
        }
    }

    /** Report ID 3: one 16-bit consumer usage (0 = released). */
    data class Consumer(val usage: Int = 0) : HIDReport() {
        override val id: Int get() = ID

        override fun toBytes(): ByteArray =
            byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())

        companion object {
            const val ID = 3
            val RELEASE = Consumer()
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*HIDReportTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid
git commit -m "feat(hid): add HID usage constants and report builders"
```

---

### Task 3: Composite HID report descriptor

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDDescriptor.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/HIDDescriptorTest.kt`

**Interfaces:**
- Consumes: `HIDReport.Keyboard.ID`, `HIDReport.Mouse.ID`, `HIDReport.Consumer.ID` (Task 2).
- Produces: `object HIDDescriptor { val BYTES: ByteArray }`.

- [ ] **Step 1: Write the failing test**

The test walks the descriptor as HID short items, checks collections balance, the three report IDs appear in order, and the total input bits per report ID equal the payload sizes from Task 2.

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

import org.junit.Assert.assertEquals
import org.junit.Test

class HIDDescriptorTest {

    private data class Item(val tag: Int, val data: Int)

    /** Parse short items: prefix byte = tag(4) type(2) size(2). */
    private fun items(bytes: ByteArray): List<Item> {
        val out = mutableListOf<Item>()
        var i = 0
        while (i < bytes.size) {
            val prefix = bytes[i].toInt() and 0xFF
            val size = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            var data = 0
            for (b in 0 until size) data = data or ((bytes[i + 1 + b].toInt() and 0xFF) shl (8 * b))
            out += Item(prefix and 0xFC, data)
            i += 1 + size
        }
        return out
    }

    @Test
    fun collectionsAreBalanced() {
        val its = items(HIDDescriptor.BYTES)
        val open = its.count { it.tag == 0xA0 }
        val close = its.count { it.tag == 0xC0 }
        assertEquals(open, close)
    }

    @Test
    fun reportIdsInOrder() {
        val ids = items(HIDDescriptor.BYTES).filter { it.tag == 0x84 }.map { it.data }
        assertEquals(listOf(HIDReport.Keyboard.ID, HIDReport.Mouse.ID, HIDReport.Consumer.ID), ids)
    }

    @Test
    fun inputSizesMatchReports() {
        val bits = mutableMapOf<Int, Int>()
        var id = 0; var size = 0; var count = 0
        for (it in items(HIDDescriptor.BYTES)) {
            when (it.tag) {
                0x84 -> id = it.data
                0x74 -> size = it.data
                0x94 -> count = it.data
                0x80 -> bits[id] = (bits[id] ?: 0) + size * count
            }
        }
        assertEquals(8 * 8, bits[HIDReport.Keyboard.ID])
        assertEquals(5 * 8, bits[HIDReport.Mouse.ID])
        assertEquals(2 * 8, bits[HIDReport.Consumer.ID])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*HIDDescriptorTest"`
Expected: compilation FAIL (`Unresolved reference: HIDDescriptor`).

- [ ] **Step 3: Implement `HIDDescriptor.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

/**
 * Composite HID report descriptor: keyboard (ID 1), mouse with wheel and
 * horizontal pan (ID 2), consumer control (ID 3).
 *
 * Never change this after release: hosts cache it at pairing time.
 */
object HIDDescriptor {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    val BYTES: ByteArray = bytes(
        // ---- Keyboard ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1, 0x01,       // Collection (Application)
        0x85, 0x01,       //   Report ID (1)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,       //   Usage Minimum (Left Control)
        0x29, 0xE7,       //   Usage Maximum (Right GUI)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x08,       //   Report Count (8)
        0x81, 0x02,       //   Input (Data, Var, Abs) — modifiers
        0x75, 0x08,       //   Report Size (8)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x01,       //   Input (Const) — reserved
        0x05, 0x08,       //   Usage Page (LEDs)
        0x19, 0x01,       //   Usage Minimum (Num Lock)
        0x29, 0x05,       //   Usage Maximum (Kana)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x05,       //   Report Count (5)
        0x91, 0x02,       //   Output (Data, Var, Abs) — LEDs
        0x75, 0x03,       //   Report Size (3)
        0x95, 0x01,       //   Report Count (1)
        0x91, 0x01,       //   Output (Const) — padding
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,       //   Usage Minimum (0)
        0x29, 0x65,       //   Usage Maximum (Application/Menu)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x65,       //   Logical Maximum (101)
        0x75, 0x08,       //   Report Size (8)
        0x95, 0x06,       //   Report Count (6)
        0x81, 0x00,       //   Input (Data, Array) — keys
        0xC0,             // End Collection

        // ---- Mouse ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x02,       // Usage (Mouse)
        0xA1, 0x01,       // Collection (Application)
        0x85, 0x02,       //   Report ID (2)
        0x09, 0x01,       //   Usage (Pointer)
        0xA1, 0x00,       //   Collection (Physical)
        0x05, 0x09,       //     Usage Page (Buttons)
        0x19, 0x01,       //     Usage Minimum (1)
        0x29, 0x03,       //     Usage Maximum (3)
        0x15, 0x00,       //     Logical Minimum (0)
        0x25, 0x01,       //     Logical Maximum (1)
        0x75, 0x01,       //     Report Size (1)
        0x95, 0x03,       //     Report Count (3)
        0x81, 0x02,       //     Input (Data, Var, Abs) — buttons
        0x75, 0x05,       //     Report Size (5)
        0x95, 0x01,       //     Report Count (1)
        0x81, 0x01,       //     Input (Const) — padding
        0x05, 0x01,       //     Usage Page (Generic Desktop)
        0x09, 0x30,       //     Usage (X)
        0x09, 0x31,       //     Usage (Y)
        0x09, 0x38,       //     Usage (Wheel)
        0x15, 0x81,       //     Logical Minimum (-127)
        0x25, 0x7F,       //     Logical Maximum (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x03,       //     Report Count (3)
        0x81, 0x06,       //     Input (Data, Var, Rel)
        0x05, 0x0C,       //     Usage Page (Consumer)
        0x0A, 0x38, 0x02, //     Usage (AC Pan)
        0x15, 0x81,       //     Logical Minimum (-127)
        0x25, 0x7F,       //     Logical Maximum (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x01,       //     Report Count (1)
        0x81, 0x06,       //     Input (Data, Var, Rel)
        0xC0,             //   End Collection
        0xC0,             // End Collection

        // ---- Consumer control ----
        0x05, 0x0C,       // Usage Page (Consumer)
        0x09, 0x01,       // Usage (Consumer Control)
        0xA1, 0x01,       // Collection (Application)
        0x85, 0x03,       //   Report ID (3)
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x03, //   Logical Maximum (1023)
        0x19, 0x00,       //   Usage Minimum (0)
        0x2A, 0xFF, 0x03, //   Usage Maximum (1023)
        0x75, 0x10,       //   Report Size (16)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x00,       //   Input (Data, Array)
        0xC0              // End Collection
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*HIDDescriptorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDDescriptor.kt app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/HIDDescriptorTest.kt
git commit -m "feat(hid): add composite keyboard/mouse/consumer report descriptor"
```

---

### Task 4: Keyboard layout abstraction + US layout

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/KeyboardLayout.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/UsLayout.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/UsLayoutTest.kt`

**Interfaces:**
- Consumes: `HIDKeys`, `HIDModifiers` (Task 2).
- Produces:
  - `data class KeyStroke(val usage: Int, val modifiers: Int = 0)`
  - `interface KeyboardLayout { val id: String; fun strokesFor(c: Char): List<KeyStroke>? }` — `null` = unmappable; a list longer than 1 = dead-key sequence.
  - `abstract class TableKeyboardLayout(override val id: String) : KeyboardLayout` with protected helpers `key(c, usage, mods = 0)`, `seq(c, vararg strokes)`, `pair(usage, normal, shifted)`.
  - `object UsLayout : TableKeyboardLayout("us")`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UsLayoutTest {

    private fun one(c: Char) = UsLayout.strokesFor(c)!!.single()

    @Test
    fun everyPrintableAsciiIsMappable() {
        for (code in 32..126) assertNotNull("char ${code.toChar()}", UsLayout.strokesFor(code.toChar()))
    }

    @Test
    fun lettersAndCase() {
        assertEquals(KeyStroke(HIDKeys.letter('a')), one('a'))
        assertEquals(KeyStroke(HIDKeys.letter('a'), HIDModifiers.L_SHIFT), one('A'))
    }

    @Test
    fun digitsAndSymbols() {
        assertEquals(KeyStroke(HIDKeys.digit(1)), one('1'))
        assertEquals(KeyStroke(HIDKeys.digit(0)), one('0'))
        assertEquals(KeyStroke(HIDKeys.digit(2), HIDModifiers.L_SHIFT), one('@'))
        assertEquals(KeyStroke(HIDKeys.SLASH, HIDModifiers.L_SHIFT), one('?'))
        assertEquals(KeyStroke(HIDKeys.GRAVE, HIDModifiers.L_SHIFT), one('~'))
    }

    @Test
    fun whitespace() {
        assertEquals(KeyStroke(HIDKeys.SPACE), one(' '))
        assertEquals(KeyStroke(HIDKeys.ENTER), one('\n'))
        assertEquals(KeyStroke(HIDKeys.TAB), one('\t'))
    }

    @Test
    fun nonAsciiIsUnmappable() {
        assertNull(UsLayout.strokesFor('é'))
        assertNull(UsLayout.strokesFor('☃'))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*UsLayoutTest"`
Expected: compilation FAIL (`Unresolved reference: UsLayout`).

- [ ] **Step 3: Implement `KeyboardLayout.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers

/** One key press: key usage plus modifier bits held with it. */
data class KeyStroke(val usage: Int, val modifiers: Int = 0)

/**
 * Maps characters to key strokes for a given host keyboard layout.
 * HID sends key positions; the host layout decides which character appears.
 */
interface KeyboardLayout {
    val id: String

    /**
     * @return strokes to type [c] in order (more than one for dead-key
     * sequences), or null when the layout cannot type it.
     */
    fun strokesFor(c: Char): List<KeyStroke>?
}

/** Layout backed by a char → strokes table filled by subclasses in `init`. */
abstract class TableKeyboardLayout(override val id: String) : KeyboardLayout {

    private val table = HashMap<Char, List<KeyStroke>>()

    init {
        key(' ', HIDKeys.SPACE)
        key('\n', HIDKeys.ENTER)
        key('\t', HIDKeys.TAB)
    }

    override fun strokesFor(c: Char): List<KeyStroke>? = table[c]

    protected fun key(c: Char, usage: Int, mods: Int = HIDModifiers.NONE) {
        table[c] = listOf(KeyStroke(usage, mods))
    }

    protected fun seq(c: Char, vararg strokes: KeyStroke) {
        table[c] = strokes.toList()
    }

    /** [normal] unshifted and [shifted] with Shift on key [usage]. */
    protected fun pair(usage: Int, normal: Char, shifted: Char) {
        key(normal, usage)
        key(shifted, usage, HIDModifiers.L_SHIFT)
    }
}
```

- [ ] **Step 4: Implement `UsLayout.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys

/** US QWERTY. */
object UsLayout : TableKeyboardLayout("us") {
    init {
        for (c in 'a'..'z') pair(HIDKeys.letter(c), c, c.uppercaseChar())
        val shiftedDigits = ")!@#$%^&*("
        for (d in 0..9) pair(HIDKeys.digit(d), '0' + d, shiftedDigits[d])
        pair(HIDKeys.MINUS, '-', '_')
        pair(HIDKeys.EQUAL, '=', '+')
        pair(HIDKeys.LEFT_BRACKET, '[', '{')
        pair(HIDKeys.RIGHT_BRACKET, ']', '}')
        pair(HIDKeys.BACKSLASH, '\\', '|')
        pair(HIDKeys.SEMICOLON, ';', ':')
        pair(HIDKeys.QUOTE, '\'', '"')
        pair(HIDKeys.GRAVE, '`', '~')
        pair(HIDKeys.COMMA, ',', '<')
        pair(HIDKeys.PERIOD, '.', '>')
        pair(HIDKeys.SLASH, '/', '?')
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*UsLayoutTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts
git commit -m "feat(hid): add keyboard layout abstraction and US layout"
```

---

### Task 5: French AZERTY layout (Windows + Linux variants)

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/FrAzertyLayout.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/FrAzertyLayoutTest.kt`

**Interfaces:**
- Consumes: `TableKeyboardLayout`, `KeyStroke` (Task 4).
- Produces: `class FrAzertyLayout(linux: Boolean) : TableKeyboardLayout(if (linux) "fr-linux" else "fr-win")` and `FrAzertyLayout.letterUsage(c: Char): Int` (companion; reused by Task 6).

Layout facts (PC French AZERTY; AltGr = Right Alt). On Windows AltGr+2 (`~`) and AltGr+7 (`` ` ``) are dead keys → type them as dead key + Space. On Linux (xkb `fr`) they print directly. `^` is typed with AltGr+9 (direct) on both. Circumflex/diaeresis vowels use the `^`/`¨` dead key (US `[` position). **Linux `~`/`` ` `` behaviour must be confirmed in Task 18.**

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.L_SHIFT
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.R_ALT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrAzertyLayoutTest {

    private val win = FrAzertyLayout(linux = false)
    private val linux = FrAzertyLayout(linux = true)

    private fun one(l: KeyboardLayout, c: Char) = l.strokesFor(c)!!.single()

    @Test
    fun ids() {
        assertEquals("fr-win", win.id)
        assertEquals("fr-linux", linux.id)
    }

    @Test
    fun everyPrintableAsciiIsMappable() {
        for (l in listOf(win, linux)) for (code in 32..126)
            assertNotNull("${l.id} ${code.toChar()}", l.strokesFor(code.toChar()))
    }

    @Test
    fun azertyLetters() {
        assertEquals(KeyStroke(HIDKeys.letter('q')), one(win, 'a'))
        assertEquals(KeyStroke(HIDKeys.letter('a')), one(win, 'q'))
        assertEquals(KeyStroke(HIDKeys.letter('w')), one(win, 'z'))
        assertEquals(KeyStroke(HIDKeys.letter('z')), one(win, 'w'))
        assertEquals(KeyStroke(HIDKeys.SEMICOLON), one(win, 'm'))
        assertEquals(KeyStroke(HIDKeys.SEMICOLON, L_SHIFT), one(win, 'M'))
        assertEquals(KeyStroke(HIDKeys.letter('e')), one(win, 'e'))
    }

    @Test
    fun digitRow() {
        assertEquals(KeyStroke(HIDKeys.digit(1), L_SHIFT), one(win, '1'))
        assertEquals(KeyStroke(HIDKeys.digit(1)), one(win, '&'))
        assertEquals(KeyStroke(HIDKeys.digit(2)), one(win, 'é'))
        assertEquals(KeyStroke(HIDKeys.digit(0)), one(win, 'à'))
        assertEquals(KeyStroke(HIDKeys.digit(9)), one(win, 'ç'))
    }

    @Test
    fun altGrSymbols() {
        assertEquals(KeyStroke(HIDKeys.digit(0), R_ALT), one(win, '@'))
        assertEquals(KeyStroke(HIDKeys.digit(3), R_ALT), one(win, '#'))
        assertEquals(KeyStroke(HIDKeys.digit(4), R_ALT), one(win, '{'))
        assertEquals(KeyStroke(HIDKeys.EQUAL, R_ALT), one(win, '}'))
        assertEquals(KeyStroke(HIDKeys.digit(8), R_ALT), one(win, '\\'))
        assertEquals(KeyStroke(HIDKeys.digit(9), R_ALT), one(win, '^'))
        assertEquals(KeyStroke(HIDKeys.letter('e'), R_ALT), one(win, '€'))
    }

    @Test
    fun punctuation() {
        assertEquals(KeyStroke(HIDKeys.letter('m')), one(win, ','))
        assertEquals(KeyStroke(HIDKeys.COMMA, L_SHIFT), one(win, '.'))
        assertEquals(KeyStroke(HIDKeys.PERIOD, L_SHIFT), one(win, '/'))
        assertEquals(KeyStroke(HIDKeys.SLASH), one(win, '!'))
        assertEquals(KeyStroke(HIDKeys.NON_US_BACKSLASH), one(win, '<'))
        assertEquals(KeyStroke(HIDKeys.QUOTE), one(win, 'ù'))
    }

    @Test
    fun tildeAndBacktickDeadOnWindowsOnly() {
        assertEquals(
            listOf(KeyStroke(HIDKeys.digit(2), R_ALT), KeyStroke(HIDKeys.SPACE)),
            win.strokesFor('~')
        )
        assertEquals(listOf(KeyStroke(HIDKeys.digit(2), R_ALT)), linux.strokesFor('~'))
        assertEquals(
            listOf(KeyStroke(HIDKeys.digit(7), R_ALT), KeyStroke(HIDKeys.SPACE)),
            win.strokesFor('`')
        )
        assertEquals(listOf(KeyStroke(HIDKeys.digit(7), R_ALT)), linux.strokesFor('`'))
    }

    @Test
    fun deadKeyAccents() {
        assertEquals(
            listOf(KeyStroke(HIDKeys.LEFT_BRACKET), KeyStroke(HIDKeys.letter('e'))),
            win.strokesFor('ê')
        )
        assertEquals(
            listOf(KeyStroke(HIDKeys.LEFT_BRACKET), KeyStroke(HIDKeys.letter('q'), L_SHIFT)),
            win.strokesFor('Â')
        )
        assertEquals(
            listOf(KeyStroke(HIDKeys.LEFT_BRACKET, L_SHIFT), KeyStroke(HIDKeys.letter('i'))),
            win.strokesFor('ï')
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FrAzertyLayoutTest"`
Expected: compilation FAIL (`Unresolved reference: FrAzertyLayout`).

- [ ] **Step 3: Implement `FrAzertyLayout.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers

/**
 * PC French AZERTY. [linux] = xkb `fr`, where AltGr+2 (~) and AltGr+7 (`)
 * are plain keys; on Windows they are dead keys, followed by Space.
 */
class FrAzertyLayout(linux: Boolean) : TableKeyboardLayout(if (linux) "fr-linux" else "fr-win") {

    companion object {
        private const val ALTGR = HIDModifiers.R_ALT
        private const val SHIFT = HIDModifiers.L_SHIFT

        /** US-position usage of AZERTY letter [c]. */
        fun letterUsage(c: Char): Int = when (c.lowercaseChar()) {
            'a' -> HIDKeys.letter('q')
            'q' -> HIDKeys.letter('a')
            'z' -> HIDKeys.letter('w')
            'w' -> HIDKeys.letter('z')
            'm' -> HIDKeys.SEMICOLON
            else -> HIDKeys.letter(c)
        }
    }

    init {
        for (c in 'a'..'z') pair(letterUsage(c), c, c.uppercaseChar())

        val digitRow = "&é\"'(-è_çà" // keys 1..9,0 unshifted
        for (i in 0..9) {
            val d = (i + 1) % 10
            pair(HIDKeys.digit(d), digitRow[i], '0' + d)
        }

        pair(HIDKeys.MINUS, ')', '°')
        pair(HIDKeys.EQUAL, '=', '+')
        key('²', HIDKeys.GRAVE)
        pair(HIDKeys.RIGHT_BRACKET, '$', '£')
        pair(HIDKeys.QUOTE, 'ù', '%')
        pair(HIDKeys.BACKSLASH, '*', 'µ')
        pair(HIDKeys.NON_US_BACKSLASH, '<', '>')
        pair(HIDKeys.letter('m'), ',', '?')
        pair(HIDKeys.COMMA, ';', '.')
        pair(HIDKeys.PERIOD, ':', '/')
        pair(HIDKeys.SLASH, '!', '§')

        key('#', HIDKeys.digit(3), ALTGR)
        key('{', HIDKeys.digit(4), ALTGR)
        key('[', HIDKeys.digit(5), ALTGR)
        key('|', HIDKeys.digit(6), ALTGR)
        key('\\', HIDKeys.digit(8), ALTGR)
        key('^', HIDKeys.digit(9), ALTGR)
        key('@', HIDKeys.digit(0), ALTGR)
        key(']', HIDKeys.MINUS, ALTGR)
        key('}', HIDKeys.EQUAL, ALTGR)
        key('¤', HIDKeys.RIGHT_BRACKET, ALTGR)
        key('€', HIDKeys.letter('e'), ALTGR)

        val tilde = KeyStroke(HIDKeys.digit(2), ALTGR)
        val backtick = KeyStroke(HIDKeys.digit(7), ALTGR)
        val space = KeyStroke(HIDKeys.SPACE)
        if (linux) {
            seq('~', tilde)
            seq('`', backtick)
        } else {
            seq('~', tilde, space)
            seq('`', backtick, space)
        }

        val circumflex = KeyStroke(HIDKeys.LEFT_BRACKET)
        val diaeresis = KeyStroke(HIDKeys.LEFT_BRACKET, SHIFT)
        seq('¨', diaeresis, space)
        for ((plain, accented) in listOf('a' to 'â', 'e' to 'ê', 'i' to 'î', 'o' to 'ô', 'u' to 'û')) {
            seq(accented, circumflex, KeyStroke(letterUsage(plain)))
            seq(accented.uppercaseChar(), circumflex, KeyStroke(letterUsage(plain), SHIFT))
        }
        for ((plain, accented) in listOf('a' to 'ä', 'e' to 'ë', 'i' to 'ï', 'o' to 'ö', 'u' to 'ü', 'y' to 'ÿ')) {
            seq(accented, diaeresis, KeyStroke(letterUsage(plain)))
        }
        for ((plain, accented) in listOf('a' to 'Ä', 'e' to 'Ë', 'i' to 'Ï', 'o' to 'Ö', 'u' to 'Ü')) {
            seq(accented, diaeresis, KeyStroke(letterUsage(plain), SHIFT))
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FrAzertyLayoutTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/FrAzertyLayout.kt app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/FrAzertyLayoutTest.kt
git commit -m "feat(hid): add French AZERTY layout for Windows and Linux hosts"
```

---

### Task 6: French Mac layout + layout registry

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/FrMacLayout.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/Layouts.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/FrMacLayoutTest.kt`

**Interfaces:**
- Consumes: `TableKeyboardLayout`, `FrAzertyLayout.letterUsage(c)`, `UsLayout`.
- Produces:
  - `object FrMacLayout : TableKeyboardLayout("fr-mac")` (Option = Left Alt).
  - `object Layouts { val FR_WIN; val FR_LINUX; val ALL: List<KeyboardLayout>; fun byId(id: String): KeyboardLayout; fun defaultIdFor(language: String): String }` — order `us, fr-win, fr-linux, fr-mac`; unknown id → `UsLayout`; `defaultIdFor("fr") == "fr-win"`, anything else `"us"`.

Layout facts (Apple French, ISO). macOS reports the generic-keyboard top-left key as usage 0x64 (`@ #`) and the key next to left Shift as 0x35 (`< >`). **These two positions and `€` must be confirmed on a real Mac in Task 18**; if they come out swapped, swap `NON_US_BACKSLASH`/`GRAVE` here and in the test.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.L_ALT
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.L_SHIFT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrMacLayoutTest {

    private fun one(c: Char) = FrMacLayout.strokesFor(c)!!.single()

    @Test
    fun everyPrintableAsciiIsMappable() {
        for (code in 32..126) assertNotNull("${code.toChar()}", FrMacLayout.strokesFor(code.toChar()))
    }

    @Test
    fun macSpecificPositions() {
        assertEquals(KeyStroke(HIDKeys.NON_US_BACKSLASH), one('@'))
        assertEquals(KeyStroke(HIDKeys.NON_US_BACKSLASH, L_SHIFT), one('#'))
        assertEquals(KeyStroke(HIDKeys.GRAVE), one('<'))
        assertEquals(KeyStroke(HIDKeys.digit(6)), one('§'))
        assertEquals(KeyStroke(HIDKeys.digit(8)), one('!'))
        assertEquals(KeyStroke(HIDKeys.EQUAL), one('-'))
        assertEquals(KeyStroke(HIDKeys.EQUAL, L_SHIFT), one('_'))
        assertEquals(KeyStroke(HIDKeys.SLASH), one('='))
        assertEquals(KeyStroke(HIDKeys.RIGHT_BRACKET, L_SHIFT), one('*'))
    }

    @Test
    fun optionSymbols() {
        assertEquals(KeyStroke(HIDKeys.digit(5), L_ALT), one('{'))
        assertEquals(KeyStroke(HIDKeys.digit(5), L_ALT or L_SHIFT), one('['))
        assertEquals(KeyStroke(HIDKeys.MINUS, L_ALT), one('}'))
        assertEquals(KeyStroke(HIDKeys.letter('l'), L_ALT or L_SHIFT), one('|'))
        assertEquals(KeyStroke(HIDKeys.PERIOD, L_ALT or L_SHIFT), one('\\'))
    }

    @Test
    fun deadKeys() {
        assertEquals(listOf(KeyStroke(HIDKeys.letter('n'), L_ALT), KeyStroke(HIDKeys.SPACE)), FrMacLayout.strokesFor('~'))
        assertEquals(listOf(KeyStroke(HIDKeys.BACKSLASH), KeyStroke(HIDKeys.SPACE)), FrMacLayout.strokesFor('`'))
        assertEquals(listOf(KeyStroke(HIDKeys.LEFT_BRACKET), KeyStroke(HIDKeys.SPACE)), FrMacLayout.strokesFor('^'))
    }

    @Test
    fun registry() {
        assertEquals(listOf("us", "fr-win", "fr-linux", "fr-mac"), Layouts.ALL.map { it.id })
        assertEquals("fr-mac", Layouts.byId("fr-mac").id)
        assertEquals("us", Layouts.byId("nope").id)
        assertEquals("fr-win", Layouts.defaultIdFor("fr"))
        assertEquals("us", Layouts.defaultIdFor("de"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FrMacLayoutTest"`
Expected: compilation FAIL (`Unresolved reference: FrMacLayout`).

- [ ] **Step 3: Implement `FrMacLayout.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers

/** Apple French AZERTY (ISO). Option is sent as Left Alt. */
object FrMacLayout : TableKeyboardLayout("fr-mac") {

    private const val OPT = HIDModifiers.L_ALT
    private const val SHIFT = HIDModifiers.L_SHIFT

    init {
        for (c in 'a'..'z') pair(FrAzertyLayout.letterUsage(c), c, c.uppercaseChar())

        val digitRow = "&é\"'(§è!çà" // keys 1..9,0 unshifted
        for (i in 0..9) {
            val d = (i + 1) % 10
            pair(HIDKeys.digit(d), digitRow[i], '0' + d)
        }

        pair(HIDKeys.MINUS, ')', '°')
        pair(HIDKeys.EQUAL, '-', '_')
        pair(HIDKeys.RIGHT_BRACKET, '$', '*')
        pair(HIDKeys.QUOTE, 'ù', '%')
        key('£', HIDKeys.BACKSLASH, SHIFT)
        pair(HIDKeys.letter('m'), ',', '?')
        pair(HIDKeys.COMMA, ';', '.')
        pair(HIDKeys.PERIOD, ':', '/')
        pair(HIDKeys.SLASH, '=', '+')
        pair(HIDKeys.NON_US_BACKSLASH, '@', '#')
        pair(HIDKeys.GRAVE, '<', '>')

        key('{', HIDKeys.digit(5), OPT)
        key('[', HIDKeys.digit(5), OPT or SHIFT)
        key('}', HIDKeys.MINUS, OPT)
        key(']', HIDKeys.MINUS, OPT or SHIFT)
        key('|', HIDKeys.letter('l'), OPT or SHIFT)
        key('\\', HIDKeys.PERIOD, OPT or SHIFT)
        key('€', HIDKeys.RIGHT_BRACKET, OPT)

        val space = KeyStroke(HIDKeys.SPACE)
        val circumflex = KeyStroke(HIDKeys.LEFT_BRACKET)
        val diaeresis = KeyStroke(HIDKeys.LEFT_BRACKET, SHIFT)
        seq('~', KeyStroke(HIDKeys.letter('n'), OPT), space)
        seq('`', KeyStroke(HIDKeys.BACKSLASH), space)
        seq('^', circumflex, space)
        seq('¨', diaeresis, space)
        for ((plain, accented) in listOf('a' to 'â', 'e' to 'ê', 'i' to 'î', 'o' to 'ô', 'u' to 'û')) {
            seq(accented, circumflex, KeyStroke(FrAzertyLayout.letterUsage(plain)))
            seq(accented.uppercaseChar(), circumflex, KeyStroke(FrAzertyLayout.letterUsage(plain), SHIFT))
        }
        for ((plain, accented) in listOf('a' to 'ä', 'e' to 'ë', 'i' to 'ï', 'o' to 'ö', 'u' to 'ü', 'y' to 'ÿ')) {
            seq(accented, diaeresis, KeyStroke(FrAzertyLayout.letterUsage(plain)))
        }
    }
}
```

- [ ] **Step 4: Implement `Layouts.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

/** All supported host layouts, addressable by id (stored in preferences). */
object Layouts {
    val FR_WIN = FrAzertyLayout(linux = false)
    val FR_LINUX = FrAzertyLayout(linux = true)

    val ALL: List<KeyboardLayout> = listOf(UsLayout, FR_WIN, FR_LINUX, FrMacLayout)

    fun byId(id: String): KeyboardLayout = ALL.firstOrNull { it.id == id } ?: UsLayout

    /** Default layout id for a phone language code (e.g. "fr"). */
    fun defaultIdFor(language: String): String = if (language == "fr") FR_WIN.id else UsLayout.id
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FrMacLayoutTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts
git commit -m "feat(hid): add French Mac layout and layout registry"
```

---

### Task 7: TextTyper (text → minimal keyboard report sequence)

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/TextTyper.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/TextTyperTest.kt`

**Interfaces:**
- Consumes: `KeyboardLayout`, `KeyStroke`, `Layouts`, `UsLayout`, `FrMacLayout`, `HIDReport.Keyboard`.
- Produces:
  - `class TypedText(val text: String, val reports: List<HIDReport.Keyboard>, val reportCharIndex: List<Int>, val unmappable: List<Int>)` with `fun charIndexAt(sentReports: Int): Int` — index (in `text`) of the first character not fully typed when only `sentReports` reports went out. `text` is the normalized input; `unmappable` holds indices into `text`.
  - `object TextTyper { fun normalize(text: String): String; fun type(text: String, layout: KeyboardLayout): TypedText }`

Rules: a release report is inserted before a stroke when the previous stroke used the same usage, different modifiers, or was a non-final stroke of a dead-key sequence. Otherwise the next press directly replaces the previous key (halves report count). Sequence always ends with a release. `\r\n` and lone `\r` become `\n`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.L_SHIFT
import com.fnosari.bluetoothhid.connectivity.hid.HIDReport.Keyboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TextTyperTest {

    private val release = Keyboard.RELEASE
    private fun k(c: Char, mods: Int = 0) = Keyboard(mods, listOf(HIDKeys.letter(c)))

    @Test
    fun differentKeysNoReleaseBetween() {
        assertEquals(listOf(k('a'), k('b'), release), TextTyper.type("ab", UsLayout).reports)
    }

    @Test
    fun repeatedCharsReleaseBetween() {
        assertEquals(listOf(k('a'), release, k('a'), release), TextTyper.type("aa", UsLayout).reports)
    }

    @Test
    fun modifierChangeInsertsRelease() {
        assertEquals(listOf(k('a'), release, k('a', L_SHIFT), release), TextTyper.type("aA", UsLayout).reports)
        assertEquals(listOf(k('a', L_SHIFT), release, k('b'), release), TextTyper.type("Ab", UsLayout).reports)
        assertEquals(listOf(k('a', L_SHIFT), k('b', L_SHIFT), release), TextTyper.type("AB", UsLayout).reports)
    }

    @Test
    fun crlfBecomesSingleEnter() {
        val t = TextTyper.type("a\r\nb\rc\n", UsLayout)
        assertEquals("a\nb\nc\n", t.text)
        assertEquals(3, t.reports.count { it.keys == listOf(HIDKeys.ENTER) })
    }

    @Test
    fun unmappableSkippedAndReported() {
        val t = TextTyper.type("a☃b", UsLayout)
        assertEquals(listOf(1), t.unmappable)
        assertEquals(listOf(k('a'), k('b'), release), t.reports)
    }

    @Test
    fun deadKeyReleasesBetweenStrokes() {
        val t = TextTyper.type("ê", Layouts.FR_WIN)
        assertEquals(
            listOf(Keyboard(0, listOf(HIDKeys.LEFT_BRACKET)), release, k('e'), release),
            t.reports
        )
    }

    @Test
    fun emptyTextHasNoReports() {
        assertTrue(TextTyper.type("", UsLayout).reports.isEmpty())
    }

    @Test
    fun charIndexAtMapsReportsToChars() {
        val t = TextTyper.type("abc", UsLayout) // a, b, c, R
        assertEquals(0, t.charIndexAt(0))
        assertEquals(2, t.charIndexAt(2))
        assertEquals(3, t.charIndexAt(3))
        assertEquals(3, t.charIndexAt(4))
        val r = TextTyper.type("aa", UsLayout) // a, R, a, R
        assertEquals(1, r.charIndexAt(1))
    }

    /**
     * Replays reports like a host: a character is produced when its key goes
     * from released to pressed, with the modifiers of that report.
     */
    private fun hostDecode(reports: List<Keyboard>, layout: KeyboardLayout): String {
        val reverse = HashMap<KeyStroke, Char>()
        for (code in 32..126) {
            val s = layout.strokesFor(code.toChar())
            if (s != null && s.size == 1) reverse[s[0]] = code.toChar()
        }
        val out = StringBuilder()
        var prev = Keyboard.RELEASE
        for (r in reports) {
            for (usage in r.keys) if (usage !in prev.keys) {
                out.append(reverse[KeyStroke(usage, r.modifiers)] ?: '?')
            }
            prev = r
        }
        return out.toString()
    }

    @Test
    fun randomPasswordsRoundTrip() {
        val rnd = Random(42)
        for (layout in listOf(UsLayout, Layouts.FR_WIN, Layouts.FR_LINUX, FrMacLayout)) {
            val alphabet = (32..126).map { it.toChar() }.filter { layout.strokesFor(it)?.size == 1 }
            repeat(50) {
                val pwd = String(CharArray(200) { alphabet[rnd.nextInt(alphabet.size)] })
                val decoded = hostDecode(TextTyper.type(pwd, layout).reports, layout)
                assertEquals("layout ${layout.id}", pwd, decoded)
            }
        }
    }
}
```

`hostDecode` only counts a key when it was absent from the previous report — the test fails without the release-between-identical-keys rule.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TextTyperTest"`
Expected: compilation FAIL (`Unresolved reference: TextTyper`).

- [ ] **Step 3: Implement `TextTyper.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDReport

/**
 * Result of converting text to keyboard reports.
 *
 * @property text normalized text the reports type.
 * @property reportCharIndex for each report, index in [text] of the char it belongs to
 * (trailing release → text.length).
 * @property unmappable indices in [text] of skipped characters.
 */
class TypedText(
    val text: String,
    val reports: List<HIDReport.Keyboard>,
    val reportCharIndex: List<Int>,
    val unmappable: List<Int>
) {
    /** First char not fully typed when only [sentReports] reports were sent. */
    fun charIndexAt(sentReports: Int): Int =
        if (sentReports >= reportCharIndex.size) text.length else reportCharIndex[sentReports]
}

/** Converts text into the shortest safe keyboard report sequence for a layout. */
object TextTyper {

    fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    fun type(text: String, layout: KeyboardLayout): TypedText {
        val norm = normalize(text)
        val reports = ArrayList<HIDReport.Keyboard>(norm.length * 2)
        val charIndex = ArrayList<Int>(norm.length * 2)
        val unmappable = ArrayList<Int>()

        var prev: KeyStroke? = null
        var releaseNext = false

        norm.forEachIndexed { i, c ->
            val strokes = layout.strokesFor(c)
            if (strokes == null) {
                unmappable += i
                return@forEachIndexed
            }
            strokes.forEachIndexed { s, stroke ->
                val p = prev
                if (p != null && (releaseNext || p.usage == stroke.usage || p.modifiers != stroke.modifiers)) {
                    reports += HIDReport.Keyboard.RELEASE
                    charIndex += i
                }
                reports += HIDReport.Keyboard(stroke.modifiers, listOf(stroke.usage))
                charIndex += i
                prev = stroke
                releaseNext = s < strokes.lastIndex
            }
        }
        if (prev != null) {
            reports += HIDReport.Keyboard.RELEASE
            charIndex += norm.length
        }
        return TypedText(norm, reports, charIndex, unmappable)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TextTyperTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/TextTyper.kt app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/TextTyperTest.kt
git commit -m "feat(hid): add TextTyper with smart key release"
```

---

### Task 8: HIDReportQueue (paced single-thread sender)

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDReportQueue.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/HIDReportQueueTest.kt`

**Interfaces:**
- Consumes: `HIDReport` (Task 2).
- Produces:
  ```kotlin
  class HIDReportQueue(sender: ReportSender, delayMs: Long = 10L) {
      fun interface ReportSender { fun send(report: HIDReport): Boolean }
      interface JobListener { fun onProgress(sent: Int, total: Int) {}; fun onFinished(result: JobResult) }
      sealed class JobResult { data object Completed; data object Cancelled; data class Failed(val sentReports: Int) }
      @Volatile var delayMs: Long
      val isBusy: Boolean            // a keyboard job is queued or running
      fun start(); fun stop()
      fun submit(report: HIDReport)  // pointer lane (mouse/consumer), sent before job reports, mouse moves coalesced
      fun submitJob(reports: List<HIDReport.Keyboard>, listener: JobListener? = null)  // keyboard lane, FIFO
      fun cancelJobs()
      companion object { const val MAX_RETRIES = 3 }
  }
  ```
  Listener callbacks run on the queue thread. Pure JVM (plain `Thread`), no Android imports — unit-testable.

Behavior:
- Worker waits on a lock until work exists. Pointer lane first, else next report of the head job.
- After each report: sleep `delayMs`. A failed send retries up to `MAX_RETRIES` times, sleeping `2 × delayMs` between attempts.
- Job send failure → job `Failed(sentReports)`, all other queued jobs `Cancelled`, then a best-effort `Keyboard.RELEASE`.
- `cancelJobs()` → worker sends `Keyboard.RELEASE`, every queued job gets `Cancelled`.
- Pointer report failure → dropped silently.
- Coalescing: submitting a `Mouse` when the last queued pointer report is a `Mouse` with the same `buttons` replaces it with the summed one if each summed axis stays within −127..127.
- `submitJob` with an empty list → listener gets `Completed` immediately.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

import com.fnosari.bluetoothhid.connectivity.hid.HIDReportQueue.JobResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HIDReportQueueTest {

    private val sent: MutableList<HIDReport> = Collections.synchronizedList(mutableListOf())
    private var queue: HIDReportQueue? = null

    @After
    fun tearDown() { queue?.stop() }

    private class Recorder : HIDReportQueue.JobListener {
        val done = CountDownLatch(1)
        @Volatile var result: JobResult? = null
        @Volatile var lastProgress = 0 to 0
        override fun onProgress(sent: Int, total: Int) { lastProgress = sent to total }
        override fun onFinished(result: JobResult) { this.result = result; done.countDown() }
        fun await(): JobResult { assertTrue(done.await(2, TimeUnit.SECONDS)); return result!! }
    }

    private fun keys(n: Int) = List(n) { HIDReport.Keyboard(0, listOf(4 + it % 20)) }

    @Test
    fun jobCompletesInOrder() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0).also { queue = it }
        q.start()
        val rec = Recorder()
        val reports = keys(5)
        q.submitJob(reports, rec)
        assertEquals(JobResult.Completed, rec.await())
        assertEquals(reports, sent.toList())
        assertEquals(5 to 5, rec.lastProgress)
    }

    @Test
    fun emptyJobCompletesImmediately() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0).also { queue = it }
        val rec = Recorder()
        q.submitJob(emptyList(), rec)
        assertEquals(JobResult.Completed, rec.await())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun pointerLaneGoesFirst() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0).also { queue = it }
        val rec = Recorder()
        q.submitJob(keys(3), rec)
        q.submit(HIDReport.Consumer(HIDConsumer.MUTE))
        q.start()
        rec.await()
        assertEquals(HIDReport.Consumer(HIDConsumer.MUTE), sent.first())
    }

    @Test
    fun mouseMovesCoalesce() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0).also { queue = it }
        q.submit(HIDReport.Mouse(dx = 10, dy = 1))
        q.submit(HIDReport.Mouse(dx = 20, dy = 2))
        q.submit(HIDReport.Mouse(dx = 120, dy = 0))          // would overflow → separate
        q.submit(HIDReport.Mouse(buttons = 1, dx = 1))       // other buttons → separate
        val rec = Recorder()
        q.submitJob(keys(1), rec)                            // sentinel to know when done
        q.start()
        rec.await()
        assertEquals(
            listOf(
                HIDReport.Mouse(dx = 30, dy = 3),
                HIDReport.Mouse(dx = 120, dy = 0),
                HIDReport.Mouse(buttons = 1, dx = 1)
            ),
            sent.filterIsInstance<HIDReport.Mouse>()
        )
    }

    @Test
    fun failureAbortsReleasesAndReportsIndex() {
        val attempts: MutableList<HIDReport> = Collections.synchronizedList(mutableListOf())
        var ok = 0
        val q = HIDReportQueue({ attempts += it; if (ok < 5) { ok++; true } else false }, delayMs = 0)
            .also { queue = it }
        q.start()
        val rec = Recorder()
        val second = Recorder()
        q.submitJob(keys(20), rec)
        q.submitJob(keys(3), second)
        assertEquals(JobResult.Failed(5), rec.await())
        assertEquals(JobResult.Cancelled, second.await())
        // 5 ok + (1 + MAX_RETRIES) attempts of report #6 + release attempts
        assertEquals(keys(20)[5], attempts[5])
        assertEquals(1 + HIDReportQueue.MAX_RETRIES, attempts.count { it == keys(20)[5] })
        // listeners fire before the best-effort release: wait for attempts to settle
        var size: Int
        do { size = attempts.size; Thread.sleep(20) } while (attempts.size != size)
        assertEquals(HIDReport.Keyboard.RELEASE, attempts.last())
    }

    @Test
    fun cancelStopsAndReleases() {
        lateinit var q: HIDReportQueue
        q = HIDReportQueue({
            sent += it
            if (sent.size == 10) q.cancelJobs()
            true
        }, delayMs = 0).also { queue = it }
        q.start()
        val rec = Recorder()
        q.submitJob(keys(2000), rec)
        assertEquals(JobResult.Cancelled, rec.await())
        assertTrue(sent.size < 2000)
        assertEquals(HIDReport.Keyboard.RELEASE, sent.last())
        assertTrue(!q.isBusy)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*HIDReportQueueTest"`
Expected: compilation FAIL (`Unresolved reference: HIDReportQueue`).

- [ ] **Step 3: Implement `HIDReportQueue.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

/**
 * Sends HID reports one by one from a single worker thread, with a fixed
 * delay between reports so the Bluetooth link never drops any.
 *
 * Two lanes: pointer (mouse/consumer, sent first, mouse moves coalesced) and
 * keyboard jobs (FIFO, progress, cancel). Whatever ends a job early, the
 * queue sends an all-keys-up report so no key stays held on the host.
 *
 * @param sender sends one report, returns false when the stack refused it.
 * @param delayMs pause after each report.
 */
class HIDReportQueue(private val sender: ReportSender, @Volatile var delayMs: Long = 10L) {

    companion object {
        private const val THREAD_NAME = "HIDReportQueue"
        const val MAX_RETRIES = 3
    }

    fun interface ReportSender {
        fun send(report: HIDReport): Boolean
    }

    interface JobListener {
        fun onProgress(sent: Int, total: Int) {}
        fun onFinished(result: JobResult)
    }

    sealed class JobResult {
        data object Completed : JobResult()
        data object Cancelled : JobResult()
        data class Failed(val sentReports: Int) : JobResult()
    }

    private class Job(val reports: List<HIDReport.Keyboard>, val listener: JobListener?) {
        var index = 0
    }

    private val lock = Object()
    private val pointer = ArrayDeque<HIDReport>()
    private val jobs = ArrayDeque<Job>()
    private var cancelRequested = false
    private var running = false
    private var thread: Thread? = null

    val isBusy: Boolean get() = synchronized(lock) { jobs.isNotEmpty() }

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        thread = Thread(::loop, THREAD_NAME).apply { isDaemon = true; start() }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        thread?.join(1000)
        thread = null
    }

    fun submit(report: HIDReport) {
        synchronized(lock) {
            val last = pointer.lastOrNull()
            if (report is HIDReport.Mouse && last is HIDReport.Mouse && last.buttons == report.buttons) {
                val merged = HIDReport.Mouse(
                    last.buttons,
                    last.dx + report.dx,
                    last.dy + report.dy,
                    last.wheel + report.wheel,
                    last.pan + report.pan
                )
                if (listOf(merged.dx, merged.dy, merged.wheel, merged.pan).all { it in -127..127 }) {
                    pointer.removeLast()
                    pointer.addLast(merged)
                } else {
                    pointer.addLast(report)
                }
            } else {
                pointer.addLast(report)
            }
            lock.notifyAll()
        }
    }

    fun submitJob(reports: List<HIDReport.Keyboard>, listener: JobListener? = null) {
        if (reports.isEmpty()) {
            listener?.onFinished(JobResult.Completed)
            return
        }
        synchronized(lock) {
            jobs.addLast(Job(reports, listener))
            lock.notifyAll()
        }
    }

    fun cancelJobs() {
        synchronized(lock) {
            if (jobs.isNotEmpty()) cancelRequested = true
            lock.notifyAll()
        }
    }

    private fun loop() {
        while (true) {
            var report: HIDReport? = null
            var job: Job? = null
            var cancelled: List<Job> = emptyList()

            synchronized(lock) {
                while (running && pointer.isEmpty() && jobs.isEmpty()) lock.wait()
                if (!running) return
                if (cancelRequested) {
                    cancelled = jobs.toList()
                    jobs.clear()
                    cancelRequested = false
                } else if (pointer.isNotEmpty()) {
                    report = pointer.removeFirst()
                } else {
                    job = jobs.first()
                    report = job.reports[job.index]
                }
            }

            if (cancelled.isNotEmpty()) {
                sendWithRetry(HIDReport.Keyboard.RELEASE)
                cancelled.forEach { it.listener?.onFinished(JobResult.Cancelled) }
                continue
            }

            val ok = sendWithRetry(report!!)
            val current = job
            if (current == null) {
                pause()
                continue
            }

            if (!ok) {
                val others = synchronized(lock) {
                    val all = jobs.toList()
                    jobs.clear()
                    cancelRequested = false
                    all.filter { it !== current }
                }
                current.listener?.onFinished(JobResult.Failed(current.index))
                others.forEach { it.listener?.onFinished(JobResult.Cancelled) }
                sendWithRetry(HIDReport.Keyboard.RELEASE)
                continue
            }

            current.index++
            current.listener?.onProgress(current.index, current.reports.size)
            if (current.index == current.reports.size) {
                synchronized(lock) { jobs.removeFirst() }
                current.listener?.onFinished(JobResult.Completed)
            }
            pause()
        }
    }

    private fun sendWithRetry(report: HIDReport): Boolean {
        for (attempt in 0..MAX_RETRIES) {
            if (sender.send(report)) return true
            if (attempt < MAX_RETRIES && delayMs > 0) Thread.sleep(delayMs * 2)
        }
        return false
    }

    private fun pause() {
        if (delayMs > 0) Thread.sleep(delayMs)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*HIDReportQueueTest"`
Expected: PASS (6 tests). Run 3 times to check for flakiness.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDReportQueue.kt app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/HIDReportQueueTest.kt
git commit -m "feat(hid): add paced report queue with retry, cancel and mouse coalescing"
```

---

### Task 9: Pure UI helpers — TextDiff and PointerMath

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/utils/TextDiff.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/utils/PointerMath.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/utils/TextDiffTest.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/utils/PointerMathTest.kt`

**Interfaces:**
- Produces:
  - `data class TextEdit(val deleteCount: Int, val insert: String)`; `object TextDiff { fun diff(old: String, new: String): TextEdit }` — common-prefix diff: delete `old.length - prefix` chars (Backspace), then type `new.substring(prefix)`.
  - `class PointerAccumulator(var sensitivity: Float = 1f) { fun move(dxPx: Float, dyPx: Float): Pair<Int, Int>; fun reset() }` — applies sensitivity × acceleration `1 + min(speed / 25, 2)` (speed = hypot of the delta in px), keeps sub-pixel remainders.
  - `class ScrollAccumulator(private val pxPerStep: Float = 40f) { fun scroll(dyPx: Float): Int; fun reset() }` — finger down (dy>0) → positive wheel steps.
  - `object PointerMath { fun split(dx: Int, dy: Int): List<Pair<Int, Int>> }` — splits a move into chunks each within −127..127, sums preserved.

- [ ] **Step 1: Write the failing tests**

`TextDiffTest.kt`:
```kotlin
package com.fnosari.bluetoothhid.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDiffTest {
    @Test fun append() = assertEquals(TextEdit(0, "c"), TextDiff.diff("ab", "abc"))
    @Test fun delete() = assertEquals(TextEdit(1, ""), TextDiff.diff("abc", "ab"))
    @Test fun autocorrectReplace() = assertEquals(TextEdit(2, "he"), TextDiff.diff("teh", "the"))
    @Test fun same() = assertEquals(TextEdit(0, ""), TextDiff.diff("abc", "abc"))
    @Test fun clear() = assertEquals(TextEdit(3, ""), TextDiff.diff("abc", ""))
}
```

`PointerMathTest.kt`:
```kotlin
package com.fnosari.bluetoothhid.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerMathTest {

    @Test
    fun tinyMovesAccumulate() {
        val acc = PointerAccumulator(1f)
        var total = 0
        repeat(10) { total += acc.move(0.5f, 0f).first }
        assertTrue("total=$total", total >= 5)
    }

    @Test
    fun fastMovesAccelerate() {
        val slow = PointerAccumulator(1f).move(2f, 0f).first
        val fast = PointerAccumulator(1f).move(50f, 0f).first
        assertTrue(fast > 25 * slow)
    }

    @Test
    fun sensitivityScales() {
        val a = PointerAccumulator(1f).move(10f, 0f).first
        val b = PointerAccumulator(2f).move(10f, 0f).first
        assertTrue(b >= 2 * a - 1)
    }

    @Test
    fun scrollSteps() {
        val s = ScrollAccumulator(40f)
        assertEquals(2, s.scroll(100f))
        assertEquals(0, s.scroll(10f))
        assertEquals(1, s.scroll(10f))
        assertEquals(-1, s.scroll(-45f))
    }

    @Test
    fun splitKeepsSumAndBounds() {
        val parts = PointerMath.split(300, -10)
        assertEquals(300, parts.sumOf { it.first })
        assertEquals(-10, parts.sumOf { it.second })
        assertTrue(parts.all { it.first in -127..127 && it.second in -127..127 })
        assertEquals(listOf(5 to 5), PointerMath.split(5, 5))
        assertEquals(emptyList<Pair<Int, Int>>(), PointerMath.split(0, 0))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TextDiffTest" --tests "*PointerMathTest"`
Expected: compilation FAIL (`Unresolved reference: TextDiff`).

- [ ] **Step 3: Implement `TextDiff.kt`**

```kotlin
package com.fnosari.bluetoothhid.utils

/** Edit turning one text into another: [deleteCount] backspaces then [insert]. */
data class TextEdit(val deleteCount: Int, val insert: String)

/** Common-prefix diff used to forward IME edits as key strokes. */
object TextDiff {
    fun diff(old: String, new: String): TextEdit {
        val prefix = old.commonPrefixWith(new).length
        return TextEdit(old.length - prefix, new.substring(prefix))
    }
}
```

- [ ] **Step 4: Implement `PointerMath.kt`**

```kotlin
package com.fnosari.bluetoothhid.utils

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Converts touch deltas (px) to mouse counts with acceleration and sub-pixel carry. */
class PointerAccumulator(var sensitivity: Float = 1f) {
    private var rx = 0f
    private var ry = 0f

    fun move(dxPx: Float, dyPx: Float): Pair<Int, Int> {
        val accel = 1f + min(hypot(dxPx, dyPx) / 25f, 2f)
        val fx = dxPx * sensitivity * accel + rx
        val fy = dyPx * sensitivity * accel + ry
        val ix = fx.toInt()
        val iy = fy.toInt()
        rx = fx - ix
        ry = fy - iy
        return ix to iy
    }

    fun reset() { rx = 0f; ry = 0f }
}

/** Converts two-finger vertical drag (px) to wheel steps. */
class ScrollAccumulator(private val pxPerStep: Float = 40f) {
    private var acc = 0f

    fun scroll(dyPx: Float): Int {
        acc += dyPx
        val steps = (acc / pxPerStep).toInt()
        acc -= steps * pxPerStep
        return steps
    }

    fun reset() { acc = 0f }
}

object PointerMath {
    private const val LIMIT = 127

    /** Splits a relative move into report-sized chunks (each axis within ±127). */
    fun split(dx: Int, dy: Int): List<Pair<Int, Int>> {
        if (dx == 0 && dy == 0) return emptyList()
        val n = max((abs(dx) + LIMIT - 1) / LIMIT, (abs(dy) + LIMIT - 1) / LIMIT)
        val out = ArrayList<Pair<Int, Int>>(n)
        var sx = 0
        var sy = 0
        for (i in 1..n) {
            val tx = dx * i / n
            val ty = dy * i / n
            out += (tx - sx) to (ty - sy)
            sx = tx
            sy = ty
        }
        return out
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TextDiffTest" --tests "*PointerMathTest"`
Expected: PASS (10 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/utils app/src/test/java/com/fnosari/bluetoothhid/utils
git commit -m "feat: add text diff and pointer math helpers"
```

---

### Task 10: Model + preferences

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/model/Host.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/model/TypingSpeed.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/persistance/preferences/PreferencesProvider.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/persistance/preferences/DataStorePreferencesProvider.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/persistance/preferences/Preferences.kt`
- Test: `app/src/test/java/com/fnosari/bluetoothhid/model/TypingSpeedTest.kt`

**Interfaces:**
- Produces:
  - `data class Host(val address: String, val name: String)`
  - `enum class TypingSpeed(val delayMs: Long) { FAST(6), NORMAL(10), SAFE(25); companion object { fun fromName(name: String): TypingSpeed } }` — unknown → `NORMAL`.
  - `interface PreferencesProvider { readString/readInt/readBoolean/readDouble(key, default); write(key, String|Int|Boolean|Double) }` (same as ClassLock).
  - `class DataStorePreferencesProvider(context: Context, scope: CoroutineScope) : PreferencesProvider` (same as ClassLock; DataStore file name `"preferences"`).
  - `object Preferences` keys: `TYPING_SPEED`, `DEFAULT_LAYOUT`, `AUTO_TYPE`, `ENTER_AT_END`, `AUTO_RECONNECT`, `LAST_HOST`, `MOUSE_SENSITIVITY`, `fun hostLayoutKey(address: String): String`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fnosari.bluetoothhid.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TypingSpeedTest {
    @Test
    fun delays() {
        assertEquals(6L, TypingSpeed.FAST.delayMs)
        assertEquals(10L, TypingSpeed.NORMAL.delayMs)
        assertEquals(25L, TypingSpeed.SAFE.delayMs)
    }

    @Test
    fun fromName() {
        assertEquals(TypingSpeed.SAFE, TypingSpeed.fromName("SAFE"))
        assertEquals(TypingSpeed.NORMAL, TypingSpeed.fromName("bogus"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TypingSpeedTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Implement model files**

`model/Host.kt`:
```kotlin
package com.fnosari.bluetoothhid.model

/** A Bluetooth host (computer) the phone types into. */
data class Host(val address: String, val name: String)
```

`model/TypingSpeed.kt`:
```kotlin
package com.fnosari.bluetoothhid.model

/** Pause between two HID reports. */
enum class TypingSpeed(val delayMs: Long) {
    FAST(6),
    NORMAL(10),
    SAFE(25);

    companion object {
        fun fromName(name: String): TypingSpeed = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
```

- [ ] **Step 4: Implement preferences**

`persistance/preferences/PreferencesProvider.kt` — copy verbatim from ClassLock (`C:/Users/ACH02/Documents/Projects/Android/ClassLock/app/src/main/java/com/fnosari/classlock/persistance/preferences/PreferencesProvider.kt`), changing only the package line to `package com.fnosari.bluetoothhid.persistance.preferences`.

`persistance/preferences/DataStorePreferencesProvider.kt` — copy verbatim from ClassLock's `DataStorePreferencesProvider.kt`, changing only the package line. (It reads with `runBlocking { dataStore.data.map { … }.first() }` and writes with `scope.launch { dataStore.edit { … } }`.)

`persistance/preferences/Preferences.kt`:
```kotlin
package com.fnosari.bluetoothhid.persistance.preferences

/** Preference keys. */
object Preferences {
    const val TYPING_SPEED = "typing_speed"            // TypingSpeed.name, default NORMAL
    const val DEFAULT_LAYOUT = "default_layout"        // layout id, default from phone language
    const val AUTO_TYPE = "auto_type"                  // Boolean, default false
    const val ENTER_AT_END = "enter_at_end"            // Boolean, default false
    const val AUTO_RECONNECT = "auto_reconnect"        // Boolean, default true
    const val LAST_HOST = "last_host"                  // MAC address, default ""
    const val MOUSE_SENSITIVITY = "mouse_sensitivity"  // Double, default 1.0

    /** Layout id chosen for one host. */
    fun hostLayoutKey(address: String) = "host_layout_$address"
}
```

- [ ] **Step 5: Run test + build**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TypingSpeedTest" :app:assembleDebug`
Expected: PASS, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/model app/src/main/java/com/fnosari/bluetoothhid/persistance app/src/test/java/com/fnosari/bluetoothhid/model
git commit -m "feat: add host/typing speed model and DataStore preferences"
```

---

### Task 11: HIDDevice (BluetoothHidDevice wrapper)

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDException.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDDevice.kt`

**Interfaces:**
- Consumes: `HIDDescriptor.BYTES`, `HIDReport`.
- Produces:
  ```kotlin
  class HIDDevice(context: Context, callBack: HIDDeviceCallBack) {
      interface HIDDeviceCallBack {
          fun onAppRegistered(registered: Boolean, pluggedDevice: BluetoothDevice?)
          fun onConnectionStateChanged(device: BluetoothDevice, state: Int) // BluetoothProfile.STATE_*
          fun onError(code: HIDDeviceExitCode)
      }
      enum class HIDDeviceExitCode { ERROR_BLUETOOTH_NOT_SUPPORTED, ERROR_BLUETOOTH_NOT_ENABLED,
          ERROR_BLUETOOTH_CONNECT_PERMISSION_NOT_GRANTED, ERROR_HID_PROFILE_NOT_SUPPORTED,
          ERROR_HID_REGISTER_FAILED, ERROR_HID_NOT_CONNECTED, ERROR_HID_SEND_FAILED }
      val connectedDevice: BluetoothDevice?
      fun open(); fun close()
      fun connect(device: BluetoothDevice): Boolean
      fun disconnect(): Boolean
      fun sendReport(report: HIDReport): Boolean   // thread-safe, called from queue thread
      fun bondedDevices(): List<BluetoothDevice>
  }
  ```
  All callbacks are delivered on the main thread (`context.mainExecutor`).

No unit test (thin Android wrapper); verified by build now and on device in Task 18.

- [ ] **Step 1: Implement `HIDException.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

/**
 * Signals a Bluetooth HID error.
 *
 * @param message optional detail about the error.
 */
class HIDException(message: String?) : Exception(message)
```

- [ ] **Step 2: Implement `HIDDevice.kt`**

```kotlin
package com.fnosari.bluetoothhid.connectivity.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Makes the phone a Bluetooth HID device (keyboard + mouse + media keys)
 * through the BluetoothHidDevice profile.
 *
 * Call [open] to get the profile proxy and register the HID app, then
 * [connect] a bonded host. Results arrive on [callBack] (main thread).
 *
 * @param context used for Bluetooth system services and the main executor.
 * @param callBack receives registration, connection and error events.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is checked in open()
class HIDDevice(private val context: Context, private val callBack: HIDDeviceCallBack) {

    companion object {
        private const val LOG_TAG = "HIDDevice"
        private const val SDP_NAME = "Bluetooth HID"
        private const val SDP_DESCRIPTION = "Keyboard and mouse"
        private const val SDP_PROVIDER = "fnosari"
    }

    /** Callback interface for HID events. */
    interface HIDDeviceCallBack {
        fun onAppRegistered(registered: Boolean, pluggedDevice: BluetoothDevice?)
        fun onConnectionStateChanged(device: BluetoothDevice, state: Int)
        fun onError(code: HIDDeviceExitCode)
    }

    /** Possible error codes. */
    enum class HIDDeviceExitCode {
        ERROR_BLUETOOTH_NOT_SUPPORTED,
        ERROR_BLUETOOTH_NOT_ENABLED,
        ERROR_BLUETOOTH_CONNECT_PERMISSION_NOT_GRANTED,
        ERROR_HID_PROFILE_NOT_SUPPORTED,
        ERROR_HID_REGISTER_FAILED,
        ERROR_HID_NOT_CONNECTED,
        ERROR_HID_SEND_FAILED
    }

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    @Volatile
    private var hid: BluetoothHidDevice? = null

    @Volatile
    var connectedDevice: BluetoothDevice? = null
        private set

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(LOG_TAG, "onAppStatusChanged registered=$registered plugged=$pluggedDevice")
            callBack.onAppRegistered(registered, pluggedDevice)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(LOG_TAG, "onConnectionStateChanged $device state=$state")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
            } else if (device == connectedDevice) {
                connectedDevice = null
            }
            callBack.onConnectionStateChanged(device, state)
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = when (id.toInt()) {
                HIDReport.Keyboard.ID -> 8
                HIDReport.Mouse.ID -> 5
                HIDReport.Consumer.ID -> 2
                else -> 0
            }
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && size > 0) {
                hid?.replyReport(device, type, id, ByteArray(size))
            } else {
                hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // Keyboard LEDs (caps lock…) are ignored
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(LOG_TAG, "HID profile proxy connected")
            val device = proxy as BluetoothHidDevice
            hid = device
            val sdp = BluetoothHidDeviceAppSdpSettings(
                SDP_NAME,
                SDP_DESCRIPTION,
                SDP_PROVIDER,
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HIDDescriptor.BYTES
            )
            if (!device.registerApp(sdp, null, null, context.mainExecutor, hidCallback)) {
                Log.d(LOG_TAG, "registerApp failed")
                callBack.onError(HIDDeviceExitCode.ERROR_HID_REGISTER_FAILED)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(LOG_TAG, "HID profile proxy disconnected")
            hid = null
            connectedDevice = null
            callBack.onAppRegistered(false, null)
        }
    }

    /** Gets the HID profile proxy and registers the HID app. */
    fun open() {
        val a = adapter
        when {
            a == null -> callBack.onError(HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_SUPPORTED)
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                callBack.onError(HIDDeviceExitCode.ERROR_BLUETOOTH_CONNECT_PERMISSION_NOT_GRANTED)
            !a.isEnabled -> callBack.onError(HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED)
            !a.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE) ->
                callBack.onError(HIDDeviceExitCode.ERROR_HID_PROFILE_NOT_SUPPORTED)
        }
    }

    /** Unregisters the HID app and releases the profile proxy. */
    fun close() {
        val h = hid ?: return
        try {
            h.unregisterApp()
        } catch (e: Exception) {
            Log.d(LOG_TAG, "unregisterApp failed: ${e.message}")
        }
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        hid = null
        connectedDevice = null
    }

    fun connect(device: BluetoothDevice): Boolean = hid?.connect(device) ?: false

    fun disconnect(): Boolean {
        val d = connectedDevice ?: return false
        return hid?.disconnect(d) ?: false
    }

    /** Sends one input report to the connected host. Returns false when it could not be queued. */
    fun sendReport(report: HIDReport): Boolean {
        val d = connectedDevice ?: return false
        val h = hid ?: return false
        return h.sendReport(d, report.id, report.toBytes())
    }

    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.toList() ?: emptyList()
}
```

- [ ] **Step 3: Build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDDevice.kt app/src/main/java/com/fnosari/bluetoothhid/connectivity/hid/HIDException.kt
git commit -m "feat(hid): add BluetoothHidDevice wrapper"
```

---

### Task 12: HIDForegroundService + compose binding helper

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/services/HIDState.kt`
- Replace: `app/src/main/java/com/fnosari/bluetoothhid/services/HIDForegroundService.kt` (stub from Task 1)
- Create: `app/src/main/java/com/fnosari/bluetoothhid/utils/HIDServiceBinding.kt`

**Interfaces:**
- Consumes: `HIDDevice` (Task 11), `HIDReportQueue` (Task 8), `TextTyper`/`TypedText`/`Layouts`/`KeyboardLayout`/`KeyStroke` (Tasks 4–7), `PointerMath` (Task 9), `Host`, `TypingSpeed`, `PreferencesProvider`, `DataStorePreferencesProvider`, `Preferences` (Task 10), `BluetoothHidApp.NOTIFICATION_CHANNEL_ID` (Task 1).
- Produces (used by every activity):
  ```kotlin
  sealed class HIDState { Starting; Idle; Connecting(host: Host); Connected(host: Host); Unavailable(code: HIDDevice.HIDDeviceExitCode) }
  data class TypingProgress(val sent: Int, val total: Int)
  sealed class TypingEvent { Completed; Cancelled; Interrupted(val atChar: Int, val text: String) }

  class HIDForegroundService : Service {
      companion object { fun start(context: Context); const val ACTION_DISCONNECT; const val ACTION_QUIT }
      inner class LocalBinder : Binder { val service: HIDForegroundService }
      lateinit var preferences: PreferencesProvider
      val state: StateFlow<HIDState>
      val layout: StateFlow<KeyboardLayout>          // layout of connected host (or default)
      val typing: StateFlow<TypingProgress?>
      val events: SharedFlow<TypingEvent>
      fun retry()
      fun bondedHosts(): List<Host>
      fun connect(address: String)
      fun disconnect()
      fun quit()
      fun setLayoutForCurrentHost(layoutId: String)
      fun typeText(text: String, enterAtEnd: Boolean = false, track: Boolean = true): TypedText?
      fun cancelTyping()
      fun sendKey(usage: Int, modifiers: Int = 0)
      fun sendStroke(stroke: KeyStroke, extraModifiers: Int = 0)
      fun backspace(count: Int)
      fun sendConsumer(usage: Int)
      fun mouseMove(dx: Int, dy: Int)
      fun mouseScroll(steps: Int)
      fun mouseClick(button: Int)
      fun setMouseButtons(buttons: Int)
  }

  @Composable fun rememberHIDService(): HIDForegroundService?   // binds without creating
  ```

No unit test (Android service). Verified by build here and on device in Task 18.

- [ ] **Step 1: Create `services/HIDState.kt`**

```kotlin
package com.fnosari.bluetoothhid.services

import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice
import com.fnosari.bluetoothhid.model.Host

/** Connection state exposed by [HIDForegroundService]. */
sealed class HIDState {
    data object Starting : HIDState()
    data object Idle : HIDState()
    data class Connecting(val host: Host) : HIDState()
    data class Connected(val host: Host) : HIDState()
    data class Unavailable(val code: HIDDevice.HIDDeviceExitCode) : HIDState()
}

/** Progress of the current tracked typing job, in reports. */
data class TypingProgress(val sent: Int, val total: Int)

/** End of a tracked typing job. */
sealed class TypingEvent {
    data object Completed : TypingEvent()
    data object Cancelled : TypingEvent()

    /** Typing stopped early; [text] from [atChar] was not typed. */
    data class Interrupted(val atChar: Int, val text: String) : TypingEvent()
}
```

- [ ] **Step 2: Replace `services/HIDForegroundService.kt`**

```kotlin
package com.fnosari.bluetoothhid.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.fnosari.bluetoothhid.BluetoothHidApp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.activities.main.MainActivity
import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice
import com.fnosari.bluetoothhid.connectivity.hid.HIDReport
import com.fnosari.bluetoothhid.connectivity.hid.HIDReportQueue
import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyStroke
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyboardLayout
import com.fnosari.bluetoothhid.connectivity.hid.layouts.Layouts
import com.fnosari.bluetoothhid.connectivity.hid.layouts.TextTyper
import com.fnosari.bluetoothhid.connectivity.hid.layouts.TypedText
import com.fnosari.bluetoothhid.model.Host
import com.fnosari.bluetoothhid.model.TypingSpeed
import com.fnosari.bluetoothhid.persistance.preferences.DataStorePreferencesProvider
import com.fnosari.bluetoothhid.persistance.preferences.Preferences
import com.fnosari.bluetoothhid.persistance.preferences.PreferencesProvider
import com.fnosari.bluetoothhid.utils.PointerMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Owns the HID connection so it survives activity changes. Activities bind
 * with [com.fnosari.bluetoothhid.utils.rememberHIDService].
 */
@SuppressLint("MissingPermission") // service only starts after BLUETOOTH_CONNECT is granted
class HIDForegroundService : Service(), HIDDevice.HIDDeviceCallBack {

    companion object {
        private const val LOG_TAG = "HIDForegroundService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "com.fnosari.bluetoothhid.action.DISCONNECT"
        const val ACTION_QUIT = "com.fnosari.bluetoothhid.action.QUIT"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, HIDForegroundService::class.java))
        }
    }

    inner class LocalBinder : Binder() {
        val service: HIDForegroundService get() = this@HIDForegroundService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var preferences: PreferencesProvider
        private set
    private lateinit var device: HIDDevice
    private lateinit var queue: HIDReportQueue

    private val _state = MutableStateFlow<HIDState>(HIDState.Starting)
    val state: StateFlow<HIDState> = _state.asStateFlow()

    private val _layout = MutableStateFlow<KeyboardLayout>(Layouts.byId("us"))
    val layout: StateFlow<KeyboardLayout> = _layout.asStateFlow()

    private val _typing = MutableStateFlow<TypingProgress?>(null)
    val typing: StateFlow<TypingProgress?> = _typing.asStateFlow()

    private val _events = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TypingEvent> = _events.asSharedFlow()

    @Volatile
    private var mouseButtons = 0
    private var closing = false

    override fun onCreate() {
        super.onCreate()
        preferences = DataStorePreferencesProvider(this, scope)
        _layout.value = Layouts.byId(defaultLayoutId())
        device = HIDDevice(this, this)
        queue = HIDReportQueue(device::sendReport, currentSpeed().delayMs)
        queue.start()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_idle), connected = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        device.open()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnect()
            ACTION_QUIT -> quit()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        closing = true
        queue.stop()
        device.close()
        scope.cancel()
        super.onDestroy()
    }

    // ---- HIDDevice callbacks (main thread) ----

    override fun onAppRegistered(registered: Boolean, pluggedDevice: BluetoothDevice?) {
        if (closing) return
        if (!registered) {
            if (_state.value !is HIDState.Unavailable) _state.value = HIDState.Idle
            return
        }
        if (_state.value is HIDState.Starting || _state.value is HIDState.Unavailable) {
            _state.value = HIDState.Idle
        }
        if (preferences.readBoolean(Preferences.AUTO_RECONNECT, true)) {
            val target = pluggedDevice?.address ?: preferences.readString(Preferences.LAST_HOST, "")
            if (target.isNotEmpty()) connect(target)
        }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        val host = Host(device.address, device.name ?: device.address)
        when (state) {
            BluetoothProfile.STATE_CONNECTING -> _state.value = HIDState.Connecting(host)
            BluetoothProfile.STATE_CONNECTED -> {
                _state.value = HIDState.Connected(host)
                preferences.write(Preferences.LAST_HOST, host.address)
                _layout.value = layoutFor(host.address)
                updateNotification(getString(R.string.notification_connected, host.name), connected = true)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                queue.cancelJobs()
                if (_state.value !is HIDState.Unavailable) _state.value = HIDState.Idle
                _layout.value = Layouts.byId(defaultLayoutId())
                updateNotification(getString(R.string.notification_idle), connected = false)
            }
        }
    }

    override fun onError(code: HIDDevice.HIDDeviceExitCode) {
        Log.d(LOG_TAG, "HID error $code")
        _state.value = HIDState.Unavailable(code)
    }

    // ---- Connection API ----

    fun retry() {
        device.close()
        _state.value = HIDState.Starting
        device.open()
    }

    fun bondedHosts(): List<Host> =
        device.bondedDevices().map { Host(it.address, it.name ?: it.address) }.sortedBy { it.name }

    fun connect(address: String) {
        val target = device.bondedDevices().firstOrNull { it.address == address } ?: return
        device.connect(target)
    }

    fun disconnect() {
        queue.cancelJobs()
        device.disconnect()
    }

    fun quit() {
        disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setLayoutForCurrentHost(layoutId: String) {
        val host = (state.value as? HIDState.Connected)?.host
        if (host != null) {
            preferences.write(Preferences.hostLayoutKey(host.address), layoutId)
        } else {
            preferences.write(Preferences.DEFAULT_LAYOUT, layoutId)
        }
        _layout.value = Layouts.byId(layoutId)
    }

    private fun defaultLayoutId(): String =
        preferences.readString(Preferences.DEFAULT_LAYOUT, Layouts.defaultIdFor(Locale.getDefault().language))

    private fun layoutFor(address: String): KeyboardLayout =
        Layouts.byId(preferences.readString(Preferences.hostLayoutKey(address), defaultLayoutId()))

    private fun currentSpeed(): TypingSpeed =
        TypingSpeed.fromName(preferences.readString(Preferences.TYPING_SPEED, TypingSpeed.NORMAL.name))

    private val isConnected: Boolean get() = state.value is HIDState.Connected

    // ---- Keyboard API ----

    /**
     * Types [text] on the host. [track] = report progress in [typing] and the
     * outcome in [events] (off for live keyboard echo).
     * @return the typed text plan, or null when not connected.
     */
    fun typeText(text: String, enterAtEnd: Boolean = false, track: Boolean = true): TypedText? {
        if (!isConnected) return null
        val typed = TextTyper.type(if (enterAtEnd) text + "\n" else text, layout.value)
        queue.delayMs = currentSpeed().delayMs
        if (!track) {
            queue.submitJob(typed.reports)
            return typed
        }
        _typing.value = TypingProgress(0, typed.reports.size)
        queue.submitJob(typed.reports, object : HIDReportQueue.JobListener {
            override fun onProgress(sent: Int, total: Int) {
                _typing.value = TypingProgress(sent, total)
            }

            override fun onFinished(result: HIDReportQueue.JobResult) {
                _typing.value = null
                val event = when (result) {
                    HIDReportQueue.JobResult.Completed -> TypingEvent.Completed
                    HIDReportQueue.JobResult.Cancelled -> TypingEvent.Cancelled
                    is HIDReportQueue.JobResult.Failed ->
                        TypingEvent.Interrupted(typed.charIndexAt(result.sentReports), typed.text)
                }
                _events.tryEmit(event)
            }
        })
        return typed
    }

    fun cancelTyping() = queue.cancelJobs()

    fun sendKey(usage: Int, modifiers: Int = 0) {
        if (!isConnected) return
        queue.submitJob(listOf(HIDReport.Keyboard(modifiers, listOf(usage)), HIDReport.Keyboard.RELEASE))
    }

    fun sendStroke(stroke: KeyStroke, extraModifiers: Int = 0) =
        sendKey(stroke.usage, stroke.modifiers or extraModifiers)

    fun backspace(count: Int) {
        if (!isConnected || count <= 0) return
        val press = HIDReport.Keyboard(0, listOf(HIDKeys.BACKSPACE))
        queue.submitJob(List(count * 2) { if (it % 2 == 0) press else HIDReport.Keyboard.RELEASE })
    }

    fun sendConsumer(usage: Int) {
        if (!isConnected) return
        queue.submit(HIDReport.Consumer(usage))
        queue.submit(HIDReport.Consumer.RELEASE)
    }

    // ---- Mouse API ----

    fun mouseMove(dx: Int, dy: Int) {
        if (!isConnected) return
        for ((x, y) in PointerMath.split(dx, dy)) queue.submit(HIDReport.Mouse(mouseButtons, x, y))
    }

    fun mouseScroll(steps: Int) {
        if (!isConnected || steps == 0) return
        queue.submit(HIDReport.Mouse(mouseButtons, wheel = steps.coerceIn(-127, 127)))
    }

    fun mouseClick(button: Int) {
        if (!isConnected) return
        queue.submit(HIDReport.Mouse(mouseButtons or button))
        queue.submit(HIDReport.Mouse(mouseButtons))
    }

    fun setMouseButtons(buttons: Int) {
        mouseButtons = buttons
        if (isConnected) queue.submit(HIDReport.Mouse(buttons))
    }

    // ---- Notification ----

    private fun updateNotification(text: String, connected: Boolean) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, connected))
    }

    private fun buildNotification(text: String, connected: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        fun serviceAction(action: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode,
            Intent(this, HIDForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, BluetoothHidApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
        if (connected) {
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.notification_disconnect),
                    serviceAction(ACTION_DISCONNECT, 1)).build()
            )
        }
        builder.addAction(
            Notification.Action.Builder(null, getString(R.string.notification_quit),
                serviceAction(ACTION_QUIT, 2)).build()
        )
        return builder.build()
    }
}
```

- [ ] **Step 3: Create `utils/HIDServiceBinding.kt`**

```kotlin
package com.fnosari.bluetoothhid.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fnosari.bluetoothhid.services.HIDForegroundService

/**
 * Binds to [HIDForegroundService] for the lifetime of the composition.
 * Does not create the service (MainActivity starts it); returns null until bound.
 */
@Composable
fun rememberHIDService(): HIDForegroundService? {
    val context = LocalContext.current
    var service by remember { mutableStateOf<HIDForegroundService?>(null) }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as HIDForegroundService.LocalBinder).service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        context.bindService(Intent(context, HIDForegroundService::class.java), connection, 0)
        onDispose {
            context.unbindService(connection)
            service = null
        }
    }
    return service
}
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fnosari/bluetoothhid/services app/src/main/java/com/fnosari/bluetoothhid/utils/HIDServiceBinding.kt
git commit -m "feat: add HID foreground service and compose binding helper"
```

---

### Task 13: Shared UI pieces + MainActivity (permissions, Home, Hosts)

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/ui/layouts/BasicLayoutWithTopBar.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/ui/components/HIDUiState.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/ui/components/ConnectionBanner.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/ui/components/LayoutPicker.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/main/components/PermissionsGate.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/main/screens/HomeScreen.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/main/screens/DevicesScreen.kt`
- Replace: `app/src/main/java/com/fnosari/bluetoothhid/activities/main/MainActivity.kt`

**Interfaces:**
- Consumes: `HIDForegroundService` API, `HIDState`, `rememberHIDService()` (Task 12), `Layouts` (Task 6), string keys (Task 1).
- Produces (used by Tasks 14–17):
  - `@Composable fun BasicLayoutWithTopBar(title: String, leftActionIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack, leftAction: (() -> Unit)? = null, rightActions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit = {})`
  - `@Composable fun HIDForegroundService?.connectionState(): HIDState`, `@Composable fun HIDForegroundService?.currentLayout(): KeyboardLayout`, `@Composable fun HIDForegroundService?.typingProgress(): TypingProgress?`
  - `@StringRes fun layoutLabel(id: String): Int`, `@StringRes fun errorLabel(code: HIDDevice.HIDDeviceExitCode): Int`
  - `@Composable fun ConnectionBanner(state: HIDState, modifier: Modifier = Modifier)` — shows `banner_connect_host` unless connected.
  - `@Composable fun LayoutChip(layout: KeyboardLayout, onPick: (String) -> Unit)` — chip + picker dialog.

- [ ] **Step 1: Create `ui/layouts/BasicLayoutWithTopBar.kt`**

Copy `ClassLock/app/src/main/java/com/fnosari/classlock/ui/layouts/BasicLayoutWithTopBar.kt` verbatim, then change: package → `com.fnosari.bluetoothhid.ui.layouts`; `contentDescription = "Go back"` → `contentDescription = stringResource(R.string.go_back)` (import `androidx.compose.ui.res.stringResource` and `com.fnosari.bluetoothhid.R`).

- [ ] **Step 2: Create `ui/components/HIDUiState.kt`**

```kotlin
package com.fnosari.bluetoothhid.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice.HIDDeviceExitCode
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyboardLayout
import com.fnosari.bluetoothhid.connectivity.hid.layouts.UsLayout
import com.fnosari.bluetoothhid.services.HIDForegroundService
import com.fnosari.bluetoothhid.services.HIDState
import com.fnosari.bluetoothhid.services.TypingProgress

@Composable
fun HIDForegroundService?.connectionState(): HIDState =
    this?.state?.collectAsStateWithLifecycle()?.value ?: HIDState.Starting

@Composable
fun HIDForegroundService?.currentLayout(): KeyboardLayout =
    this?.layout?.collectAsStateWithLifecycle()?.value ?: UsLayout

@Composable
fun HIDForegroundService?.typingProgress(): TypingProgress? =
    this?.typing?.collectAsStateWithLifecycle()?.value

@StringRes
fun layoutLabel(id: String): Int = when (id) {
    "fr-win" -> R.string.layout_fr_win
    "fr-linux" -> R.string.layout_fr_linux
    "fr-mac" -> R.string.layout_fr_mac
    else -> R.string.layout_us
}

@StringRes
fun errorLabel(code: HIDDeviceExitCode): Int = when (code) {
    HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_SUPPORTED -> R.string.error_bluetooth_not_supported
    HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED -> R.string.error_bluetooth_not_enabled
    HIDDeviceExitCode.ERROR_BLUETOOTH_CONNECT_PERMISSION_NOT_GRANTED -> R.string.error_permission
    HIDDeviceExitCode.ERROR_HID_PROFILE_NOT_SUPPORTED -> R.string.error_hid_not_supported
    HIDDeviceExitCode.ERROR_HID_REGISTER_FAILED -> R.string.error_hid_register_failed
    HIDDeviceExitCode.ERROR_HID_NOT_CONNECTED -> R.string.error_hid_not_connected
    HIDDeviceExitCode.ERROR_HID_SEND_FAILED -> R.string.error_hid_send_failed
}
```

- [ ] **Step 3: Create `ui/components/ConnectionBanner.kt`**

```kotlin
package com.fnosari.bluetoothhid.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.services.HIDState

/** Shown on feature screens while no host is connected. */
@Composable
fun ConnectionBanner(state: HIDState, modifier: Modifier = Modifier) {
    if (state is HIDState.Connected) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.banner_connect_host),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

- [ ] **Step 4: Create `ui/components/LayoutPicker.kt`**

```kotlin
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
```

- [ ] **Step 5: Create `activities/main/components/PermissionsGate.kt`**

```kotlin
package com.fnosari.bluetoothhid.activities.main.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fnosari.bluetoothhid.R

private val PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.POST_NOTIFICATIONS
)

private fun Context.hasBluetoothConnect() =
    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Shows [content] once BLUETOOTH_CONNECT is granted, otherwise an explanation screen. */
@Composable
fun PermissionsGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasBluetoothConnect()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = context.hasBluetoothConnect()
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(PERMISSIONS) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasBluetoothConnect() }

    if (granted) {
        content()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically)
    ) {
        Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.permissions_text))
        Button(onClick = { launcher.launch(PERMISSIONS) }) { Text(stringResource(R.string.permissions_grant)) }
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }) { Text(stringResource(R.string.action_open_settings)) }
    }
}
```

- [ ] **Step 6: Create `activities/main/screens/HomeScreen.kt`**

```kotlin
package com.fnosari.bluetoothhid.activities.main.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.activities.keyboard.KeyboardActivity
import com.fnosari.bluetoothhid.activities.main.MainActivity
import com.fnosari.bluetoothhid.activities.mouse.MouseActivity
import com.fnosari.bluetoothhid.activities.scan.ScanActivity
import com.fnosari.bluetoothhid.activities.settings.SettingsActivity
import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice.HIDDeviceExitCode
import com.fnosari.bluetoothhid.services.HIDState
import com.fnosari.bluetoothhid.ui.components.LayoutChip
import com.fnosari.bluetoothhid.ui.components.connectionState
import com.fnosari.bluetoothhid.ui.components.currentLayout
import com.fnosari.bluetoothhid.ui.components.errorLabel
import com.fnosari.bluetoothhid.ui.layouts.BasicLayoutWithTopBar
import com.fnosari.bluetoothhid.utils.rememberHIDService

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val service = rememberHIDService()
    val state = service.connectionState()
    val layout = service.currentLayout()
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        service?.retry()
    }

    BasicLayoutWithTopBar(title = stringResource(R.string.app_name)) {
        Column(
            modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val text = when (state) {
                        HIDState.Starting -> stringResource(R.string.state_starting)
                        HIDState.Idle -> stringResource(R.string.state_idle)
                        is HIDState.Connecting -> stringResource(R.string.state_connecting, state.host.name)
                        is HIDState.Connected -> stringResource(R.string.state_connected, state.host.name)
                        is HIDState.Unavailable -> stringResource(errorLabel(state.code))
                    }
                    Text(text, style = MaterialTheme.typography.titleMedium)
                    if (state is HIDState.Connected) {
                        LayoutChip(layout) { service?.setLayoutForCurrentHost(it) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (state) {
                            is HIDState.Connected -> OutlinedButton(onClick = { service?.disconnect() }) {
                                Text(stringResource(R.string.home_disconnect))
                            }
                            is HIDState.Unavailable -> if (state.code == HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED) {
                                Button(onClick = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
                                    Text(stringResource(R.string.action_enable_bluetooth))
                                }
                            } else {
                                Button(onClick = { service?.retry() }) { Text(stringResource(R.string.action_retry)) }
                            }
                            else -> {}
                        }
                        Button(onClick = { navController.navigate(MainActivity.Routes.DEVICES.name) }) {
                            Icon(Icons.Rounded.Devices, contentDescription = null)
                            Text(stringResource(R.string.home_devices), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile(Icons.Rounded.QrCodeScanner, stringResource(R.string.home_tile_scan), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, ScanActivity::class.java))
                }
                Tile(Icons.Rounded.Keyboard, stringResource(R.string.home_tile_keyboard), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, KeyboardActivity::class.java))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile(Icons.Rounded.Mouse, stringResource(R.string.home_tile_mouse), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, MouseActivity::class.java))
                }
                Tile(Icons.Rounded.Settings, stringResource(R.string.home_tile_settings), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.aspectRatio(1f)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
```

- [ ] **Step 7: Create `activities/main/screens/DevicesScreen.kt`**

```kotlin
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
```

- [ ] **Step 8: Replace `activities/main/MainActivity.kt`**

```kotlin
package com.fnosari.bluetoothhid.activities.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fnosari.bluetoothhid.activities.main.components.PermissionsGate
import com.fnosari.bluetoothhid.activities.main.screens.DevicesScreen
import com.fnosari.bluetoothhid.activities.main.screens.HomeScreen
import com.fnosari.bluetoothhid.services.HIDForegroundService
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothHIDTheme {
                PermissionsGate {
                    val context = LocalContext.current
                    LaunchedEffect(Unit) { HIDForegroundService.start(context) }
                    MainActivityNavigation()
                }
            }
        }
    }

    /**
     * Routes for the main activity
     */
    enum class Routes {
        HOME,
        DEVICES
    }
}

/**
 * Navigation for the main activity
 */
@Composable
fun MainActivityNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) },
        startDestination = MainActivity.Routes.HOME.name
    ) {
        composable(MainActivity.Routes.HOME.name) { HomeScreen(navController) }
        composable(MainActivity.Routes.DEVICES.name) { DevicesScreen(navController) }
    }
}
```

- [ ] **Step 9: Build + install + smoke test**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.
If a phone is attached (`adb devices`): `.\gradlew.bat :app:installDebug`, launch the app, grant permissions. Expected: Home shows "Not connected", notification "Waiting for a host" appears, Hosts screen lists paired devices.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat: add main activity with permissions, home and hosts screens"
```

---

### Task 14: ScanActivity (camera scan, preview, type)

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/scan/components/QrAnalyzer.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/scan/components/PreviewSheet.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/scan/screens/ScanScreen.kt`
- Replace: `app/src/main/java/com/fnosari/bluetoothhid/activities/scan/ScanActivity.kt`

**Interfaces:**
- Consumes: Task 12 service API (`typeText`, `cancelTyping`, `events`, `setLayoutForCurrentHost`, `preferences`), Task 13 `ui/components` helpers, `TextTyper` (Task 7), `Preferences` keys.
- Produces: `@Composable fun CameraPreview(enabled: Boolean, onCode: (String) -> Unit, modifier: Modifier = Modifier)`; `@Composable fun PreviewSheet(...)` (signature below).

- [ ] **Step 1: Create `components/QrAnalyzer.kt`**

```kotlin
package com.fnosari.bluetoothhid.activities.scan.components

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** ML Kit analyzer: reports the first decoded value, then stays quiet until re-enabled. */
class QrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
    )

    @Volatile
    var enabled = true

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (!enabled || media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val value = codes.firstNotNullOfOrNull { it.rawValue }
                if (value != null && enabled) {
                    enabled = false
                    onCode(value)
                }
            }
            .addOnCompleteListener { image.close() }
    }

    fun close() = scanner.close()
}

/** Back camera preview with barcode analysis. [onCode] runs on the main thread. */
@Composable
fun CameraPreview(enabled: Boolean, onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val analyzer = remember { QrAnalyzer { code -> context.mainExecutor.execute { currentOnCode(code) } } }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(enabled) { analyzer.enabled = enabled }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, context.mainExecutor)
        onDispose {
            if (future.isDone) future.get().unbindAll()
            executor.shutdown()
            analyzer.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
```

- [ ] **Step 2: Create `components/PreviewSheet.kt`**

```kotlin
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
```

- [ ] **Step 3: Create `screens/ScanScreen.kt`**

```kotlin
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
```

- [ ] **Step 4: Replace `ScanActivity.kt`**

```kotlin
package com.fnosari.bluetoothhid.activities.scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.activities.scan.screens.ScanScreen
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class ScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BluetoothHIDTheme { ScanScreen(onBack = ::finish) } }
    }
}
```

- [ ] **Step 5: Build**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(scan): add camera scan with preview, typing progress and resume"
```

---

### Task 15: KeyboardActivity (live IME forwarding + special keys)

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/keyboard/components/SpecialKeysBar.kt`
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/keyboard/screens/KeyboardScreen.kt`
- Replace: `app/src/main/java/com/fnosari/bluetoothhid/activities/keyboard/KeyboardActivity.kt`

**Interfaces:**
- Consumes: service `typeText(text, track = false)`, `backspace`, `sendKey`, `sendStroke`, `sendConsumer`, `setLayoutForCurrentHost`; `TextDiff` (Task 9); `HIDKeys`, `HIDModifiers`, `HIDConsumer`; Task 13 components.
- Produces: `@Composable fun SpecialKeysBar(sticky: Int, layout: KeyboardLayout, onToggleSticky: (Int) -> Unit, onKey: (usage: Int) -> Unit, onShortcut: (usage: Int, modifiers: Int) -> Unit, onConsumer: (usage: Int) -> Unit)`.

Key cap labels (`Esc`, `Tab`, `F1`, `Ctrl`…) are printed key names, identical on EN and FR keyboards, so they stay as literals in `SpecialKeysBar` (only exception to the strings rule).

Behavior:
- **Live** mode: each text change → `TextDiff.diff(old, new)` → `backspace(deleteCount)` then `typeText(insert, track = false)`. If a sticky modifier is on and the edit is exactly one inserted char → send that char's last stroke with the sticky modifiers (e.g. sticky Ctrl + typing `c` = Ctrl+C), then clear sticky. **Clear** empties the field without sending anything.
- **Block** mode: text is only local; **Send** types it (tracked), **Clear** empties.
- Special key tap → `sendKey(usage, sticky)`, then sticky resets to 0.

- [ ] **Step 1: Create `components/SpecialKeysBar.kt`**

```kotlin
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
```

- [ ] **Step 2: Create `screens/KeyboardScreen.kt`**

```kotlin
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

    fun onTextChange(new: String) {
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
```

- [ ] **Step 3: Replace `KeyboardActivity.kt`**

```kotlin
package com.fnosari.bluetoothhid.activities.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.activities.keyboard.screens.KeyboardScreen
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class KeyboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BluetoothHIDTheme { KeyboardScreen(onBack = ::finish) } }
    }
}
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(keyboard): add live IME forwarding and special keys bar"
```

---

### Task 16: MouseActivity (touchpad)

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/mouse/screens/TouchpadScreen.kt`
- Replace: `app/src/main/java/com/fnosari/bluetoothhid/activities/mouse/MouseActivity.kt`

**Interfaces:**
- Consumes: service `mouseMove`, `mouseScroll`, `mouseClick`, `setMouseButtons`, `preferences`; `PointerAccumulator`, `ScrollAccumulator` (Task 9); `HIDMouseButtons`; `Preferences.MOUSE_SENSITIVITY`.

Gestures: 1 finger move → pointer; quick tap → left click (or release drag lock if locked); finger held still ≥ long-press timeout → drag lock on (left held) until next tap; 2 fingers moving → wheel; 2-finger tap → right click. Left/Right buttons below the pad: press = button down, release = up (so hold Left + move on pad drags).

- [ ] **Step 1: Create `screens/TouchpadScreen.kt`**

```kotlin
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
```

- [ ] **Step 2: Replace `MouseActivity.kt`**

```kotlin
package com.fnosari.bluetoothhid.activities.mouse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.activities.mouse.screens.TouchpadScreen
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class MouseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BluetoothHIDTheme { TouchpadScreen(onBack = ::finish) } }
    }
}
```

- [ ] **Step 3: Build**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(mouse): add touchpad with tap, drag lock, scroll and right click"
```

---

### Task 17: SettingsActivity

**Files:**
- Create: `app/src/main/java/com/fnosari/bluetoothhid/activities/settings/screens/SettingsScreen.kt`
- Replace: `app/src/main/java/com/fnosari/bluetoothhid/activities/settings/SettingsActivity.kt`

**Interfaces:**
- Consumes: `DataStorePreferencesProvider`, `Preferences`, `TypingSpeed`, `Layouts`, `LayoutPickerDialog`, `layoutLabel` (Task 13).

Settings are read by the service at use time (`typeText` reads speed each call; layout default read on disconnect/connect), so no service notification is needed.

- [ ] **Step 1: Create `screens/SettingsScreen.kt`**

```kotlin
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
```

- [ ] **Step 2: Replace `SettingsActivity.kt`**

```kotlin
package com.fnosari.bluetoothhid.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fnosari.bluetoothhid.activities.settings.screens.SettingsScreen
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BluetoothHIDTheme { SettingsScreen(onBack = ::finish) } }
    }
}
```

- [ ] **Step 3: Build**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(settings): add typing, layout, connection, mouse and language settings"
```

---

### Task 18: Full verification (lint + on-device checks)

**Files:**
- Modify (only if a check fails): the layout file of the failing layout and its test.

- [ ] **Step 1: Full build, tests, lint**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: `BUILD SUCCESSFUL`, all unit tests pass, lint 0 errors (fix any error, warnings may stay). Check that every key in `values/strings.xml` exists in `values-fr/strings.xml` and vice versa:
```bash
diff <(grep -o 'name="[^"]*"' app/src/main/res/values/strings.xml | sort) <(grep -o 'name="[^"]*"' app/src/main/res/values-fr/strings.xml | sort)
```
Expected: no output.

- [ ] **Step 2: Install on a real phone** (HID profile is not available on the emulator)

Run: `.\gradlew.bat :app:installDebug`, open the app, grant permissions. Expected: Home "Not connected"; persistent notification.

- [ ] **Step 3: Pair + connect, per host OS (Windows, macOS, Linux)**

Hosts › Pair new host → add the phone from the computer's Bluetooth settings. Expected: Home "Connected to <PC>". Kill and relaunch the app → auto-reconnects.

- [ ] **Step 4: Layout exactness test, per host OS with its layout**

Open a plain text editor on the host. In Keyboard › Block mode paste and Send this line (all printable ASCII + accents):
```
 !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~ éèàçù âêîôû äëïöü €
```
Expected: identical text on the host. If FR (Mac) swaps `@#` with `<>`, swap `NON_US_BACKSLASH`/`GRAVE` in `FrMacLayout` + its test. If FR (Linux) prints `~ ` (extra space) or nothing for `~`/`` ` ``, change those to the dead-key form in `FrAzertyLayout` + test. Commit any fix as `fix(hid): correct <layout> mapping for <chars>`.

- [ ] **Step 5: Speed + robustness**

1. Generate a 200-char random password (e.g. a password manager), show it as QR on another screen, scan it, Type at Normal. Expected: identical, ≤ 5 s. Repeat at Fast; note if any char is lost (then Normal stays default).
2. Scan a 2 000-char QR, Type, press Cancel mid-way. Expected: stops within ~0.5 s, no key stuck (type on the physical keyboard of the PC: normal characters, no repeated key/modifier).
3. Start typing a long text, turn off Bluetooth on the PC. Expected: snackbar "Typing interrupted at character N" with Resume; reconnect, Resume types the rest.

- [ ] **Step 6: Keyboard + mouse**

Live mode: type with Gboard (incl. autocorrect and swipe) — host text matches the field. Sticky Ctrl + `a` selects all. Special keys, F-keys, media keys work. Touchpad: move, tap click, 2-finger scroll, 2-finger tap right click, long-press drag of a window, Left hold + move drags.

- [ ] **Step 7: Commit verification notes**

Append a short "Verified on" list (phone model, Android version, host OSes, results) to the end of the spec file, then:
```bash
git add -A
git commit -m "docs: record on-device verification results"
```
