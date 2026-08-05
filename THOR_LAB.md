# Eden Thor Lab

Eden Thor Lab is an unofficial Android testing fork for the AYN Thor.

## Isolation

- App name: `Eden Thor Lab`
- Base package: `com.remipelloux.edenthorlab`
- Recommended test variant: `thorLabRelWithDebInfo`
- Test package: `com.remipelloux.edenthorlab.relWithDebInfo`

Android gives this package a separate private and external app-data directory. Installing it does not
replace or reuse the official Eden package `dev.eden.eden_emulator.nightly`.

## Build

From `src/android`:

```sh
./gradlew assembleThorLabRelWithDebInfo
```

The output APK is written below `src/android/app/build/outputs/apk/thorLab/relWithDebInfo/`.

## Device testing

Before installation, verify the connected device and inspect the APK package name. Never uninstall,
clear, or migrate data from the official Eden package while testing this fork.
