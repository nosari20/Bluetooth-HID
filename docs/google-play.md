# Google Play listing

Everything needed to publish **Bluetooth HID** on Google Play. Listing texts and graphics live in [`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/) (the standard layout read by fastlane `supply`, F-Droid and most store tools). You can also copy them into the Play Console by hand.

## Store listing

| Field | Value / file |
|---|---|
| App name (≤30) | `title.txt` — *Bluetooth HID: Keyboard, Mouse* |
| Short description (≤80) | `short_description.txt` |
| Full description (≤4000) | `full_description.txt` (uses Play's `<b>` tags) |
| App icon 512×512 | `images/icon.png` |
| Feature graphic 1024×500 | `images/featureGraphic.png` |
| Phone screenshots (2–8, 9:16) | `images/phoneScreenshots/1_home.png` … `7_settings.png` (1080×1920) |
| Release notes (≤500) | `changelogs/1.txt` (file name = `versionCode`) |
| Category | **Tools** |
| Tags | Keyboard, Remote control, Productivity |
| Contact email | *(required — your support address)* |
| Website | https://nosari20.github.io/apps/bluetooth-hid/ |
| Privacy policy | https://nosari20.github.io/apps/bluetooth-hid/privacy/ (copy of `PRIVACY.md`, keep in sync) |

Screenshots were captured on a Pixel 10 Pro XL (demo-mode status bar) and framed to 9:16, because raw 1080×2404 captures exceed Play's 2:1 aspect-ratio limit.

## App content (Policy › App content)

**Privacy policy** — https://nosari20.github.io/apps/bluetooth-hid/privacy/

**Ads** — No, the app contains no ads.

**App access** — All functionality is available without an account. Note for reviewers: *"Pair the phone with a computer via Hosts › Pair new host to use typing, mouse and presenter features."*

**Content rating** (IARC questionnaire) — Category *Utility, productivity, communication or other*. Answer **No** to every content question (violence, sexuality, language, controlled substances, gambling, user interaction/sharing, location sharing, digital purchases). Expected rating: **Everyone / PEGI 3**.

**Target audience** — 13 and over (or 18+). Do not select under-13 ages, to stay out of the Families program.

**News app** — No. **COVID-19 app** — No. **Government app** — No. **Financial features** — None. **Health** — None.

### Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no data collected) |
| Do you provide a way for users to request that their data is deleted? | Not applicable (all data stays on the device; uninstalling deletes it) |

Why "No" is accurate: the app has no internet permission (ML Kit's `INTERNET`/`ACCESS_NETWORK_STATE` are removed in the manifest). Camera frames are decoded on-device by the bundled ML Kit model and never stored. Typed text is sent only over Bluetooth to the computer the user connected. Preferences (layout per computer, settings) stay in app storage on the phone. Data that never leaves the device is not "collected" under Play's definition.

### Foreground service declaration

Play asks for this because the app declares `FOREGROUND_SERVICE_CONNECTED_DEVICE`. The permission is allowed only if the app "performs tasks noticeable to the user when they're not directly interacting with your app". This app does:

- **Typing continues after the user leaves the app.** A long scanned text (password, Wi-Fi key, configuration string) keeps being typed on the computer if the user switches to another app mid-way. The characters keep appearing on the computer screen.
- **The computer keeps its keyboard and mouse.** While the user is in another app, the paired computer still sees the phone as a connected Bluetooth keyboard/mouse. Stopping the service would disconnect it on the computer.
- **Always visible.** A persistent notification names the connected computer and offers *Disconnect* and *Quit*.

Answers for *App content › Foreground service permissions*:

- **Task type:** Connected device (interaction with an external device over Bluetooth).
- **Description:** "Bluetooth HID turns the phone into a Bluetooth keyboard and mouse for the user's computer. After the user connects a computer, a foreground service keeps that Bluetooth HID connection open and finishes typing long text the user started, for example a scanned password, even if the user switches to another app. The connection is user-initiated, shown by a persistent notification naming the computer with Disconnect and Quit actions, and ends when the user disconnects or quits."
- **User impact if the task is deferred or interrupted:** "The computer immediately loses its keyboard and mouse. Text being typed is cut off mid-way, so a password or configuration string ends up partially typed on the computer."
- **Video** (unlisted YouTube or Drive link): connect a computer → Scan & type a long QR text → press Home mid-typing and show the text still arriving on the computer → pull down the notification (computer name, Disconnect/Quit) → tap Disconnect.

### Permissions

No restricted permissions (SMS, call log, location, accessibility, all-files) are used, so no extra declaration forms are needed. Nearby devices (Bluetooth), Camera and Notifications are standard runtime permissions explained in [PRIVACY.md](../PRIVACY.md).

## Release checklist

1. **Upload key + signing:** Play needs a signed **AAB**. Create an upload keystore (`keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`), keep it **out of git**, add a `release` `signingConfig` that reads it from `~/.gradle/gradle.properties` or environment variables, and enable Play App Signing.
2. Build: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
3. Bump `versionCode`/`versionName` in `app/build.gradle.kts` for every upload, and add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
4. Start with **Internal testing**. New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before production access.
5. Device catalog: the app requires Android 16 (API 36) and Bluetooth, so older phones won't see it on the store.

The application ID `com.fnosari.bluetoothhid` cannot change after the first upload.

## Regenerating the screenshots

1. Phone connected over adb, app in the state to capture, demo-mode status bar:
   `adb shell settings put global sysui_demo_allowed 1`
   `adb shell am broadcast -a com.android.systemui.demo -e command enter`
   `adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930`
2. `adb exec-out screencap -p > raw/<name>.png` for each screen. Use Keyboard **Block** mode for sample text, so nothing is sent to the computer.
3. Frame each capture at 1080×1920 with a title above the phone (teal `#0D9488` background, white title, `#CCFBF1` subtitle).
4. Never publish the *Hosts* screen: it lists your other Bluetooth devices and their MAC addresses.
