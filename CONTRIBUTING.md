# Contributing

Thanks for helping! Bug reports, host-compatibility reports, new keyboard layouts and fixes are all welcome.

## Reporting a bug

Open an issue with the bug template. Please include: phone model and Android version, the computer OS and its **keyboard layout**, the layout chosen in the app, and what you typed vs. what appeared.

## Development setup

1. Android Studio (recent stable), Android SDK 37.
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` must pass.
3. Test HID behavior on a **real phone** paired with a real computer — the emulator cannot act as a keyboard for a host.

## Pull requests

- Branch from `main`; keep a PR to one topic.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `chore:` …).
- Logic goes in pure Kotlin (`connectivity/hid/`, `utils/`) with JUnit tests written **before** the code; Android/Compose classes stay thin.
- Every user-visible string goes in `res/values/strings.xml` **and** `res/values-fr/strings.xml` with the same keys. No hard-coded UI text.
- Update `CHANGELOG.md` under *Unreleased*.
- CI (build, unit tests, lint) must be green.

## Rules that protect users

- **Never change `HIDDescriptor`.** Computers cache it at pairing time; a change silently breaks every paired computer until it is re-paired.
- **Never leave keys held.** Any path that stops typing must end with an all-keys-up report (see `HIDReportQueue`).
- **No network access, no analytics.**

## Adding a keyboard layout

1. Create `connectivity/hid/layouts/<Name>Layout.kt` extending `TableKeyboardLayout` with a unique id (e.g. `de-win`). Map characters by **US key position** (`HIDKeys`), with Shift / AltGr (`R_ALT`) / Option (`L_ALT`) modifiers; use `seq()` for dead keys.
2. Register it in `Layouts.ALL` and add its label (`layoutLabel`, both string files).
3. Add a test like `FrAzertyLayoutTest`: every printable ASCII mappable, known key positions, dead keys; and add the layout to `TextTyperTest.randomPasswordsRoundTrip`.
4. Verify on a real host: type the full-ASCII line from `docs/superpowers/plans/2026-10-09-bluetooth-hid.md` (Task 18) and compare.

## License

By contributing you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
