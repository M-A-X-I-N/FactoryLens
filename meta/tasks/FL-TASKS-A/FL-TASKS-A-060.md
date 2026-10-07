# FL-TASKS-A-060 — Migrate Phase C Rider MVP tasks

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Convert legacy Rider MVP work into baseline non-terminal task specifications.

## Scope

- `FL-C200`
- `FL-C210`
- `FL-C220`
- `FL-C230`
- `FL-C240`
- `FL-C250`
- `FL-C260`

## Requirements

- Preserve every existing task ID.
- Migrate each legacy `READY` task to `QUEUED`.
- Create one durable self-contained task specification per task.
- Preserve C260 as the explicit working-product-gate task.
- Derive only real dependencies from the product architecture and gate requirements; do not treat old row order as dependency proof.
- Leave every Phase C task out of Dispatch.

## Acceptance criteria

- All seven Phase C IDs exist as `QUEUED` baseline tasks with specs.
- The Rider MVP/gate semantics remain understandable without legacy `TASKS.md`.
- No Phase C work becomes authorized by migration.

## Validation

- Compare against legacy §1.6 and the roadmap initiative produced by A-020.
