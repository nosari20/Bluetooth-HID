## What

<!-- What does this change and why? Link the issue: Fixes #123 -->

## How tested

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passes
- [ ] Tested on a real phone + host (phone model / Android version / host OS + keyboard layout):

## Checklist

- [ ] New logic has JUnit tests written first
- [ ] Strings added to both `values/strings.xml` and `values-fr/strings.xml`
- [ ] `HIDDescriptor` unchanged (changing it breaks every paired computer)
- [ ] `CHANGELOG.md` updated under *Unreleased*
