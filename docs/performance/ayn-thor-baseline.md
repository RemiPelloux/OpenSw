# AYN Thor performance baseline

This document is the acceptance record for OpenSw performance changes. Measurements must come from
the `openSwProfile` build. OpenSw never changes Android, CPU/GPU frequencies, or the official Eden
package and data.

## Device record

| Field | Value |
|---|---|
| OpenSw source | `thor-lab`; the APK version is generated as `opensw-<git-sha>` |
| APK package | `com.remipelloux.opensw` |
| Exact AYN Thor model | `AYN Thor`, firmware `Thor_V1.0.0.377_20260206_165408_user` |
| SoC / Android | Qualcomm `QCS8550` (Snapdragon 8 Gen 2), Android 13 / API 33 |
| RAM / displays | 11,535,400 KiB; 1080x1920 main at 120 Hz; 1080x1240 secondary |
| GPU driver | Adreno 740 with PurpleVK/Turnip `25.99.99` (`26.0.0-T23-1.4.335` overlay) |
| Fan mode / power mode | Smart fan; no Android, clock or power-mode changes made by OpenSw |
| Sustained temperature | approximately 55-63 C, smart fan 33-43 percent (16,500-21,500 reported RPM) |

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

## Verified device observation

Pokemon Legends: Arceus 1.1.1 is installed and selected. OpenSw reports Title ID
`01001F5010DFA000`, main NSO Build ID `AEE8F150DDA1B5A8`, and 43 compatible cheat sections. With
Mastercode and `60 FPS` enabled, the native engine reported `Applied 2 cheat sections (57 opcodes)`.

A stable SurfaceFlinger sample produced 59.96 FPS, 16.677 ms median frame interval, 18.991 ms p95
and 20.297 ms p99. This confirms correct update, cheat selection and presentation, but does not
replace the controlled 0/5/20-cheat matrix above. Memory in the earlier instrumentation pass was
4,820,600 KiB PSS, 5,718,232 KiB RSS and 116 KiB swap PSS.
The dominant 3.38 GiB `Other mmap` category is consistent with the emulator's 4 GiB guest-memory
layout and must not be treated as a Java leak without a native mapping breakdown.

A 60-second Perfetto trace was captured successfully. The production firmware denies access to
Simpleperf CPU cycle counters without elevated privileges; the capture script now records that
limitation and continues instead of discarding the remaining report.

## Experiments

1. Cheat VM command tracing is compiled out unless `ENABLE_CHEAT_VM_TRACE` is explicitly defined.
   This removes register and opcode formatting from the 12 Hz execution loop. Device gain is not yet
   claimed.
2. Per-pipeline Vulkan creation messages are compiled out of profile builds. The original run wrote
   113 `Info` messages over 28.8 seconds during shader warm-up; the rebuilt profile emitted none.
   No FPS gain is claimed from this single observation.
3. Generic ARM64 versus ARMv9 remains an A/B experiment. It must not become the Thor default before
   the full scenario matrix passes.
4. ThinLTO remains an A/B experiment. `fast-math` is prohibited.

The generic ARMv8-A, ARMv9, ThinLTO and ARMv9 + ThinLTO `openSwProfile` APKs all completed the local
Android build matrix on 2026-08-05 with the same local signing identity. This validates the build
presets only; no ARMv9 or ThinLTO performance gain is claimed from compilation alone.

Promote an experiment only with a reproducible improvement of at least 3 percent or a clear p95/p99
reduction, and no regression above 2 percent in another scenario. Otherwise keep it Experimental or
drop it.

## Capture commands

Run `tools/performance/capture-ayn-thor.sh <output-dir>` after connecting exactly one ADB device.
The script records package coexistence, properties, thermal state, Perfetto and a Simpleperf report.
It only reads the official Eden package state.
