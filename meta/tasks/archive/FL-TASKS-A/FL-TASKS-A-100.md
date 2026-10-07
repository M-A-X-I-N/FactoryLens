# FL-TASKS-A-100 — Update task-system references and enforcement

## Global migration constraints

- Root `TASKS.md` remains non-authoritative migration input until `FL-TASKS-A-110` retires it.
- `meta/tasks.md` remains the sole executable-work scheduling authority throughout.
- Do not place migrated product work in Dispatch merely because it existed in the legacy roadmap.
- Preserve existing FactoryLens product task IDs wherever they remain meaningful.
- Do not rewrite product implementation or broaden scope while converting task representation.
- Distinguish real structural dependencies from mere legacy ordering; do not manufacture dependencies just to reproduce table order.

## Description

After all legacy content has a baseline destination, update current routing, documentation, and mechanical consistency checks so root `TASKS.md` is no longer treated as current authority.

## Requirements

- Search tracked current-policy/navigation files for references to root `TASKS.md`.
- Update root README/repository map, `docs/README.md`, local repository policy, scripts/checks, and other current navigation that still names the legacy ledger as authoritative.
- Point executable-work navigation to `meta/tasks.md`; link initiatives/reminders where context warrants.
- Remove the transitional local-policy statement that root `TASKS.md` is pending migration.
- Update repository consistency enforcement so the final state no longer requires root `TASKS.md`.
- Do not rewrite historical research merely because it mentions old task IDs/paths unless the reference falsely presents current authority.

## Acceptance criteria

- Current authoritative navigation no longer points agents/humans to root `TASKS.md` for live status.
- Mechanical repository checks describe the post-migration layout.
- Historical references are left intact where they are genuinely historical.

## Validation

- Search tracked files for `TASKS.md` references and classify every remaining hit.
- Run targeted repository consistency validation as appropriate.
