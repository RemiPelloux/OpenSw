# OpenSw engineering roadmap

This roadmap is ordered by risk and dependency. An item moves to complete only with recorded device
evidence; compilation alone is not a performance or stability result.

## Current top bottlenecks

1. Android CPU affinity currently pins the four emulated CPU threads to host CPUs `0-3` without
   reading the SoC topology. The function name claims performance-core placement, but no capacity or
   frequency data is used. This can trap emulation on inefficient cores and is the highest-priority
   sustained-performance investigation.
2. Every `Common::Fiber` eagerly allocates and zeroes a 2 MiB stack plus a 2 MiB rewind stack. The
   rewind path is currently unused, while dozens of service and kernel fibers make this a major
   startup, memory-residency and shutdown-lifetime cost.
3. Vulkan pipeline compilation uses at least four workers because the current clamp makes the
   documented automatic/zero branch unreachable. Six or seven effective workers can contend with
   four critical CPU emulation threads, while some pipeline paths still wait synchronously and cause
   frametime spikes.

## P0: release stability

1. Add a profile/debug-only Android instrumentation driver for launch, pause, resume and production
   shutdown. It must wait for native acknowledgements and must not ship an externally callable
   control surface in public release builds.
2. Validate the main/idle kernel-thread ownership fix with six cycles, then 30 cycles, recording
   10-second and 30-second memory snapshots.
3. Report remaining dangling kernel objects by concrete type and reference count. Fix ownership at
   the creator; do not bulk-close unknown objects or suppress the shutdown warning.
4. Instrument fiber creation, destruction, peak stack use and ownership by thread type. Remove the
   unused rewind allocation or make it lazy, then right-size stacks with guard pages. Require fiber
   assertions and the 30-cycle memory test to remain green before promotion.
5. Diagnose and fix the reproducible macOS host-test crashes in HostMemory and
   `DeviceMemoryManager: UpdatePagesCachedBatch basic`; bound the CoreTiming test duration.
6. Run Arceus for 60 minutes and complete repeated pause, resume, rotation and secondary-display
   attach/detach checks without assert, ANR or abnormal memory growth.
7. Keep Eden installed, stopped and unchanged throughout every acceptance run.

## P1: measured Thor performance

1. Remove the unconditional Android `CPU 0-3` affinity from the baseline. Detect host CPU capacity
   and maximum frequency when evaluating an optional topology-aware policy. Compare no affinity,
   Android-managed scheduling and topology-aware placement using FPS, p95/p99, speed and thermal
   state; keep affinity disabled unless it wins reproducibly without regressions.
2. Fix Vulkan worker selection so `0` has an explicit automatic meaning and values below four are
   testable. Compare 2, 4 and 6 workers with cold/warm caches, measure compilation time, p95/p99 and
   temperature, and make the displayed Thor Max worker count match the effective native count.
3. Complete the fixed Arceus and Monster Train 2 matrix for cold/warm caches and 0/5/20 cheats.
4. Compare generic ARMv8-A, ARMv9, ThinLTO and ARMv9 plus ThinLTO with five warm runs per case.
5. Profile CPU scheduling, Vulkan compilation/presentation, audio and I/O with Perfetto. Use
   instrumentation counters for fiber-backed allocations that Android heapprofd cannot unwind.
6. Promote only changes with at least 3 percent repeatable gain or a clear p95/p99 improvement and
   no regression above 2 percent elsewhere.

## P2: cockpit and UX completion

1. Finish controller focus restoration after cheat search and IME dismissal.
2. Validate cockpit recreation across hinge changes, rotation and secondary-display loss.
3. Add an in-app session health view backed by the existing shared sampler, with no permanent game
   overlay and no work for hidden metrics.
4. Audit French and English strings, accessibility labels and compact/grid/list/carousel layouts on
   both Thor displays.

## P3: distribution

1. Finish license and attribution review, generic-device smoke tests and clean-install migration.
2. Publish the generic ARMv8-A APK first. Keep Thor-specific compiler variants as labelled test
   artifacts until their A/B gates pass.
3. Tag a release only after the 30-cycle and 60-minute stability gates are complete and the
   diagnostic bundle is confirmed to exclude protected user data.
