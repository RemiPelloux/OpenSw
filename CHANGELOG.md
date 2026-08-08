<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
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
- Make Android thread scheduling policy explicit and session-scoped per OpenSw mode: `Standard` leaves
  placement to Android, `Thor Balanced` uses ADPF hints only, and the stable/max modes add the
  topology fallback when hints are unavailable.
- Report render work duration to ADPF without speed-limiter sleep and discard long pause/idle gaps.
- Rename the emulated CPU/GPU clock levels while preserving their existing configuration keys and
  numeric values.

### Fixed

- Emulate direct Maxwell `LineLoop` draws as closed Vulkan line strips instead of triangle lists.
- Keep compute push-descriptor state consistent when descriptor-buffer allocation falls back.
- Keep `Thor Balanced` free of explicit Linux thread-priority changes as required by its hints-only
  policy.
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
