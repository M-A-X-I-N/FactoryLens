# FL-TASKS-A-070 — Migrate deferred Phase D tasks

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Convert defined post-product-gate hardening/enrichment tasks into baseline frozen work.

## Scope

- `FL-D300`
- `FL-D310`
- `FL-D320`
- `FL-D330`
- `FL-D340`
- `FL-D350`
- `FL-D360`

## Requirements

- Preserve every existing task ID.
- Migrate each legacy `DEFERRED` task to `FROZEN`.
- Preserve the policy that Phase D must not delay proving the working product.
- Treat successful `FL-C260` completion as a structural prerequisite where the legacy "after the product gate" boundary requires it.
- Create one durable task specification per task.
- Create no claims or Dispatch entries.

## Acceptance criteria

- All seven Phase D tasks exist as `FROZEN` baseline tasks with self-contained specs.
- Their post-product-gate hold is explicit.
- No Phase D task is executable merely because it has been migrated.

## Validation

- Compare states/specs against legacy §1.7 and the roadmap initiative.
