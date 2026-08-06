# OpenSw repository instructions

- Work only inside this repository unless the user explicitly requests otherwise.
- Preserve unrelated worktree changes and keep changes scoped to the current task.
- Do not modify or delete data belonging to the official Eden Android package
  `dev.eden.eden_emulator.nightly`.
- Use the repository's existing build, formatting, and test conventions.
- Keep OpenSw compatible with generic Android ARM64 unless a change is explicitly
  isolated to an optional AYN Thor profile.
- Do not claim device tests or performance gains unless they were actually measured.

## Pelloux guidelines

- Inspect the relevant code, logs, tests, and device state before proposing a cause.
  Clearly distinguish verified facts, hypotheses, and measurements.
- Prefer targeted searches, focused builds, and the smallest relevant test suite so
  iteration stays fast. Expand validation when the change has a wider blast radius.
- Fix root causes. Do not hide failures by removing assertions, swallowing errors,
  clamping invalid values, or weakening useful diagnostics.
- Keep changes small, reversible, and grouped by purpose. Do not mix branding, UI,
  engine fixes, performance tuning, and documentation in one indistinct change.
- Establish a baseline before performance work. Promote an optimization only when
  repeated A/B measurements show a real gain without a material regression.
- Never enable unsafe memory behavior, risky math flags, CPU affinity, frequency
  changes, or device-specific shortcuts without isolated testing and evidence.
- Preserve compatibility with non-Thor Android ARM64 devices. Thor-specific behavior
  must be opt-in, reversible, and confined to an explicit profile.
- Do not leave placeholders, fake controls, dead menu entries, misleading metrics,
  or unfinished user-facing flows in a completed feature.
- For UI work, preserve controller and touch navigation, visible focus, rotation,
  landscape layouts, and the Thor dual-screen fallback behavior.
- Run and report the relevant tests after implementation. State precisely what was
  not tested, especially when the AYN Thor is disconnected from ADB.
- Update the active OpenSw documentation when behavior or setup changes, while
  retaining upstream licenses, copyrights, and the "Based on Eden" attribution.
- Before committing or pushing, review the diff and repository status, exclude
  generated dumps, logs, secrets, ROMs, keys, firmware, saves, and unrelated files.
