<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# OpenSw troubleshooting

## Performance profile

The global profile is stored under `Settings > Performance profile`. A game's properties can store
`Inherit`, `Standard`, `60 Opti` or `Max`; the Title-ID override is applied on the next launch and
restored when that session ends. Changing a profile does not rewrite the game's INI file.

All three profiles use asynchronous presentation, optimised vertex buffers, asynchronous
GPU/shaders and hybrid ADPF scheduling. They differ only in Vulkan pipeline workers: four, six or
eight. More workers can increase heat and CPU contention, so try `Standard` when `Max` performs worse
or causes more sustained fan activity.

## High GPU use and fan speed

High GPU utilisation during a heavy 3D scene can be normal: the renderer may be doing useful work at
the device's GPU limit. On the measured Arceus camera sweep, utilisation reached 98 percent at the
observed maximum GPU frequency while frame time worsened. That establishes a GPU-bound workload, not
by itself a memory leak or a thermal fault.

Compare the same scene, settings and starting temperature. Record named KGSL GPU temperature,
Android thermal status, frame-time percentiles and RSS over time. A steadily increasing allocation
that does not fall after leaving the scene is leak evidence; high RSS during gameplay alone is not.
Never use clock changes, reduced resolution or cache clearing to hide the cause.

Perfetto output smaller than 4 KiB, or without the OpenSw PID/process and thread identity, is empty
evidence. Mark Perfetto unavailable and use native counters, SurfaceFlinger, KGSL and RSS instead.

## Safe diagnostic bundle

Open Settings and select `Share OpenSw diagnostics`. The ZIP contains build/device metadata and up
to five recent Performance Lab reports. It deliberately excludes keys, firmware, saves, game paths,
game files and Eden data.

## Session shutdown

If a native memory invariant fails, record the complete `HostMemory` message. OpenSw logs the page
table identity, address-space width, offset, length and separate-heap state; do not hide the failure
by disabling assertions or clipping the range.

## Secondary display

The cockpit requires Android to expose the lower screen as a non-default active display. If it does
not appear, Performance and Cheats remain available from the main in-game menu.
