# FL-TASKS-A-010 — Define legacy task migration map

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Produce a complete semantic map from every section and task row in legacy root `TASKS.md` to its baseline destination before deleting or rewriting any legacy task state.

## Required conversion rules

Use these already-decided mappings unless repository evidence exposes a direct contradiction:

- legacy `DONE` executable tasks → terminal `COMPLETE` task history/archive;
- legacy `ACTIVE` `FL-B150` → `FROZEN`, with no claim and no Dispatch entry;
- legacy `READY` agent-executable product tasks → `QUEUED`, with no automatic Dispatch entry;
- legacy `DEFERRED` defined executable product tasks → `FROZEN`;
- legacy `READY — HUMAN` `FL-H001` → human-owned non-agent future intent, not executable agent work;
- working-product gate / phase narrative → initiative-level roadmap context;
- S1–S10 standardization draft → non-executable initiative;
- licensing timing / other lightweight future intent → reminder when appropriate;
- durable product non-goals/boundaries → roadmap initiative or existing durable docs, not fake tasks;
- generic legacy task-maintenance rules already covered by baseline policy → supersede rather than duplicate locally.

## Requirements

- Account for §§1.1–1.10 and every `FL-*` row.
- Record the destination, resulting lifecycle state where applicable, preserved ID, and any true dependency decisions.
- Define the task-history archive convention needed for migrated terminal tasks and for this migration block.
- Prefer a tracked migration workspace/map under this block so later tasks can verify coverage without rereading chat history.
- Do not migrate task rows yet except for bookkeeping strictly necessary to persist the map.

## Acceptance criteria

- Every legacy section and task row has exactly one intended destination or an explicit reason it is intentionally superseded.
- B150's target state is explicitly `FROZEN`.
- No migration decision depends on remembered chat context.
- Later tasks can mechanically prove whether all legacy content was handled.

## Validation

- Compare the migration map against the complete legacy `TASKS.md` heading/task inventory.
- Verify no legacy task ID is omitted or duplicated.
