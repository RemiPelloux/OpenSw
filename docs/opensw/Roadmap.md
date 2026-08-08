<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# OpenSw engineering roadmap

This roadmap is ordered by dependency. A build, a single run or a panel sample cannot promote a
performance change. Completed work stays enabled only while rendering and generic Android
compatibility remain correct.

## Current product contract

- Release package: `com.remipelloux.opensw`; update it in place without clearing user data.
- Profiles: `Standard`, `60 Opti` and `Max`, with four, six and eight Vulkan workers.
- Shared behavior: asynchronous presentation, optimised vertex buffers, asynchronous GPU/shaders
  and hybrid ADPF scheduling.
- Resolution and quality remain `1x`; OpenSw does not change Android CPU/GPU clocks.
- Profile APK and lab agent are measurement tools, not replacements for the Release application.

## P0: measurement integrity

1. Create a fresh five-run Arceus 1.1.1 camera baseline at `1x`, using the same save, deterministic
   replay hash, cheats, filter, driver, fan mode and warm cache.
2. Start runs within a 2 C GPU-temperature band. Record `/sys/class/kgsl/kgsl-3d0/temp` by name and
   require Android thermal status `0`; reject mixed or unnamed thermal maxima.
3. Require matching package, PID, Title ID, native session generation, surface, APK/source hash and
   scenario hash throughout every capture.
4. Reject Perfetto files below 4 KiB or without OpenSw process/thread data. Mark tracing unavailable
   after ftrace validation fails and continue with native counters, SurfaceFlinger, KGSL and RSS.
5. Retain the earlier 39.51 FPS camera sweep only as diagnostic evidence because its Perfetto and
   thermal captures failed these gates.

## P1: worker-profile comparison

1. Compare `Standard` (4), `60 Opti` (6) and `Max` (8) independently. Keep all shared rendering and
   scheduling settings identical.
2. Run five warm-cache sweeps per profile, then repeat the winning comparison from the same initial
   temperature band. Include cold-cache compilation tests separately; do not mix them with steady
   camera results.
3. Record median FPS, p95/p99, speed, RSS, GPU busy-time/frequency/utilisation and named GPU
   temperature. A higher worker count is not automatically better.

## P2: GPU and per-draw attribution

1. Use the delivered descriptor-mode, payload reuse, bytes-written, chunk-switch and exhaustion
   counters to A/B the descriptor offset/payload caches. Add retained spill chunks only if the
   exhaustion counter proves that the current scheduler-finish fallback occurs in this workload.
2. Use the delivered vertex bind/slot/synchronization/upload counters to A/B sparse optimized binding
   on Android. Keep synchronization unchanged and retain the compatibility setting.
3. Use the delivered self/linear/hash/slow-path transition counters and probe depth to A/B the hybrid
   graphics-pipeline transition cache against its linear predecessor.
4. Use the delivered texture upload/decode/unswizzle bytes and time to decide whether predictive
   streaming, preload or a new cache is justified. Camera movement alone is not proof of streaming.
5. Build the existing candidates from their separate commits and test each independently with five
   baseline versus five candidate runs. Remove any candidate without repeatable benefit.

## P3: stability and memory

1. Complete 30 launch/pause/resume/physical-rotation/stop/restart cycles. Six Foretales patch cycles
   passed; physical rotation and the full gate remain open.
2. Run Arceus for 45-60 minutes with camera movement, pause/resume and secondary-display changes.
   Reject any assert, abort, ANR, rendering fault, failed shutdown acknowledgement or unexplained
   post-session memory growth.
3. Attribute the observed in-game native-memory rise from about 5.0 to 5.8 GiB by allocation owner.
   Do not label high guest mappings or peak gameplay RSS as a leak without retention evidence.
4. Diagnose the remaining host failures: `MemoryTracker: Out of bound ranges 3` and
   `DeviceMemoryManager: UpdatePagesCachedBatch basic`.

## P4: compatibility and release

1. Run generic ARM64 smoke tests outside the Thor. Keep any Thor-specific experiment opt-in and
   reversible.
2. Validate rendering screenshots at identical timestamps, controller/touch navigation, landscape,
   rotation and secondary-display fallback.
3. Re-run host tests, Android/Kotlin tests, ARM64 native build, metadata checks and
   `git diff --check`. Review the repository for logs, traces, screenshots, ROMs, keys, firmware,
   saves and other generated data before committing.
4. Build and sign the normal Release APK from committed source, verify package/certificate/hash and
   update `com.remipelloux.opensw` in place. Never modify the official Eden package or its data.

## Promotion rule

Require at least 3 percent median-FPS improvement or simultaneous p95 and p99 improvement, with no
metric, RSS or temperature regression above 2 percent. Geometry, textures, lighting and corruption
screenshots must match at identical camera timestamps. Combine only independently proven changes,
then repeat five validation sweeps on the combined build.

## Explicit non-goals

- Reducing resolution to `0.75x`, adding FSR or changing quality to create an FPS claim.
- Changing Android CPU/GPU clocks, fan policy, power mode or unsafe math flags.
- Clearing shader caches as an optimisation when counters show zero pipeline misses/compilations.
- Adding speculative `2D`/`3D` modes, prediction or preload before counters identify their workload.
- Weakening validation, hiding failures or claiming Profile APK results as a Release update.
