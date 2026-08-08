<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Changelog

Significant OpenSw-specific changes are recorded here. The repository retains earlier Eden/yuzu
history and tags; this changelog covers the maintained OpenSw Android product.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). OpenSw does not claim
semantic-version compatibility until a public release policy is adopted.

## Unreleased

### Changed

- Merge Eden upstream through `c0ffc900cd`, including the multithreading/ADPF refactor, crash fixes,
  translation updates and weekly translation scheduling.
- Use asynchronous presentation, optimised vertex buffers, asynchronous GPU/shaders and hybrid ADPF
  scheduling in every profile. `Standard`, `60 Opti` and `Max` select four, six and eight Vulkan
  pipeline workers respectively.
- Report render work duration to ADPF without speed-limiter sleep and discard long pause/idle gaps.
- Rename the emulated CPU/GPU clock levels while preserving their existing configuration keys and
  numeric values.

### Fixed

- Emulate direct Maxwell `LineLoop` draws as closed Vulkan line strips instead of triangle lists.
- Keep compute push-descriptor state consistent when descriptor-buffer allocation falls back.
- Reduce the user-facing profiles to `Standard`, `60 Opti` and `Max`; legacy `Balanced` selections
  migrate to `Standard` while preserving the numeric values of `60 Opti` and `Max`.
- Invalidate guest compute descriptor state after internal compute passes.
- Close leaked Android Storage Access Framework validation cursors.
- Prevent stale native game metadata ownership during concurrent library refreshes.

### Performance

- Reuse unchanged graphics and compute descriptor payloads and compute descriptor-buffer allocations.
- Skip vertex upload-range construction when the memory tracker has no CPU-dirty data.
- Deduplicate overlapping Android document-tree scans and cache directory listings within a scan.
- Remove the per-redraw allocation from the frame-time graph.

### Documentation

- Add renderer compatibility notes, normal Release installation instructions and public project
  governance files.
- Align every active OpenSw guide with the three-profile 4/6/8-worker contract, safe Release updates,
  verified lifecycle evidence and the measurement-only role of Profile builds.
- Replace invalid Perfetto and mixed-sensor thermal claims with explicit rejection rules, and publish
  the next descriptor, vertex-binding, pipeline-transition and texture-attribution roadmap.
