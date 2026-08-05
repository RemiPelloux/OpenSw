<!--
# SPDX-FileCopyrightText: Copyright 2025 Eden Emulator Project
# SPDX-License-Identifier: GPL-3.0-or-later

# SPDX-FileCopyrightText: 2018 yuzu Emulator Project
# SPDX-License-Identifier: GPL-2.0-or-later
-->
<!-- lang: en-GB -->

<h1 align="center">
  <br>
  <img src="./branding/opensw-monogram.svg" alt="OpenSw" width="200">
  <br>
  <b>OpenSw</b>
  <br>
</h1>

<h4 align="center">
OpenSw is an unofficial Android emulator test fork for AYN Thor devices. It is designed for
reversible performance experiments, safe Eden data import and in-game cheat controls.
</h4>

<p align="center">
  <a href="#isolation">Isolation</a> |
  <a href="#building">Building</a> |
  <a href="#testing">Testing</a> |
  <a href="#based-on-eden">Based on Eden</a> |
  <a href="#license">License</a>
</p>

## Isolation

OpenSw uses `com.remipelloux.opensw`; debug builds use `com.remipelloux.opensw.debug`. Neither
package replaces or shares private app data with `dev.eden.eden_emulator.nightly`.

The `Standard`, `AYN Thor` and `Experimental` modes only affect OpenSw. No mode changes Android,
device frequencies or the official Eden installation.

## Building

Build the locally signed, release-like profiling APK from `src/android`:

```sh
./gradlew :app:assembleOpenSwProfile
```

The APK is produced at
`src/android/app/build/outputs/apk/openSw/profile/app-openSw-profile.apk`.

## Testing

See [OPENSW.md](./OPENSW.md) for package and device-testing rules. Performance acceptance and the
fixed AYN Thor scenarios are documented in
[docs/performance/ayn-thor-baseline.md](./docs/performance/ayn-thor-baseline.md).

## Based on Eden

OpenSw is [based on Eden](https://git.eden-emu.dev/eden-emu/eden) and retains the Eden/yuzu source
history, copyrights and GPL notices. It is not affiliated with or supported by the upstream Eden
Emulator Project.

The upstream documentation remains available under [docs](./docs) and
[CONTRIBUTING.md](./CONTRIBUTING.md).

## License

OpenSw remains licensed under GPLv3 (or any later version). Refer to
[LICENSE.txt](./LICENSE.txt).
