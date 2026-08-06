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
