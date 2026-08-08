# Build OpenSw for Android

## Requirements

- Android Studio with SDK 36
- Android NDK `28.2.13676358`
- JDK 17
- Git submodules initialised

From `src/android`, build the normal installable Release APK:

```sh
./gradlew :app:assembleOpenSwRelease
```

The output is `app/build/outputs/apk/openSw/release/app-openSw-release.apk` and installs as
`com.remipelloux.opensw`. Use `adb install -r` for an in-place update. Confirm the APK certificate
matches the installed package before updating; never uninstall or clear app data when preserving
saves, settings, cheats and shader caches.

Build the final APK after committing the source. OpenSw derives its version name from Git `HEAD`, so
an APK built while a merge is still uncommitted identifies as the previous revision. Verify the APK
package, version, certificate and SHA-256 before installation, then confirm that Android reports the
same `firstInstallTime` and private data directory after `adb install -r`.

Build the isolated, profileable measurement APK with:

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

The Release and Profile variants are different applications. Device profiling may use
`com.remipelloux.opensw.profile`, but the normal emulator is updated only by installing the Release
APK over `com.remipelloux.opensw`; installing Profile never updates the normal app.
