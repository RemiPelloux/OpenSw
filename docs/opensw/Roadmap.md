# OpenSw engineering roadmap

This roadmap is ordered by risk and dependency. An item moves to complete only with recorded device
evidence; compilation alone is not a performance or stability result.

## Current top bottlenecks

1. Vulkan worker selection and small-draw waits still need a controlled 2/4/6-worker comparison
   with cold and warm caches. No default changes until Perfetto attribution and five-run A/B evidence
   show a p95/p99 or median-FPS improvement without regression.
2. Runtime memory, audio cancellation, guarded fibers and Vulkan teardown compile and have focused
   tests. Six Foretales cycles are complete; 30 cycles with physical rotation and the 45-60 minute
   Arceus validation remain pending.
3. The Eden bindless Vulkan, NPad guard and audio DSP bounds changes are ported and build in
   Release/Profile. Bindless remains unpromoted until its dedicated visual and performance A/B.

## Delivered foundation

- Native/Kotlin session generations reject callbacks from stopped emulation sessions.
- The Profile JNI snapshot preserves its first 12 fields and adds five presentation counters.
- Panel captures are versioned native diagnostics with monotonic timestamps, configuration identity
  and explicit invalidation; only raw SurfaceFlinger/Perfetto captures can support A/B promotion.
- The secondary-screen cockpit provides Direct and Profile-only Details views, fixed Capture/Share
  actions and session controls without a hero or duplicated cover.
- Favorites, compact selection, search and focus restoration are implemented with DiffUtil and
  bounded Coil caching.

## P0: release stability

1. Add a profile/debug-only Android instrumentation driver for launch, pause, resume and production
   shutdown. It must wait for native acknowledgements and must not ship an externally callable
   control surface in public release builds.
2. The six-cycle gate for the main/idle kernel-thread ownership fix is complete. Run the 30-cycle
   gate next, recording 10-second and 30-second memory snapshots and physical rotation.
3. Keep the kernel registry non-owning and diagnostic-only. Fix any remaining ownership issue at the
   creator; do not bulk-close unknown objects or suppress the shutdown warning.
4. Validate guarded fiber stacks and cancelled audio waits under repeated lifecycle stress. Require
   the 30-cycle memory test to remain green before promotion.
5. Diagnose the remaining macOS host-test failures in HostMemory and
   `DeviceMemoryManager: UpdatePagesCachedBatch basic`; bound the CoreTiming test duration.
6. Run Arceus for 60 minutes and complete repeated pause, resume, rotation and secondary-display
   attach/detach checks without assert, ANR or abnormal memory growth.
7. Keep Eden installed, stopped and unchanged throughout every acceptance run.

## P1: measured Thor performance

1. Keep Android-managed scheduling as the baseline now that unconditional `CPU 0-3` affinity has
   been removed. Evaluate topology-aware placement only as an optional measured experiment.
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

1. Validate the implemented controller focus restoration after cheat search and IME dismissal at
   normal and maximum font scales.
2. Validate cockpit recreation across hinge changes, rotation and secondary-display loss.
3. Validate the delivered Direct/Details session health view across recreation; retain no permanent
   game overlay and no work for hidden metrics.
4. Audit French and English strings, accessibility labels and compact/grid/list/carousel layouts on
   both Thor displays.
5. Favorites and recently played/resume ordering are delivered. Validate the compact quick actions
   and keep the 64 dp selection bar without reintroducing a hero or duplicated cover.

## P3: distribution

1. Finish license and attribution review, generic-device smoke tests and clean-install migration.
2. Publish the generic ARMv8-A APK first. Keep Thor-specific compiler variants as labelled test
   artifacts until their A/B gates pass.
3. Tag a release only after the 30-cycle and 60-minute stability gates are complete and the
   diagnostic bundle is confirmed to exclude protected user data.
