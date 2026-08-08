<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# OpenSw Performance Lab

## Modes

| Profile | Vulkan workers | Async presentation | Optimised vertex buffers | Async GPU/shaders | Scheduling |
|---|---:|---|---|---|---|
| `Standard` | 4 | On | On | On | Hybrid ADPF |
| `60 Opti` | 6 | On | On | On | Hybrid ADPF |
| `Max` | 8 | On | On | On | Hybrid ADPF; worker count is experimental |

The shared features are product defaults because they are compatibility-preserving code paths, not
three independent quality modes. Profiles change only the pipeline worker count. `Max` can increase
contention, power use or heat on a workload that does not benefit from eight workers, so it is not
described as universally faster.

The versioned settings migration reapplies the shared feature contract once when its schema changes.
A legacy saved `Balanced` selection maps to `Standard`; existing numeric values for `60 Opti` and
`Max` keep their meaning. This migration does not change resolution, renderer accuracy or clocks.

Choose a global default in OpenSw settings. A game's settings page can select `Inherit` or store a
Title-ID-specific override. The override is applied in memory before native startup and restored at
session end; the per-game INI file is not rewritten.

The modes do not change Android CPU/GPU frequencies or OpenSw's emulated CPU/GPU clock settings.
Policy changes are session-scoped. The performance snapshot records the active `system`, `hints` or
`hybrid` policy so a capture can be interpreted without guessing how workers were scheduled.

ADPF receives measured render work time before the speed limiter sleeps, rather than the whole frame
interval. Durations above four times the target are treated as pause/idle gaps and are not reported
as demand. This is scheduling feedback, not an FPS or thermal improvement claim; promotion still
requires repeated same-quality A/B runs on the device.

Change the global profile from `Settings > Performance profile`. For one game, open its properties,
then `Settings > Performance profile`; `Inherit` follows the global selection.

On a fresh installation running on a detected AYN Thor, OpenSw offers `60 Opti` once. Dismissing the
proposal keeps `Standard`. Versioned schema migrations may normalise legacy profile values and
shared feature flags as documented above, but never select a higher worker profile automatically.

The in-game Performance panel reports FPS, current and p95 frametime, emulation speed, RSS, system
RAM, battery data and Android thermal status. A thermal warning requires `SEVERE` status for ten
seconds. A speed warning requires less than 95 percent speed for thirty seconds. OpenSw suggests a
lower mode but never changes it automatically.

The panel, cockpit, HUD and A/B capture share one demand-driven sampler. Native frame statistics run
at the fast cadence only while requested. RSS, system RAM, battery and thermal sensors are cached at
the slow cadence, and a HUD that hides one of those metrics does not read its source. One battery
receiver is shared by every active consumer and unregistered when no battery metric remains visible.

`Start A/B capture` records a local JSON diagnostic. Reports contain Title ID and metrics, not keys,
saves, firmware, game paths or game content. Panel samples are useful for attribution but are not
promotion evidence: their 250 ms cadence cannot reconstruct frame-time percentiles. Promotion uses
raw frame timestamps and the criteria in the [Thor baseline](../performance/ayn-thor-baseline.md).

## Camera-turn diagnosis

The current Arceus camera sweep is diagnostic evidence, not a valid promotion baseline. It measured
39.51 median FPS, 42.18 ms p95 and 67.50 ms p99 at `1x`. GPU utilisation reached 98 percent at the
observed 680 MHz maximum. Native counters recorded 4.22 million pipeline hits and no misses,
compilations or waits; lookup rate increased from about 2,900/s while static to about 42,000/s during
camera movement. Native memory increased from about 5.0 GiB to 5.8 GiB during the sweep.

These measurements identify a GPU-bound, high per-draw workload. They do not establish a leak and
they do not support shader-cache clearing as a fix. The Profile build already exposes descriptor,
vertex-binding, pipeline-transition and texture upload/decode/unswizzle counters. The next step is to
use those counters and independently A/B the existing candidate optimisations.

The associated Perfetto files are invalid: they are only 2.5-2.7 KiB and contain no OpenSw process
or thread data. They must be reported as unavailable, never as successful traces. The thermal sample
is also invalid for promotion because it mixed unnamed sensors and accepted 92.7 C despite an 80 C
limit. A valid run records the named KGSL GPU temperature and requires Android thermal status `0`.

## Promotion matrix

Compare `Standard` (4), `60 Opti` (6) and `Max` (8) independently with identical `1x` resolution,
filter, graphics settings, save, replay hash, 60 FPS and shiny cheats, warm cache, driver and fan
mode. Initial GPU temperature must be within 2 C. Record five runs for each baseline and candidate.

Promote a change only when it improves median FPS by at least 3 percent or improves p95 and p99
together, with no metric, RSS or temperature regression above 2 percent. Screenshots at identical
camera timestamps must match in geometry, textures and lighting and show no corruption.

## Compiler matrix

Run `tools/performance/build-opensw-matrix.sh` to produce comparable `openSwProfile` APKs for generic
ARMv8-A, ARMv9, ThinLTO and ARMv9 + ThinLTO. The script records a SHA-256 beside every APK. All four
variants build successfully, but only the generic ARMv8-A APK is a public default. A successful
build is not evidence of a speed-up; ARMv9 and ThinLTO remain A/B candidates until the baseline
thresholds are met on the Thor.

## Deterministic lab

Build the Profile app, signed lab agent and instrumentation APK:

```sh
cd src/android
./gradlew \
  :app:assembleOpenSwProfile \
  :lab-agent:assembleDebug \
  :lab-agent:assembleDebugAndroidTest
```

`tools/performance/opensw-lab session-status` reads the real native generation, lifecycle state,
Title ID and surface status. Lifecycle commands wait for the requested state instead of treating a
submitted command as success.

Replay JSON uses nanosecond timestamps and Android button/axis IDs. `start-replay` computes and
inserts the canonical SHA-256 before handing the replay to the signed Profile bridge:

```json
{
  "schema": "opensw-input-replay-v1",
  "title_id": "01001F5010DFA000",
  "game_version": "1.1.1",
  "controller_id": "00112233445566778899aabbccddeeff",
  "controller_port": 0,
  "duration_ns": 1000000000,
  "events": [
    {"timestamp_ns": 0, "kind": "BUTTON", "control": 96, "value": 1.0},
    {"timestamp_ns": 100000000, "kind": "BUTTON", "control": 96, "value": 0.0}
  ]
}
```

The controller ID and port must match the controller mapping active for the game. A promotion capture
requires the replay to be `RUNNING`, the expected Title ID and Profile PID to match, and a render
surface to be attached. `opensw-performance-v2 capture` queries this identity through the lab
instrumentation unless `--runtime-identity` is explicitly supplied.

Native reports can exceed Android's Binder transaction limit during a 60-second capture. The
Profile bridge therefore exports the report to its app-specific external directory and returns a
small path, size and SHA-256 descriptor. `opensw-lab finish-capture` verifies the descriptor, pulls
the report, verifies its bytes and removes that exact temporary device export.

When a device has already failed ftrace validation, pass `--perfetto off` to the host capture. The
manifest records Perfetto as unavailable while SurfaceFlinger, KGSL, RSS, Android thermal status and
native Profile counters continue without repeatedly restarting the unavailable trace service.

## Upstream integration

OpenSw currently includes Eden upstream through `c0ffc900cdf19b9373549c59a7e6b22c33615ea4`.
The imported multithreading code is combined with OpenSw's existing Vulkan descriptor, pipeline and
vertex diagnostics. Upstream behavior is not presented as an OpenSw speed-up until it passes the
same five-run baseline/candidate gates and screenshot checks as any local optimization.
