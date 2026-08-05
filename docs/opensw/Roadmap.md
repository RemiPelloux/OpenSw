# OpenSw engineering roadmap

This roadmap is ordered by risk and dependency. An item moves to complete only with recorded device
evidence; compilation alone is not a performance or stability result.

## P0: release stability

1. Add a profile/debug-only Android instrumentation driver for launch, pause, resume and production
   shutdown. It must wait for native acknowledgements and must not ship an externally callable
   control surface in public release builds.
2. Validate the main/idle kernel-thread ownership fix with six cycles, then 30 cycles, recording
   10-second and 30-second memory snapshots.
3. Report remaining dangling kernel objects by concrete type and reference count. Fix ownership at
   the creator; do not bulk-close unknown objects or suppress the shutdown warning.
4. Diagnose and fix the reproducible macOS host-test crashes in HostMemory and
   `DeviceMemoryManager: UpdatePagesCachedBatch basic`; bound the CoreTiming test duration.
5. Run Arceus for 60 minutes and complete repeated pause, resume, rotation and secondary-display
   attach/detach checks without assert, ANR or abnormal memory growth.
6. Keep Eden installed, stopped and unchanged throughout every acceptance run.

## P1: measured Thor performance

1. Complete the fixed Arceus and Monster Train 2 matrix for cold/warm caches and 0/5/20 cheats.
2. Compare generic ARMv8-A, ARMv9, ThinLTO and ARMv9 plus ThinLTO with five warm runs per case.
3. Profile CPU scheduling, Vulkan compilation/presentation, audio and I/O with Perfetto. Use
   instrumentation counters for fiber-backed allocations that Android heapprofd cannot unwind.
4. Promote only changes with at least 3 percent repeatable gain or a clear p95/p99 improvement and
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
