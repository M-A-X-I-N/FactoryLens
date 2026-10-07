# FL-TASKS-A-050 — Migrate unfinished Phase B tasks

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Convert legacy Phase B unfinished analyzer work into baseline task specifications.

## Scope

- `FL-B150`
- `FL-B160`
- `FL-B170`
- `FL-B180`
- `FL-B190`

## Required lifecycle mapping

- `FL-B150` → `FROZEN` by explicit human decision. Preserve that it was the pre-migration active task, but create no claim and do not dispatch it.
- `FL-B160` through `FL-B190` → `QUEUED`, with no automatic Dispatch entries.

## Requirements

- Preserve each existing product task ID.
- Create one durable task specification per non-terminal task with description, requirements, constraints/non-goals, acceptance criteria, and validation.
- Preserve the meaningful legacy "Done when" contract while enriching it only from authoritative current docs/source when needed.
- Derive actual dependencies from implementation requirements; do not simply chain tasks because the old table was ordered.
- Record B150's frozen policy hold separately from technical blockers.

## Acceptance criteria

- All five Phase B tasks exist in the baseline task model with the required states.
- B150 is visibly `FROZEN`, unclaimed, and undispatched.
- B160–B190 are `QUEUED` but undispatched.
- Each has a self-contained specification.

## Validation

- Compare all migrated specs/states against legacy §1.5 and current analyzer docs/source.
