# FL-TASKS-A-040 — Archive completed legacy product tasks

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

Preserve historical completed task identity and completion evidence for legacy Phase A and B100–B140 without keeping terminal rows on the active scheduling surface.

## Scope

Migrate these terminal task IDs:

- `FL-A010`
- `FL-A020`
- `FL-A030`
- `FL-A040`
- `FL-B100`
- `FL-B110`
- `FL-B120`
- `FL-B130`
- `FL-B140`

## Requirements

- Represent all scoped tasks as `COMPLETE` in the task-history/archive convention established by A-010.
- Preserve each ID, title, and meaningful legacy completion criterion/evidence link.
- Use current source/docs/Git history as authoritative if the legacy wording and implemented reality differ.
- Do not create active claims or Dispatch entries for terminal history.
- Keep the active task index focused on non-terminal work.

## Acceptance criteria

- All nine scoped IDs are preserved exactly once in durable task history.
- Their completed status is unambiguous.
- Relevant durable evidence/docs remain linked where practical.

## Validation

- Cross-check the archived set against legacy §§1.4 and 1.5.
