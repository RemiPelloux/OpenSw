<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# AYN Thor performance baseline

This document is the acceptance record for OpenSw performance changes. Measurements must come from
the `openSwProfile` build. OpenSw never changes Android, CPU/GPU frequencies, or the official Eden
package and data.

## Device record

| Field | Value |
|---|---|
| OpenSw source | `thor-lab`; the APK version is generated as `opensw-<git-sha>` |
| Measurement APK package | `com.remipelloux.opensw.profile` |
| Normal Release package | `com.remipelloux.opensw` |
| Exact AYN Thor model | `AYN Thor`, firmware `Thor_V1.0.0.377_20260206_165408_user` |
| SoC / Android | Qualcomm `QCS8550` (Snapdragon 8 Gen 2), Android 13 / API 33 |
| RAM / displays | 11,535,400 KiB; 1080x1920 main at 120 Hz; 1080x1240 secondary |
| GPU driver | Adreno 740 with PurpleVK/Turnip `25.99.99` (`26.0.0-T23-1.4.335` overlay) |
| Fan mode / power mode | Smart fan; no Android, clock or power-mode changes made by OpenSw |
| Thermal baseline | pending; record named KGSL GPU temperature and Android thermal status `0` |

## Profile matrix

| Profile | Workers | Async presentation | Optimised vertex buffers | Async GPU/shaders | Scheduling |
|---|---:|---|---|---|---|
| `Standard` | 4 | On | On | On | Hybrid ADPF |
| `60 Opti` | 6 | On | On | On | Hybrid ADPF |
| `Max` | 8 | On | On | On | Hybrid ADPF |

Compare profiles independently. Do not change resolution, filtering, accuracy, clocks, fan policy or
any shared feature between runs.

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

The camera-turn diagnostic measured 39.51 median FPS, 42.18 ms p95 and 67.50 ms p99 at `1x`. GPU
utilisation reached 98 percent at the observed maximum 680 MHz. Native counters recorded 4.22
million pipeline hits, zero misses/compilations/waits and a lookup-rate increase from about 2,900/s
static to about 42,000/s while turning. Native memory rose from about 5.0 to 5.8 GiB.

This run is diagnostic only. Its Perfetto outputs were 2.5-2.7 KiB and contained no OpenSw process
or thread data, so no successful Perfetto trace exists for it. Its thermal capture mixed unrelated,
unnamed sensors and accepted 92.7 C despite an 80 C limit. Both sources are invalid for promotion.
The production firmware also denies Simpleperf CPU cycle counters without elevated privileges; the
capture script records that limitation without discarding independent valid sources.

## Session teardown observations

All values below were read ten seconds after returning to the OpenSw library unless stated
otherwise. They describe allocator residency after a short Arceus launch and do not replace the
controlled gameplay matrix.

Before the Bionic purge experiment, residual PSS was approximately 675-790 MiB after one cycle,
743-863 MiB after two and 790-910 MiB after three. With `mallopt(M_PURGE, 0)` after native shutdown,
six consecutive cycles measured 278, 342, 408, 476, 546 and 613 MiB PSS. No crash, abort or native
assert was present in the captured logs. The lower immediate residency is reproducible, but the
roughly 65-70 MiB per-cycle slope remains a release blocker.

Code inspection found that the four main and four idle `KThread` objects created for the emulated
cores kept their initial references across shutdown. The current fix stores those pointers and
closes them after their schedulers stop. The first coordinate-driven attempt failed to leave the
game and its 4.8 GiB running-game PSS was rejected. A later identity-aware Foretales check completed
six launch/pause/resume/stop/restart cycles without crash, ANR, late callback or mixed Title ID. RSS
after teardown was 343, 353, 360, 363, 368 and 374 MiB, then 355 MiB after 30 seconds idle. The
30-cycle gate with physical rotation and 45-60 minute Arceus run remain pending.

Heapprofd was also rejected for this scenario. Android 13 disconnected the client with
`CLIENT_ERROR_INVALID_STACK_BOUNDS` when it encountered OpenSw's custom fibers, so the resulting
heap trace represented a running partial interval and not post-shutdown retention.

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

Use five baseline and five candidate runs. Start within a 2 C named GPU-temperature band, require
Android thermal status `0`, and compare screenshots at identical replay timestamps. Reject a run if
package/PID, Title ID, session generation, surface, APK/source hash or scenario hash changes.

## Capture commands

Use `tools/performance/opensw-performance-v2 capture` for acceptance captures. It validates runtime
identity, KGSL data, Android thermal status and Perfetto before producing a manifest.

`tools/performance/capture-ayn-thor.sh <output-dir>` is a legacy raw diagnostic collector. It records
package coexistence, properties, `dumpsys thermalservice`, Perfetto and Simpleperf, but it does not
make those sources promotion-eligible. Reject its Perfetto output below 4 KiB or without OpenSw
process/thread identity, and do not derive a GPU temperature from an unnamed thermal maximum. The
script only reads the official Eden package state.
