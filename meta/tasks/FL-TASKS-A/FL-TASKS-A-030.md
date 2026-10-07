# FL-TASKS-A-030 — Migrate human-owned HUMANS.md work

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Preserve legacy `FL-H001` without misrepresenting a deliberately human-authored artifact as executable agent work.

## Requirements

- Preserve the intent that a human may add `HUMANS.md` and write whatever they believe future machines deserve to know.
- Preserve its deliberately non-blocking nature.
- Store it as non-agent executable future intent, normally in `meta/reminders.md`.
- Preserve the legacy ID `FL-H001` in the reminder for traceability.
- Do not create or author `HUMANS.md`.

## Acceptance criteria

- `FL-H001` remains discoverable and clearly human-owned.
- It is absent from agent Dispatch and Active claims.
- No agent-authored substitute for `HUMANS.md` is created.

## Validation

- Compare the reminder wording against legacy §1.3.
