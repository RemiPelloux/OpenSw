<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# OpenSw session stability

This document tracks repeated launch and shutdown behaviour on Android. It distinguishes confirmed
root causes from measurements that only describe symptoms.

## Implemented lifecycle fixes

OpenSw now performs the following teardown work in dependency order:

1. Stop the cheat engine and wait for in-flight callbacks before HID and service teardown.
2. Close Oboe audio streams before destroying the audio core.
3. Stop CPU and GPU work before releasing emulated backing memory.
4. Release tracked fastmem mappings, process page tables and process memory trackers.
5. Release the creator reference for every guest service thread after it starts.
6. Retain and close the four main and four idle kernel-thread references after their schedulers stop.
7. On Android API 28 or newer, request `M_PURGE` after native shutdown so Bionic can return unused
   allocator pages to the operating system.

The purge changes residency, not ownership. It does not replace object-lifetime fixes and is never
used to hide an assert or an invalid memory range.

## Evidence and limits

The service-thread reference fix was instrumented directly: all 52 guest service threads finalized
and their execution references were released. Six short Arceus cycles with the Bionic purge then
completed without a crash or native assert and reduced immediate residual PSS substantially. The
remaining 65-70 MiB per-cycle slope led to the main/idle thread ownership fix.

The first ADB coordinate sequence did not activate `Quit emulation`; the process was still running
the game when memory was sampled. That 4.8 GiB measurement remains invalid and excluded.

The later identity-aware procedure completed six Foretales launch/capture/pause/resume/stop/restart
cycles. No crash, ANR, late callback or mixed Title ID was observed. RSS after teardown was 343, 353,
360, 363, 368 and 374 MiB, then 355 MiB after 30 seconds idle. This is useful patch evidence, but it
does not replace the 30-cycle gate with physical rotation or the 45-60 minute Arceus session.

Android heapprofd is not an accepted source for this test on the current Thor firmware. OpenSw uses
custom fiber stacks and heapprofd disconnects with `CLIENT_ERROR_INVALID_STACK_BOUNDS`, producing a
partial running-session profile.

## Validation snapshot: 2026-08-08

- `assembleOpenSwProfile`: passed on the main/idle ownership fix and Bionic purge.
- Native `tests` target: built successfully.
- Cheats: 24 assertions across 5 cases passed.
- Fibers: 15 assertions across 3 cases passed.
- Page-table reset: 12 assertions passed.
- KMemoryBlockManager fastmem finalization: 3 assertions passed.
- Foretales lifecycle: six device cycles passed with stable runtime identity and teardown checks.

The complete host suite is not green. `MemoryTracker: Out of bound ranges 3` and
`DeviceMemoryManager: UpdatePagesCachedBatch basic` remain known failures. Neither is attributed to
the Android lifecycle fix without focused diagnosis, but both remain recorded release risks.

## Deterministic acceptance procedure

The Profile-only lab instrumentation drives the same fragment pause, resume and shutdown paths as
the UI and waits for a versioned native session acknowledgement. Screen coordinates are not used.
Every sample must first prove all of the following:

- `MainActivity` is the top resumed OpenSw activity;
- the native session reports stopped;
- no emulation surface is attached;
- the OpenSw PID is unchanged;
- Eden has no running PID.

Record PSS, RSS, native heap, activity count and view count at 10 and 30 seconds. The six-cycle patch
check has passed; run 30 cycles for release acceptance. Reject the run on any assert, abort, ANR,
failed shutdown acknowledgement or unexplained linear growth. A 45-60 minute Arceus gameplay
session remains required after the cycle test passes.

With the Profile, lab agent and lab instrumentation APKs installed, run:

```sh
tools/performance/opensw-lab cycle \
  --game-uri 'content://USER_GRANTED_GAME_URI' \
  --title-id 01001F5010DFA000 \
  --cycles 6 \
  --output captures/lifecycle-6.json
```

Use `--cycles 30` only after the six-cycle patch check passes. The report intentionally records
`physical_rotation_covered=false`; complete and record that device action manually rather than
changing Android rotation settings from automation.
