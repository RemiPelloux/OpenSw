# OpenSw

OpenSw is a standalone Android Switch emulator with a generic ARM64 build and an optional AYN Thor
performance profile.

## Isolation

- App name: `OpenSw`
- Base package: `com.remipelloux.opensw`
- Recommended test variant: `openSwProfile`
- Debug package: `com.remipelloux.opensw.debug`

Android gives each OpenSw variant its own private and external app-data directory.

## Build

From `src/android`:

```sh
./gradlew assembleOpenSwProfile
```

The output APK is written below `src/android/app/build/outputs/apk/openSw/profile/`.

## Device testing

Before installation, verify the connected device and inspect the APK package name. Testing must stay
scoped to OpenSw and must never uninstall, clear or write to another application's package. Legacy
data import must use the Android system document picker and a user-granted read-only permission.
