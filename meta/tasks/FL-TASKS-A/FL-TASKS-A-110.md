# FL-TASKS-A-110 — Retire legacy TASKS.md and validate cutover

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Perform the final semantic-coverage audit, retire root `TASKS.md`, and validate that the baseline task/initiative/reminder model fully replaces it.

## Requirements

- Verify the A-010 migration map shows complete coverage of §§1.1–1.10 and every legacy task row.
- Verify all non-terminal product tasks have correct baseline states/specs.
- Verify completed historical tasks are preserved in the established archive.
- Verify human-only, unscheduled, and S1–S10 material has appropriate non-executable destinations.
- Delete root `TASKS.md` only after those checks pass.
- Make repository consistency enforcement reject reintroduction of the retired root ledger if that is the cleanest post-migration invariant.
- Run the real repository checks relevant to the changed control-plane/documentation surface.
- Mark this migration block terminal and archive its task specs/workspace according to the convention established by A-010.

## Acceptance criteria

- Root `TASKS.md` no longer exists.
- No meaningful legacy task/roadmap intent was silently lost.
- `meta/tasks.md` is the sole live executable-work ledger.
- Product roadmap context is in initiatives/reminders/docs as appropriate.
- Repository checks pass.
- The FL-TASKS-A block is terminal and recoverably archived.

## Validation

- Full migration-map coverage check.
- Search for stale authoritative `TASKS.md` references.
- Run repository consistency checks on supported CI platforms or equivalent current validation.
