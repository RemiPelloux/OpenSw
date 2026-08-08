<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# OpenSw handbook

OpenSw is a standalone Android Switch emulator distributed as `com.remipelloux.opensw`. Debug builds
use `com.remipelloux.opensw.debug`.

The maintained guides are:

- [Android build and installation](./Android.md)
- [Performance modes and A/B captures](./Performance.md)
- [Vulkan rendering compatibility](./Rendering.md)
- [Session stability and memory lifecycle](./SessionStability.md)
- [Secondary-screen cockpit and live cheats](./CockpitAndCheats.md)
- [Troubleshooting and safe diagnostics](./Troubleshooting.md)
- [Engineering roadmap](./Roadmap.md)

The handbook documents current product behavior. Historical measurements and rejected runs live in
the [Thor audit](../performance/ayn-thor-audit.md); executable acceptance criteria live in the
[Thor baseline](../performance/ayn-thor-baseline.md); remaining campaign work is tracked in the
[campaign status](../performance/roadmap.md). A build, screenshot or diagnostic run is not a
performance claim.

OpenSw never changes Android power modes, CPU/GPU frequencies or data owned by another application.
Optional legacy import is read-only and requires an explicit Android document-provider grant.
