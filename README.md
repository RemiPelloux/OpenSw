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
OpenSw is an Android Switch emulator fork with a general ARM64 build and a performance path
measured on AYN Thor. It combines reversible per-game profiles, live cheats, a dual-screen cockpit
and reproducible performance diagnostics.
</h4>

<p align="center">
  <a href="#features">Features</a> |
  <a href="#live-cheats">Live cheats</a> |
  <a href="#isolation">Isolation</a> |
  <a href="#building">Building</a> |
  <a href="#testing">Testing</a> |
  <a href="#based-on-eden">Based on Eden</a> |
  <a href="#license">License</a>
</p>

## Features

- A separate `OpenSw` Android package that can coexist with the official Eden nightly build.
- `Standard`, `Thor Balanced`, `Thor 60 stable` and experimental `Thor Max` modes.
- A global default with optional per-Title-ID overrides that never rewrite game configuration files.
- A release-like, locally signed and profileable APK for repeatable AYN Thor measurements.
- A read-only Eden import assistant for keys, firmware, profiles, saves, settings, mods and cheats.
- An in-game cheat overlay with controller and touch navigation.
- A shared Performance Lab with p95 frametime, memory, power, thermal warnings and A/B reports.
- An automatic cockpit on the Thor secondary display with an in-game drawer fallback.
- Sanitised diagnostic bundles without keys, saves, firmware, game paths or game content.
- Build-ID-aware cheat import from text files, Atmosphere/Eden trees and ZIP archives.
- Optional cheat catalogues backed by `switch-cheats-db` and `NX-60FPS-RES-GFX-Cheats`.
- Perfetto capture tooling and a documented AYN Thor performance baseline.
- Eden's updater and publication configuration disabled in OpenSw builds.

## Live cheats

Open the in-game menu and select **Cheats** to enable or disable compatible codes without restarting
the emulator. The panel shows the current Title ID and Build ID, supports search and active-only
filtering, and keeps the game running behind it.

OpenSw loads cheats only when their 16-character Build ID exactly matches the game's main NSO. The
Mastercode is enabled and locked automatically; ordinary cheats default to off. State is stored by
Title ID, Build ID and opcode fingerprint, so changed remote codes do not inherit an old enabled
state. Disabling a cheat stops future executions but cannot undo memory writes that already occurred;
some codes can therefore still require a game restart.

Catalogue installation is explicit and never silently replaces a locally modified file. Imports are
validated, size-limited, protected against ZIP path traversal and installed atomically with backups.

## Isolation

OpenSw uses `com.remipelloux.opensw`; debug builds use `com.remipelloux.opensw.debug`. Neither
package replaces or shares private app data with `dev.eden.eden_emulator.nightly`.

The OpenSw modes only affect OpenSw. No mode changes Android, CPU/GPU frequencies, unsafe memory
settings or the official Eden installation. `Standard` restores the exact values captured before a
Thor mode was enabled.

The Eden migration flow uses Android's document picker and a user-granted read-only source. It stages
and verifies copied files before installing them into OpenSw; game files, caches, logs and temporary
data are not copied.

## Building

Build the locally signed, release-like profiling APK from `src/android`:

```sh
./gradlew :app:assembleOpenSwProfile
```

The APK is produced at
`src/android/app/build/outputs/apk/openSw/profile/app-openSw-profile.apk`.

Build and archive the generic, ARMv9, ThinLTO and ARMv9 + ThinLTO measurement APKs with:

```sh
tools/performance/build-opensw-matrix.sh
```

## Testing

See the [OpenSw handbook](./docs/opensw/README.md) and [OPENSW.md](./OPENSW.md) for product,
package and device-testing rules. Performance acceptance and the fixed AYN Thor scenarios are in
[docs/performance/ayn-thor-baseline.md](./docs/performance/ayn-thor-baseline.md).

The current device baseline covers an AYN Thor with Snapdragon 8 Gen 2 and Adreno 740. Performance
changes are promoted only after repeatable A/B measurements; risky compiler flags and Android clock
changes are intentionally excluded.

## Based on Eden

OpenSw is [based on Eden](https://git.eden-emu.dev/eden-emu/eden) and retains the Eden/yuzu source
history, copyrights and GPL notices. It is not affiliated with or supported by the upstream Eden
Emulator Project.

Unverified desktop guides are retained as clearly labelled upstream documentation under
[docs](./docs). OpenSw-specific documentation lives under [docs/opensw](./docs/opensw).

## License

OpenSw remains licensed under GPLv3 (or any later version). Refer to
[LICENSE.txt](./LICENSE.txt).
