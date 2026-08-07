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

## Profile measurement contract

The `openSwProfile` build reports requested and effective Vulkan pipeline worker counts separately.
The effective value accounts for usable hardware concurrency and driver serialization. Profile
captures reset separate capture-window queue maxima and record actual small-draw wait count and
duration while preserving the original 17 native snapshot fields.

`tools/performance/opensw-performance-v2` requires revision-2 manifests for promotion. A run must
include matching local and installed APK hashes, `opensw-runtime-identity-v1`, replay and cheat-set
hashes, cache state, firmware, driver, display configuration, fan mode, and a calibrated starting
temperature. A/B summaries may differ only in the declared `workers` or `build` experiment fields.
Older manifests remain diagnostic artifacts and are not promotion eligible.

## Lab packages

The Android project contains a shared `:lab-protocol` module and a debug-only `:lab-agent` package,
`com.remipelloux.opensw.lab`. OpenSw Profile alone declares the signature-protected automation
bridge; OpenSw Release contains no bridge service or permission. The bridge accepts structured
worker, exact-Title-ID cache, stop, status, and capture operations. It has no shell, package-manager,
or arbitrary filesystem API and does not query or control the official Eden package.
