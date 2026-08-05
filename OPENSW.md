# OpenSw

OpenSw is an unofficial Android testing fork of Eden for the AYN Thor.

## Isolation

- App name: `OpenSw`
- Base package: `com.remipelloux.opensw`
- Recommended test variant: `openSwProfile`
- Debug package: `com.remipelloux.opensw.debug`

Android gives this package a separate private and external app-data directory. Installing it does not
replace or reuse the official Eden package `dev.eden.eden_emulator.nightly`.

## Build

From `src/android`:

```sh
./gradlew assembleOpenSwProfile
```

The output APK is written below `src/android/app/build/outputs/apk/openSw/profile/`.

## Device testing

Before installation, verify the connected device and inspect the APK package name. Never uninstall,
clear or write to the official Eden package while testing this fork. Eden data import must use the
Android system document picker and a user-granted read-only permission.
