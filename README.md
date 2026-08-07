<!--
# SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
# SPDX-License-Identifier: GPL-3.0-or-later

# SPDX-FileCopyrightText: Copyright 2025 Eden Emulator Project
# SPDX-License-Identifier: GPL-3.0-or-later

# SPDX-FileCopyrightText: 2018 yuzu Emulator Project
# SPDX-License-Identifier: GPL-2.0-or-later
-->
<!-- lang: en-GB -->

<p align="center">
  <img src="./branding/opensw-monogram.svg" alt="OpenSw Android emulator logo" width="200">
</p>

<h1 align="center">OpenSw Android Nintendo Switch Emulator</h1>

<p align="center">
  Open-source ARM64 emulator for Android with a Vulkan renderer, per-game profiles, live cheats,
  dual-screen controls and reproducible performance diagnostics.
</p>

<p align="center">
  <a href="./LICENSE.txt"><img alt="License: GPL-3.0-or-later" src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue"></a>
  <img alt="Platform: Android ARM64" src="https://img.shields.io/badge/platform-Android%20ARM64-3DDC84">
  <img alt="Renderer: Vulkan" src="https://img.shields.io/badge/renderer-Vulkan-AC162C">
</p>

<p align="center">
  <a href="#features">Features</a> |
  <a href="#build-from-source">Build</a> |
  <a href="#live-cheats">Live cheats</a> |
  <a href="#app-identity-and-data">App identity and data</a> |
  <a href="#performance-development">Performance</a> |
  <a href="#testing">Testing</a> |
  <a href="#roadmap">Roadmap</a> |
  <a href="#based-on-eden">Based on Eden</a> |
  <a href="#community-and-project-policy">Community</a> |
  <a href="#license">License</a>
</p>

OpenSw targets generic Android ARM64 hardware. AYN Thor support is an optional, reversible profile,
not a requirement and not a device-wide overclock. OpenSw does not include games, encryption keys or
firmware; users must provide content they are legally entitled to use.

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

The maintained Android targets build as `openSwRelease` and `openSwProfile`. The normal installable
application is `com.remipelloux.opensw`; Profile is an isolated measurement variant and is not the
public runtime package.

Repeated-session shutdown now rejects callbacks from older native generations and has focused fixes
for cheat callbacks, audio streams, emulated backing memory, page tables, process trackers, guest
service-thread references and Vulkan presentation teardown. Six Foretales
launch/pause/resume/capture/stop/restart cycles completed on the Thor without crash, ANR, mixed
Title ID or continuous RSS growth after settling. The 30-cycle acceptance run, physical rotation
coverage and the 45-60 minute Arceus session remain required before release certification.

The Profile build preserves 17 compatible JNI counters and appends per-draw Vulkan diagnostics,
including presentation queue depth and time spent waiting for a free frame, the scheduler,
swapchain acquisition and presentation. Release
builds hide those counters while retaining the compact Direct view on the Thor secondary display.

Vulkan direct draws now emulate Maxwell `LineLoop` with a line strip and an explicit closing index,
instead of incorrectly treating the vertices as triangles. The implementation and audited fallback
behavior are documented in [Vulkan rendering compatibility](./docs/opensw/Rendering.md).

## Build from source

OpenSw does not currently publish GitHub Release binaries. Build the normal generic ARM64 APK with
the documented Android SDK, NDK and JDK versions:

```sh
cd src/android
./gradlew :app:assembleOpenSwRelease
```

The APK is written to
`src/android/app/build/outputs/apk/openSw/release/app-openSw-release.apk`. Installing it with
`adb install -r` updates `com.remipelloux.opensw` in place when the signing certificate matches.
Never uninstall or clear the package when the intent is to preserve saves and configuration.

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

## Performance development

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

Nintendo Switch is a trademark of Nintendo. OpenSw is an independent open-source project and is not
affiliated with, endorsed by or supported by Nintendo or the Eden Emulator Project.

## Community and project policy

- Read the [documentation index](./docs/README.md) and [OpenSw handbook](./docs/opensw/README.md).
- Report reproducible defects with the [GitHub issue template](https://github.com/RemiPelloux/OpenSw/issues/new/choose).
- Discuss usage and proposals in [GitHub Discussions](https://github.com/RemiPelloux/OpenSw/discussions).
- Review [CONTRIBUTING.md](./CONTRIBUTING.md), [SECURITY.md](./SECURITY.md),
  [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) and [CHANGELOG.md](./CHANGELOG.md).

## License

OpenSw remains licensed under GPLv3 (or any later version). Refer to
[LICENSE.txt](./LICENSE.txt).
