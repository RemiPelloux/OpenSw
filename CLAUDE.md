# OpenSw development rules

AI assistants and LLM-based development tools are explicitly authorized for this
OpenSw fork. They may inspect, search, audit, document, test, build, and modify the
codebase when acting within the user's request and the rules below.

## Required workflow

- Read and follow `AGENTS.md` before changing code.
- Inspect the relevant implementation, callers, tests, logs, and device state before
  deciding on a root cause. Label unverified explanations as hypotheses.
- Keep every change scoped, reviewable, and reversible. Preserve unrelated worktree
  changes and do not perform opportunistic refactors.
- Fix root causes. Never hide a defect by removing assertions, swallowing errors,
  weakening validation, clamping invalid state, or disabling useful diagnostics.
- Use existing project patterns and dependencies unless a new abstraction or
  dependency has a concrete, documented benefit.
- Add or update tests in proportion to the risk and blast radius of the change.
- Run the smallest relevant checks during iteration, then broaden validation before
  declaring the work complete.
- Report exactly which checks and device tests were run. Never invent test results,
  benchmark data, compatibility claims, or performance gains.

## Performance work

- Establish a reproducible baseline before changing performance-sensitive code.
- Use isolated A/B comparisons with identical settings, workload, thermal state, and
  build configuration. Record median and tail frametimes, not FPS alone.
- Promote an optimization only when repeated measurements demonstrate a useful gain
  without a material regression in stability, correctness, or another workload.
- Do not enable unsafe memory behavior, `fast-math`, unverified fence changes, forced
  CPU affinity, frequency control, or architecture-specific flags without explicit,
  isolated evidence and a reversible fallback.
- Keep the public Android ARM64 build generic. AYN Thor-specific behavior must be
  opt-in, reversible, and isolated behind an explicit profile.

## Android and device safety

- OpenSw owns only the package `com.remipelloux.opensw`.
- Never modify, delete, launch, or overwrite data belonging to the official Eden
  package `dev.eden.eden_emulator.nightly` unless the user explicitly requests a
  specific permitted action.
- Never change Android system settings, clocks, thermal controls, firmware, or device
  partitions as part of an emulator optimization.
- Do not commit or publish ROMs, updates, firmware, encryption keys, saves, user data,
  device dumps, logs containing private paths, credentials, or secrets.

## Product quality

- Do not ship placeholders, fake controls, dead menu entries, misleading metrics, or
  incomplete user-facing flows.
- Preserve touch and controller navigation, visible focus, rotation, portrait and
  landscape layouts, accessibility, and the AYN Thor dual-screen fallback.
- User-facing OpenSw behavior must use the OpenSw identity while retaining upstream
  licenses, copyrights, source history, and the "Based on Eden" attribution.
- Update active documentation whenever setup, behavior, compatibility, or validation
  requirements change. Clearly mark inherited Eden documentation that is unverified.

## Git discipline

- Review `git status`, the complete intended diff, and staged files before committing.
- Group commits by purpose. Do not mix engine fixes, performance experiments, UI,
  branding, generated assets, and documentation without a concrete reason.
- Never stage unrelated user changes. Never rewrite shared history or use destructive
  Git commands unless the user explicitly authorizes the exact operation.
- A commit or push requires an explicit user request. Before publishing, verify that
  generated artifacts, dumps, protected content, and secrets are excluded.
