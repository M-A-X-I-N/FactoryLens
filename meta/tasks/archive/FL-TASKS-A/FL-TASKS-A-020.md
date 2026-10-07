# FL-TASKS-A-020 — Migrate roadmap and working-product gate context

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Separate the legacy roadmap-level product context from executable scheduling state.

## Requirements

- Create or update a FactoryLens product-roadmap initiative under `meta/initiatives/`.
- Preserve the working-product gate, its RSS2/Wiremod/current-environment validation expectations, and the distinction between research proof and supported-product behavior.
- Preserve the phase-level intent necessary to understand why Phase B precedes the Rider MVP and why Phase D is post-gate hardening.
- Keep executable task state in `meta/tasks.md` / task specs rather than duplicating mutable task status in the initiative.
- Link related executable task IDs without turning the initiative into Dispatch.

## Acceptance criteria

- A fresh agent can understand the product-gate/roadmap context without reading legacy `TASKS.md`.
- Mutable task status is not duplicated in the initiative.
- The initiative remains explicitly non-executable.

## Validation

- Compare the initiative against legacy §§1.2 and phase-introduction text.
- Verify links to any referenced current task IDs/paths.
