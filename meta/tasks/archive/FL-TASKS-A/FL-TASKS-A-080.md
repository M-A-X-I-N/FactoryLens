# FL-TASKS-A-080 — Migrate explicitly unscheduled intent

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Preserve legacy §1.8 without turning deliberately unscheduled boundaries into fake executable tasks.

## Requirements

- Preserve the intent to defer license selection/release-policy scheduling until after `FL-C260` unless the human explicitly pulls it forward, normally as a reminder.
- Preserve the listed items that must not become accidental prerequisites for C260 as roadmap/product-boundary context.
- Reuse existing durable product/MVP documentation when it already owns a boundary instead of duplicating it.
- Do not create executable tasks merely to represent an explicit non-goal.

## Acceptance criteria

- Licensing timing remains discoverable and non-executable.
- C260 non-prerequisite boundaries remain durable and unambiguous.
- No new task/Dispatch entry is created solely for a legacy non-goal.

## Validation

- Account for every item in legacy §1.8.
