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

### Fixed

- Emulate direct Maxwell `LineLoop` draws as closed Vulkan line strips instead of triangle lists.
- Keep compute push-descriptor state consistent when descriptor-buffer allocation falls back.
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
