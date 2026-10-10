# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## What this is

Android app (`com.fnosari.bluetoothhid`, Kotlin + Jetpack Compose, minSdk 36) that makes the phone a Bluetooth HID keyboard, mouse and presentation remote via `BluetoothHidDevice`. Single `:app` module. Apache-2.0.

## Commands

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug   # the build check — all must pass, lint 0 errors
./gradlew :app:testDebugUnitTest --tests "*TextTyperTest"            # one test class
./gradlew :app:installDebug                                          # install on the connected phone
```

Windows: if a build fails with `AccessDeniedException` / `Unable to delete directory` under `app/build`, run `./gradlew --stop`, delete `app/build`, rebuild. That's a file lock outside the build, not a code error.

## Architecture (where things go)

- `activities/<feature>/` — one Activity per feature (`main`, `scan`, `keyboard`, `mouse`, `presenter`, `settings`) with `screens/` and `components/`. MainActivity uses Navigation with `enum class Routes`.
- `connectivity/hid/` — app-agnostic HID framework, pure Kotlin except `HIDDevice`:
  - `HIDDescriptor` (report IDs: keyboard 1 / mouse 2 / consumer 3), `HIDReport`, `HIDKeys`
  - `HIDReportQueue` — single worker thread, pacing, retry, cancel/fail, mouse coalescing
  - `layouts/` — `KeyboardLayout` tables (us, fr-win, fr-linux, fr-mac) + `TextTyper`
  - `PresenterKeys`
- `services/HIDForegroundService` — owns `HIDDevice` + queue; exposes `StateFlow`s; activities bind via `utils/rememberHIDService()`.
- `persistance/preferences/` — `PreferencesProvider` + DataStore impl; keys in `Preferences`.
- `ui/components/` shared composables, `ui/layouts/BasicLayoutWithTopBar`, `utils/` pure helpers.

## Rules

1. **Never change `HIDDescriptor`.** Hosts cache it at pairing; any change breaks every paired computer until it's re-paired.
2. **No stuck keys.** Every path that ends typing (cancel, failure, disconnect, Bluetooth off) must end with an all-keys-up report.
3. **TDD for logic.** Logic goes in pure Kotlin (`connectivity/hid/`, `utils/`) with a JUnit test written and seen failing first. Activities/Compose stay thin.
4. **Strings in pairs.** Every user-visible string lives in `res/values/strings.xml` **and** `res/values-fr/strings.xml` with identical keys. No hard-coded UI text (key-cap labels like `Esc`/`F1` are the only exception). Check parity:
   `diff <(grep -o 'name="[^"]*"' app/src/main/res/values/strings.xml | sort) <(grep -o 'name="[^"]*"' app/src/main/res/values-fr/strings.xml | sort)`
5. **No network.** `INTERNET`/`ACCESS_NETWORK_STATE` are stripped in the manifest (`tools:node="remove"`); keep it that way, no analytics.
6. **Layouts** map characters by US key position; new layouts need a test like `FrAzertyLayoutTest` and an entry in `TextTyperTest.randomPasswordsRoundTrip`.
7. **Verify on a real phone** for anything touching HID — the emulator registers the HID app but cannot type into a real host. Wireless debugging: `adb connect <ip:port>`; screenshot with `adb exec-out screencap -p > shot.png`. Do not send keys to the host computer without the user's OK: they land in whatever window has focus.
8. Commits: Conventional Commits; update `CHANGELOG.md` under *Unreleased*.

## Gotchas

- System "touch feedback" off silences `performHapticFeedback`; Presenter uses `SlideVibrator` (media usage) instead.
- A host paired while another HID app was active keeps that app's descriptor → reports ignored. Fix: unpair on both sides, pair again.
- `DataStorePreferencesProvider` reads with `runBlocking` (ClassLock pattern) — fine for small reads, avoid in hot paths.

Design history: `docs/superpowers/specs/` and `docs/superpowers/plans/` (historical, don't rewrite).
