# Build OpenSw for Android

## Requirements

- Android Studio with SDK 36
- Android NDK `28.2.13676358`
- JDK 17
- Git submodules initialised

From `src/android`, build the profileable release-like APK:

```sh
./gradlew :app:assembleOpenSwProfile
```

The output is `app/build/outputs/apk/openSw/profile/app-openSw-profile.apk`. It is signed with the
local debug key unless release signing variables are supplied, and installs as
`com.remipelloux.opensw.profile`.

Build a measurement variant explicitly:

```sh
./gradlew :app:assembleOpenSwProfile -PopenswCpuPreset=armv9 -PopenswLtoMode=thin
```

Supported values are `generic|armv9` and `off|thin`. The public default remains generic ARMv8-A.
Use `tools/performance/build-opensw-matrix.sh` to archive all four combinations with SHA-256 files.

The optional measurement controller is built with
`:lab-agent:assembleDebug :lab-agent:assembleDebugAndroidTest`. Both APKs are debug-only and signed
with the same local key as OpenSw Profile so Android can enforce the bridge's signature permission.
OpenSw Release does not contain the service or permission.
