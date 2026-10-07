# Repository meta

The exact lowercase top-level path `meta/` is reserved by this baseline for repository meta/control-plane and collaboration state.

Do **not** rename or recase this directory merely to match a repository's source-code naming convention. A repository may use snake_case, kebab-case, PascalCase, or another style elsewhere while this integration point remains exactly:

```text
meta/
```

Keeping the path stable makes manual baseline comparison and adoption predictable across repositories.

The baseline seed places these repository-owned surfaces here:

- `tasks.md` — executable-work scheduling, Dispatch, and Active claims;
- `tasks/` — task specifications and tracked temporary workspaces;
- `reminders.md` — lightweight non-executable future intent;
- `initiatives/` — structured non-executable unfinished work/debt.

Repositories may add repository-control material beneath `meta/` when useful. Do not repurpose the reserved directory for unrelated application/source content.
