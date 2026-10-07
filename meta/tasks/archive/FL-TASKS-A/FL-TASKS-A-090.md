# FL-TASKS-A-090 — Migrate repository-standardization draft

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Move the legacy S1–S10 "keep for later" repository-standardization draft into the baseline initiative model.

## Requirements

- Create a structured initiative under `meta/initiatives/`.
- Preserve S1–S10 identifiers as historical/candidate labels if useful, but do not convert them into executable task IDs.
- Preserve the explicit rule that the draft must not be executed/refined merely because it exists.
- Preserve the future design choice among one-time FactoryLens cleanup, recurring audit, generic cross-repository framework, or a combination.
- Preserve the stated candidate completion criteria/gaps at initiative level without duplicating mutable task state.

## Acceptance criteria

- All S1–S10 candidate areas remain recoverable from the initiative.
- The initiative is explicitly non-executable and absent from Dispatch.
- No standardization implementation is accidentally authorized.

## Validation

- Compare the initiative against all content in legacy §1.9.
