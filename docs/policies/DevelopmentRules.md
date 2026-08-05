# Development rules

These rules apply to Eden Thor Lab. They supplement the existing coding and licensing guidelines.

## Responsibility

- Every change must have a named human owner who understands and accepts the result.
- Tools, including AI-assisted tools, may support development but do not replace review.
- Do not claim that a test, device check, benchmark, or review happened unless it actually happened.

## Quality

- Keep changes focused and consistent with the surrounding architecture.
- Prefer existing project abstractions over duplicate implementations.
- Treat warnings, crashes, corrupted saves, and regressions as release blockers unless documented otherwise.
- Add tests in proportion to the behavior and risk being changed.

## Verification

- Build every affected target before merging.
- Test Android changes on the AYN Thor when they affect emulation, input, rendering, storage, or overlays.
- Record the app version, package ID, game version, Build ID, driver, and relevant settings for game-specific tests.
- Preserve the official Eden installation and its data during fork testing.

## Traceability

- State what changed, why it changed, and how it was verified.
- Identify generated or substantially tool-assisted changes during review.
- Keep commits small enough to review and revert independently.
- Never fabricate citations, source provenance, or compatibility claims.

## Licensing and security

- Preserve GPL licensing, copyright notices, and third-party attributions.
- Review new dependencies for license compatibility and maintenance risk.
- Never commit credentials, private keys, production signing material, firmware, game content, or user saves.
- Use a distinct Android package ID and app name so test builds cannot overwrite official Eden data.
