# OpenSw Performance Lab

## Modes

- `Standard` removes OpenSw overrides and restores the captured values.
- `Thor Balanced` enables asynchronous presentation and optimised vertex buffers.
- `Thor 60 stable` additionally enables asynchronous GPU/shaders and six Vulkan workers.
- `Thor Max` uses eight Vulkan workers and remains experimental.

Choose a global default in OpenSw settings. A game's settings page can select `Inherit` or store a
Title-ID-specific override. The override is applied in memory before native startup and restored at
session end; the per-game INI file is not rewritten.

On a fresh installation running on a detected AYN Thor, OpenSw offers `Thor 60 stable` once. The
choice remains explicit: dismissing the proposal keeps `Standard`, and an existing installation is
never migrated silently.

The in-game Performance panel reports FPS, current and p95 frametime, emulation speed, RSS, system
RAM, battery data and Android thermal status. A thermal warning requires `SEVERE` status for ten
seconds. A speed warning requires less than 95 percent speed for thirty seconds. OpenSw suggests a
lower mode but never changes it automatically.

The panel, cockpit, HUD and A/B capture share one demand-driven sampler. Native frame statistics run
at the fast cadence only while requested. RSS, system RAM, battery and thermal sensors are cached at
the slow cadence, and a HUD that hides one of those metrics does not read its source. One battery
receiver is shared by every active consumer and unregistered when no battery metric remains visible.

`Start A/B capture` records a local JSON report. Reports contain Title ID and metrics, not keys,
saves, firmware, game paths or game content. Compiler experiments are promoted only after the
criteria in the [Thor baseline](../performance/ayn-thor-baseline.md) pass.

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
