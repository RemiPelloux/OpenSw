<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Contributing to OpenSw

OpenSw accepts focused bug fixes, Android improvements, Vulkan correctness work, documentation and
reproducible performance tooling. Open an issue before a large feature or architectural rewrite so
the scope and validation plan can be agreed first.

By contributing, you agree that your work is distributed under the repository's existing GPL and
file-level license terms. OpenSw is based on Eden and retains the Eden/yuzu source history,
copyrights and attribution.

## Before opening a change

1. Search [existing issues](https://github.com/RemiPelloux/OpenSw/issues).
2. Read the [development rules](docs/policies/DevelopmentRules.md) and relevant subsystem guide.
3. Create a focused branch from the current default branch.
4. Keep unrelated formatting, branding, generated files and refactors out of the change.
5. Never commit games, keys, firmware, saves, shader caches, APK signing keys, device dumps or
   personal paths.

AI-assisted development is allowed. The contributor remains responsible for the design, licensing,
tests and reviewability of every submitted line. Disclose substantial generated content in the pull
request.

## Build and test

The normal Android package is `com.remipelloux.opensw`. From `src/android`, build it with:

```sh
./gradlew :app:assembleOpenSwRelease
```

Run the smallest relevant tests while iterating, then expand validation to match the blast radius.
Common final checks include:

```sh
cmake --build build-host-tests --target tests
build-host-tests/bin/tests
cd src/android
./gradlew :app:testOpenSwReleaseUnitTest :app:assembleOpenSwRelease
git diff --check
```

Exact prerequisites and output paths are documented in
[docs/opensw/Android.md](docs/opensw/Android.md). Do not claim a device test unless it was actually
run on the named device and build.

## Performance changes

Establish a baseline before changing performance-sensitive code. Submit raw measurement inputs,
device/build identity and repeated A/B results. A faster build must preserve rendering, saves,
settings, cheats and generic Android ARM64 compatibility. Do not use unsafe math flags, affinity,
clock changes or lower rendering quality as an optimization.

The acceptance contract is documented in
[docs/performance/ayn-thor-baseline.md](docs/performance/ayn-thor-baseline.md). An AYN Thor-specific
path must remain optional and reversible.

The three product profiles share renderer and scheduling features; they select four, six or eight
Vulkan pipeline workers. Treat a profile comparison as a worker-count experiment, keep the selected
profile and effective worker count in every manifest, and do not silently change other graphics or
Android settings between baseline and candidate runs.

## Pull requests

A pull request should explain:

- the user-visible or technical problem;
- the root cause and why the change fixes it;
- tests and builds actually run;
- device or rendering evidence when relevant;
- known limitations and follow-up work;
- licensing or attribution changes.

Keep commits grouped by purpose and suitable for review or revert. Resolve review comments with code
or evidence, not by weakening diagnostics or deleting failing tests.

## Reporting problems

Use the repository issue templates for reproducible bugs and feature proposals. Use
[GitHub Discussions](https://github.com/RemiPelloux/OpenSw/discussions) for usage questions. Report
unpatched vulnerabilities privately according to [SECURITY.md](SECURITY.md).

Community participation is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
