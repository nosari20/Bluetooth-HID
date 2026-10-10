---
name: add-keyboard-layout
description: Add a new host keyboard layout (e.g. German, Belgian, UK) to the HID typing engine, test-first. Use when asked to support a new computer keyboard layout or fix a layout mapping.
---

# Add a keyboard layout

The computer's layout decides which character a key position prints, so each layout is a table: character → key strokes (US key position + modifiers).

## Steps

1. **Collect facts first.** For the target OS (Windows / Linux xkb / macOS differ!), list for every printable ASCII char and the layout's own accented chars: key position (by US name), and Shift / AltGr (`HIDModifiers.R_ALT`) / Option (`HIDModifiers.L_ALT`, Mac) needs. Note which keys are **dead keys** on that OS (they need `seq(char, deadStroke, spaceStroke)`).
2. **Write the test first** — `app/src/test/java/com/fnosari/bluetoothhid/connectivity/hid/layouts/<Name>LayoutTest.kt`, modeled on `FrAzertyLayoutTest`:
   - id is correct
   - every printable ASCII (32..126) is mappable
   - 5–10 known positions (letters that move, digit row, AltGr symbols)
   - dead-key sequences
   Run `./gradlew :app:testDebugUnitTest --tests "*<Name>LayoutTest"` → must fail to compile.
3. **Implement** `connectivity/hid/layouts/<Name>Layout.kt` extending `TableKeyboardLayout("<id>")`, using `pair()`, `key()`, `seq()`. Ids look like `de-win`, `de-linux`, `de-mac`.
4. **Register** in `Layouts.ALL`; add the label to `ui/components/HIDUiState.kt#layoutLabel` and a `layout_<id>` string in **both** `values/strings.xml` and `values-fr/strings.xml`. If the phone language should default to it, update `Layouts.defaultIdFor` (+ test).
5. Add the layout to the list in `TextTyperTest.randomPasswordsRoundTrip`.
6. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` — all green.
7. **Verify on a real host** with that layout: Keyboard › Block mode, send the full-ASCII line (Task 18 of `docs/superpowers/plans/2026-10-09-bluetooth-hid.md`) plus the layout's accents into a text editor and compare. Ask the user before sending keys to their computer.
8. `CHANGELOG.md` (*Unreleased › Added*), commit `feat(hid): add <name> keyboard layout`.

Never touch `HIDDescriptor` for a layout change.
