# AYN Thor performance baseline

This document is the acceptance record for OpenSw performance changes. Measurements must come from
the `openSwProfile` build. OpenSw never changes Android, CPU/GPU frequencies, or the official Eden
package and data.

## Device record

| Field | Value |
|---|---|
| OpenSw commit | Pending device run |
| APK version/package | `opensw-<sha>` / `com.remipelloux.opensw` |
| Exact AYN Thor model | Pending ADB connection |
| SoC / Android | Pending ADB connection |
| GPU driver | Pending ADB connection |
| Fan mode / power mode | Pending device run |
| Ambient / start temperature | Pending device run |

## Fixed scenarios

Use Pokemon Legends: Arceus 1.1.1 (`01001F5010DFA000`, Build ID
`AEE8F150DDA1B5A8`) and Monster Train 2 (`010051701FB46000`). Record five runs after warm-up for
cold and warm shader caches, with 0, 5, and 20 active cheats. Keep driver, resolution, accuracy,
fan mode, save position, camera path and test duration identical.

| Game | Cache | Cheats | Median FPS | p95 ms | p99 ms | Speed | RSS MiB | Peak C | Notes |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Arceus 1.1.1 | cold | 0 | pending | pending | pending | pending | pending | pending | |
| Arceus 1.1.1 | warm | 0 | pending | pending | pending | pending | pending | pending | |
| Arceus 1.1.1 | warm | 5 | pending | pending | pending | pending | pending | pending | |
| Arceus 1.1.1 | warm | 20 | pending | pending | pending | pending | pending | pending | |
| Monster Train 2 | cold | 0 | pending | pending | pending | pending | pending | pending | |
| Monster Train 2 | warm | 0 | pending | pending | pending | pending | pending | pending | |

## Experiments

1. Cheat VM command tracing is compiled out unless `ENABLE_CHEAT_VM_TRACE` is explicitly defined.
   This removes register and opcode formatting from the 12 Hz execution loop. Device gain is not yet
   claimed.
2. Generic ARM64 versus ARMv9 remains an A/B experiment. It must not become the Thor default before
   the full scenario matrix passes.
3. ThinLTO remains an A/B experiment. `fast-math` is prohibited.

Promote an experiment only with a reproducible improvement of at least 3 percent or a clear p95/p99
reduction, and no regression above 2 percent in another scenario. Otherwise keep it Experimental or
drop it.

## Capture commands

Run `tools/performance/capture-ayn-thor.sh <output-dir>` after connecting exactly one ADB device.
The script records package coexistence, properties, thermal state, Perfetto and a Simpleperf report.
It only reads the official Eden package state.
