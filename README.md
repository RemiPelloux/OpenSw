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
OpenSw is a standalone Android Switch emulator developed as its own complete project, with a general
ARM64 build and an optional performance path measured on AYN Thor. It combines reversible per-game
profiles, live cheats, a dual-screen cockpit and reproducible performance diagnostics.
</h4>

<p align="center">
  <a href="#features">Features</a> |
  <a href="#live-cheats">Live cheats</a> |
  <a href="#app-identity-and-data">App identity and data</a> |
  <a href="#building">Building</a> |
  <a href="#testing">Testing</a> |
  <a href="#roadmap">Roadmap</a> |
  <a href="#based-on-eden">Based on Eden</a> |
  <a href="#license">License</a>
</p>

## Features

- A self-contained Android emulator with dedicated Release and Profile builds.
- `Standard`, `Thor Balanced`, `Thor 60 stable` and experimental `Thor Max` modes.
- A global default with optional per-Title-ID overrides that never rewrite game configuration files.
- A release-like, locally signed and profileable APK for repeatable AYN Thor measurements.
- An optional read-only legacy import assistant for keys, firmware, profiles, saves, settings, mods
  and cheats.
- An in-game cheat overlay with controller and touch navigation.
- A shared Performance Lab with rolling p95 frametime, memory, power, thermal warnings and
  versioned native diagnostics that are explicitly excluded from A/B promotion decisions.
- Reproducible `opensw-performance-v2` tooling that derives A/B evidence from raw
  SurfaceFlinger/Perfetto timestamps rather than sampled UI averages.
- An automatic cockpit on the Thor secondary display with an in-game drawer fallback.
- Sanitised diagnostic bundles without keys, saves, firmware, game paths or game content.
- Build-ID-aware cheat import from text files, Atmosphere/Eden trees and ZIP archives.
- Optional cheat catalogues backed by `switch-cheats-db` and `NX-60FPS-RES-GFX-Cheats`.
- Perfetto capture tooling and a documented AYN Thor performance baseline.
- A dedicated OpenSw build, update and publication configuration.

## Current status

The maintained Android targets build as `openSwRelease` and `openSwProfile`. Pokemon Legends:
Arceus 1.1.1 has been verified on the AYN Thor with Build ID
`AEE8F150DDA1B5A8`; the live cheat engine applied Mastercode plus the 60 FPS section and a stable
presentation sample measured 59.96 FPS.

Repeated-session shutdown now rejects callbacks from older native generations and has focused fixes
for cheat callbacks, audio streams, emulated backing memory, page tables, process trackers, guest
service-thread references and Vulkan presentation teardown. Six Foretales
launch/pause/resume/capture/stop/restart cycles completed on the Thor without crash, ANR, mixed
Title ID or continuous RSS growth after settling. The 30-cycle acceptance run, physical rotation
coverage and the 45-60 minute Arceus session remain required before release certification.

The Profile build exposes 17 compatible JNI counters, including presentation queue depth and time
spent waiting for a free frame, the scheduler, swapchain acquisition and presentation. Release
builds hide those counters while retaining the compact Direct view on the Thor secondary display.

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

## App identity and data

OpenSw uses `com.remipelloux.opensw`; debug builds use `com.remipelloux.opensw.debug`. Neither
variant shares private app data with another application.

The OpenSw modes only affect OpenSw. No mode changes Android, CPU/GPU frequencies, unsafe memory
settings or data owned by another application. `Standard` restores the exact values captured before
a Thor mode was enabled.

The optional legacy migration flow uses Android's document picker and a user-granted read-only
source. It stages and verifies copied files before installing them into OpenSw; game files, caches,
logs and temporary data are not copied.

## Building

Build the installable Release and the locally signed profiling APK from `src/android`:

```sh
./gradlew :app:assembleOpenSwRelease :app:assembleOpenSwProfile
```

The APKs are produced under `src/android/app/build/outputs/apk/openSw/release` and
`src/android/app/build/outputs/apk/openSw/profile`.

Build and archive the generic, ARMv9, ThinLTO and ARMv9 + ThinLTO measurement APKs with:

```sh
tools/performance/build-opensw-matrix.sh
```

## Testing

See the [OpenSw handbook](./docs/opensw/README.md) and [OPENSW.md](./OPENSW.md) for product,
package and device-testing rules. Performance acceptance and the fixed AYN Thor scenarios are in
[docs/performance/ayn-thor-baseline.md](./docs/performance/ayn-thor-baseline.md).

Session lifetime findings, valid measurements and rejected measurements are recorded in
[docs/opensw/SessionStability.md](./docs/opensw/SessionStability.md).

The current Thor evidence, APK hashes and remaining user validation are recorded in the
[AYN Thor audit](./docs/performance/ayn-thor-audit.md) and
[performance roadmap](./docs/performance/roadmap.md).

The current device baseline covers an AYN Thor with Snapdragon 8 Gen 2 and Adreno 740. Performance
changes are promoted only after repeatable A/B measurements; risky compiler flags and Android clock
changes are intentionally excluded.

## Roadmap

The ordered engineering roadmap and its acceptance gates are maintained in
[docs/opensw/Roadmap.md](./docs/opensw/Roadmap.md), with campaign status in
[docs/performance/roadmap.md](./docs/performance/roadmap.md). Stability and deterministic device
automation come before new performance flags or additional UI features.

## Based on Eden

OpenSw is [based on Eden](https://git.eden-emu.dev/eden-emu/eden) and retains the Eden/yuzu source
history, copyrights and GPL notices. It is not affiliated with or supported by the upstream Eden
Emulator Project.

Unverified desktop guides are retained as clearly labelled upstream documentation under
[docs](./docs). OpenSw-specific documentation lives under [docs/opensw](./docs/opensw).

## License

OpenSw remains licensed under GPLv3 (or any later version). Refer to
[LICENSE.txt](./LICENSE.txt).
