# Bluetooth HID — design spec

Date: 2026-10-09
Status: approved in brainstorm, pending written review

## 1. Purpose

Android app (`com.fnosari.bluetoothhid`) that makes the phone a Bluetooth
keyboard + mouse for a computer:

- **Scan → type**: scan a QR code / barcode and type its text on the host.
- **Keyboard**: type with the phone IME, plus a special-keys bar.
- **Mouse**: touchpad.

Motivation: existing app (dev.fabik.bluetoothhid) is slow to type scanned
text and sometimes garbles it.

Primary use: **occasional long payloads** (passwords, Wi-Fi keys, URLs,
config strings). Exactness first, speed second.

Success criteria:
- 200-char random password typed into Notepad/TextEdit identical, first try,
  in ≤ 5 s at Normal speed.
- Connection survives switching between app screens and the camera.
- No stuck modifiers after cancel or disconnect.

Hosts: Windows, macOS, Linux. Host layouts: US QWERTY, FR AZERTY
(Windows/Linux), FR AZERTY (Mac).

Constraints: minSdk 36, targetSdk 37, Kotlin + Compose + Material3. No
network, no analytics. UI strings EN (`values/`) + FR (`values-fr/`),
key for key.

Out of scope v1: Android/iOS/TV hosts tuning, layouts other than the three
above, BLE HID (HOGP), custom full on-screen PC keyboard.

## 2. Structure (ClassLock conventions)

Per-activity folders, app-agnostic `connectivity/` framework, preferences
provider interface.

```
com/fnosari/bluetoothhid/
├─ activities/
│  ├─ main/MainActivity.kt          enum Routes { HOME, DEVICES }
│  │   screens/HomeScreen.kt        connection card + tiles Scan/Keyboard/Mouse/Settings
│  │   screens/DevicesScreen.kt     bonded hosts, pair new (discoverable), connect/disconnect
│  ├─ scan/ScanActivity.kt
│  │   screens/ScanScreen.kt        camera + preview sheet
│  │   components/QrAnalyzer.kt     CameraX ImageAnalysis + ML Kit bundled scanner
│  ├─ keyboard/KeyboardActivity.kt
│  │   screens/KeyboardScreen.kt    IME capture + text block send
│  │   components/SpecialKeysBar.kt
│  ├─ mouse/MouseActivity.kt
│  │   screens/TouchpadScreen.kt
│  └─ settings/SettingsActivity.kt  enum Routes
│      screens/SettingsScreen.kt
├─ connectivity/hid/                app-agnostic HID framework
│  ├─ HIDDevice.kt                  BluetoothHidDevice wrapper, CallBack + ExitCode
│  ├─ HIDException.kt
│  ├─ HIDDescriptor.kt              composite report descriptor
│  ├─ HIDReport.kt                  sealed: Keyboard / Mouse / Consumer → ByteArray
│  ├─ HIDReportQueue.kt             paced single-thread sender
│  └─ layouts/
│     ├─ KeyboardLayout.kt          interface + KeyStroke + HIDKeys usage constants
│     ├─ UsLayout.kt, FrAzertyLayout.kt, FrMacLayout.kt
│     └─ TextTyper.kt               text → List<HIDReport.Keyboard>
├─ model/Host.kt, TypingSpeed.kt
├─ persistance/preferences/         PreferencesProvider, DataStorePreferencesProvider, Preferences (keys)
├─ services/HIDForegroundService.kt owns HIDDevice + HIDReportQueue, binder API
├─ ui/layouts/BasicLayoutWithTopBar.kt, ui/theme/
└─ utils/BluetoothPermissions.kt
```

## 3. HID framework

### 3.1 Descriptor (fixed from v1 so hosts never need re-pairing)

| Report ID | Device | Payload |
|---|---|---|
| 1 | Keyboard | modifiers (1 B), reserved (1 B), 6 key usages (6 B) |
| 2 | Mouse | buttons (1 B, 3 bits), dx (int8), dy (int8), wheel (int8), AC pan (int8) |
| 3 | Consumer | 16-bit usage (1 slot) |

Keyboard also declares LED output report (ignored).
SDP: name "Bluetooth HID", subclass `SUBCLASS1_COMBO`.

### 3.2 HIDDevice

- `getProfileProxy(HID_DEVICE)`, `registerApp(sdp, null, null, executor, callback)`.
- `connect(device)`, `disconnect()`, `sendReport(id, bytes): Boolean`.
- `HIDDeviceCallBack`: `onAppRegistered(Boolean)`, `onConnectionStateChanged(device, state)`,
  `onError(ExitCode)`.
- `HIDDeviceExitCode`: `ERROR_BLUETOOTH_NOT_SUPPORTED`, `ERROR_BLUETOOTH_NOT_ENABLED`,
  `ERROR_BLUETOOTH_CONNECT_PERMISSION_NOT_GRANTED`, `ERROR_HID_PROFILE_NOT_SUPPORTED`,
  `ERROR_HID_REGISTER_FAILED`, `ERROR_HID_NOT_CONNECTED`, `ERROR_HID_SEND_FAILED`.

### 3.3 HIDReportQueue

- Single `HandlerThread`. Two lanes: **pointer** (mouse/consumer/single keys,
  sent first) and **text** (bulk typing jobs).
- Delay between reports = `TypingSpeed` (Fast 6 ms, Normal 10 ms, Safe 25 ms).
- `sendReport` false → retry after 2× delay, max 3 retries → job fails with
  index of last typed char.
- Job API: `submitText(reports, onProgress(done,total), onDone(Result))`, `cancel()`.
- Cancel, failure or disconnect → clear text lane, send all-keys-up keyboard
  report (best effort).
- Mouse moves coalesced: pending unsent move merged (dx/dy summed, clamped to
  int8 and split if needed).

### 3.4 Layouts + TextTyper

- `KeyboardLayout { id; fun strokeFor(c: Char): List<KeyStroke>? }` —
  list allows dead-key sequences (FR `ê` = `^` dead key then `e`).
  `KeyStroke(usage, modifiers)`.
- US: all printable ASCII. FR (Win/Linux): printable ASCII + `é è à ç ù ²
  € £ µ § ° ¨`, AltGr = Right Alt. Accented circumflex/trema via dead keys.
  FR Mac: ASCII + same accents, Mac positions (`@` `#` `|` `~` `{` `}` `[` `]` `\`).
- Specials: `\n` → Enter, `\r\n` → single Enter, `\t` → Tab.
- `TextTyper.type(text, layout): TypedText(reports, unmappable: List<Int>)`:
  - per stroke: press report `(mods, [usage])`.
  - insert an all-up report only when next stroke has same usage, or different
    modifiers, or after a dead-key stroke; otherwise next press replaces the key.
  - ends with all-up report.
  - unmappable chars skipped, positions reported.

## 4. Service

`HIDForegroundService` (type `connectedDevice`), started by MainActivity on
launch, stopped by "Quit" in notification or Home menu.
Binder exposes: `state: StateFlow<HIDState>` (Unavailable(code) / Idle /
Connecting(host) / Connected(host) / Typing(done,total)), `connect`, `disconnect`,
`makeDiscoverable`, `typeText(text)`, `sendKey(usage, mods)`, `sendConsumer`,
`mouseMove`, `mouseButtons`, `mouseScroll`, `cancelTyping`.
Layout resolved per connected host MAC from preferences (default from system
locale: FR → FR Win, else US).
Notification: host name + Disconnect action.

## 5. Screens

- **Home**: connection card (state, host, layout chip → change), tiles.
  Auto-reconnect last host on open (setting, default on).
- **Devices**: bonded devices list, connect/disconnect, "Pair new host"
  (discoverable 120 s + instructions per OS).
- **Scan**: CameraX preview, ML Kit (QR + all 1D/2D formats). On detection:
  haptic, pause analysis, bottom sheet: monospace text, char count, unmappable
  highlighted red, layout chip, **Type** / Copy / Rescan. Typing: progress + Cancel.
  Settings: auto-type on scan, Enter at end.
- **Keyboard**: focused text field, IME open. Live mode: diff old/new text —
  appended chars typed, removed chars → Backspace ×n. Block mode: compose
  text then Send. Special keys bar (horizontal scroll): Esc Tab arrows Home End
  PgUp PgDn Del Ins, sticky Ctrl/Alt/Shift/Win(⌘), F1–F12, Ctrl+A/C/V/X/Z,
  Alt+Tab, Ctrl+Alt+Del, media (vol −/+, mute, play/pause).
- **Mouse**: touchpad. 1 finger move with acceleration; tap = left click;
  2-finger tap = right click; 2-finger vertical drag = wheel; long press =
  drag lock; left/right buttons. Sensitivity setting.
- **Settings**: typing speed, default layout, auto-type, Enter at end,
  auto-reconnect, mouse sensitivity, language (EN/FR), about.

## 6. Error handling

| Situation | Behavior |
|---|---|
| BT off | Prompt system enable |
| Permission denied | Card explaining + button to app settings |
| HID profile missing (OEM) | Home shows `ERROR_HID_PROFILE_NOT_SUPPORTED` message |
| Not connected | Send actions disabled, banner |
| Send fails ×3 | Typing aborted, keys released, snackbar "Interrupted at char N" + Resume |
| Disconnect while typing | Same as above |
| Unmappable chars | Shown before send, skipped |

## 7. Testing

JUnit (JVM) in `app/src/test/.../connectivity/hid/`:
- Layouts: every printable ASCII maps on US/FR/FR-Mac; known positions
  (FR `a`→Q usage, `@`→AltGr+0, Mac `@`→`<` key …); accents on FR.
- TextTyper: smart key-up (`ab`, `aa`, `aA`, dead keys), newline/tab,
  unmappable positions, final all-up.
- HIDReport bytes, descriptor sanity (report IDs present, balanced collections).

Manual on device: pair with Windows, macOS, Linux; 200-char password into a
text editor at each speed; mouse/scroll; disconnect mid-typing.

Build check: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
