# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.1] - 2026-10-10

### Changed
- The connection service is a foreground service (with notification) only while a computer is connecting/connected or text is being typed; it stops when idle and the app is closed (Google Play foreground-service policy).
- Privacy policy hosted at https://nosari20.github.io/apps/bluetooth-hid/privacy/, with a contact section.

### Changed
- New flat app icon (keyboard + mouse + Bluetooth badge) with a monochrome layer for themed icons; legacy bitmap launcher icons removed.

## [1.0.0] - 2026-10-10

### Added
- Bluetooth HID device: composite keyboard, mouse (wheel, horizontal pan) and consumer-control descriptor.
- Scan & type: CameraX + ML Kit (bundled) scanner, preview with unmappable characters highlighted, progress, cancel, resume after interruption, optional auto-type and Enter at end.
- Keyboard: live IME forwarding, block mode, special-keys bar with sticky modifiers, shortcuts and media keys.
- Mouse: touchpad with acceleration, tap/two-finger tap clicks, two-finger scroll, drag lock, hold buttons.
- Presenter: PageDown/PageUp zones, volume keys change slides, PowerPoint video play/pause, media play/pause, vibration feedback, keep-screen-on, YouTube-style screen lock.
- Keyboard layouts: US, French AZERTY (Windows), French AZERTY (Linux), French AZERTY (Mac), with dead-key accents; layout remembered per host.
- Paced report queue (Fast / Normal / Safe) with retries; all keys released on cancel, failure or disconnect.
- Foreground service keeping the connection across screens; auto-reconnect to the last host; follows Bluetooth on/off.
- English and French UI; per-app language setting.

[Unreleased]: ../../compare/v1.0.1...HEAD
[1.0.1]: ../../compare/v1.0.0...v1.0.1
[1.0.0]: ../../releases/tag/v1.0.0
