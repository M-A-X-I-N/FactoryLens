# Baseline maintenance and adoption

Read this file when comparing, updating, or adopting the cross-repository agent baseline.

The v1 lifecycle is intentionally manual. There is no updater, synchronization bot, baseline-version file, manifest, schema version, or template ancestry relationship.

## Canonical generic source

`M-A-X-I-N/baseline` is the canonical Git tree for the current consumer-visible generic baseline.

A repository generated from the GitHub template is an independent repository with independent history. Existing repositories may adopt the same baseline later. Neither case creates an upstream Git merge relationship.

Generic improvements may be discovered in any consumer, but before they are propagated they should be expressed and reviewed in `M-A-X-I-N/baseline`. Consumers then adopt the resulting generic change deliberately.

## Ownership boundary

Treat these paths as **baseline-managed generic policy**:

- `.agents/README.md`;
- everything under `.agents/baseline/`.

After a complete manual update, adopted baseline-managed files should normally match the canonical baseline content.

Treat these as **repository-owned** after bootstrap/adoption:

- root `AGENTS.md`;
- everything under `.agents/local/`;
- everything under `.agents/memory/`;
- task/reminder/initiative contents and other state under `meta/`;
- ordinary repository source, tests, documentation, CI, configuration, and architecture.

The exact lowercase top-level path `meta/` is reserved by the baseline integration contract for project-control/collaboration state. Do not rename or recase it merely to match a repository's source naming convention.

Repository-specific behavior belongs in local instructions. If repository-specific policy has leaked into a baseline-managed file, move that policy into the appropriate local file rather than preserving an opaque generic-file fork.

## No v1 version state

Do not add a baseline version, source-commit field, manifest, schema marker, lock file, or other synchronization metadata merely to answer “which baseline does this repository have?”

For v1, use:

- the canonical baseline repository's Git history;
- the consumer repository's Git history;
- current file contents/blob identity;
- ordinary diffs;
- `git log -- <path>` / equivalent history inspection;
- `git blame` / equivalent line provenance when intent is unclear;
- semantic review of baseline versus local ownership.

This is deliberately sufficient until real maintenance experience proves otherwise.

## Updating an existing consumer

Use a recoverable branch/lineage and ordinary repository history.

### 1. Inspect

Before changing anything:

1. inspect the canonical `M-A-X-I-N/baseline` tree and relevant history;
2. inspect the consumer's current instructions, local policy, task state, and repository history;
3. verify there is no unrelated concurrent work that would be overwritten;
4. identify the current baseline-managed path set from the canonical baseline.

### 2. Compare

Compare each baseline-managed file by content, not by assumed shared ancestry.

Classify differences as:

- **identical** — no action;
- **generic baseline change** — canonical baseline has a generic change the consumer should adopt;
- **consumer-local leakage** — repository-specific policy was edited into a baseline-managed file and should move to local policy;
- **semantic conflict** — the generic change and an intentional local override interact and require explicit reconciliation;
- **missing/obsolete generic file** — the current baseline added, removed, or reorganized generic policy.

Use both repositories' Git history/blame when the text alone does not explain why a difference exists.

### 3. Propose

Before mutation, make the intended transformation understandable:

- baseline-managed files to add/update/remove;
- local rules that must be preserved or relocated;
- local routing/link changes required by structural baseline changes;
- any conflict that genuinely needs human design judgment.

Do not treat differences under `.agents/local/`, `.agents/memory/`, or live `meta/` state as baseline drift merely because the template's seed differs.

### 4. Reconcile

Apply the generic baseline change while preserving repository-owned state:

1. move repository-specific policy out of baseline-managed files when necessary;
2. update baseline-managed files from the canonical baseline;
3. update repository-owned routing only when the generic structure requires it;
4. preserve existing tasks, claims, reminders, initiatives, memory, architecture, CI, and other local state;
5. keep explicit local overrides explicit rather than editing the generic rule in-place.

Seed-once local files may receive useful new ideas from the template, but those changes are semantic proposals, never automatic replacements.

### 5. Validate

At minimum verify:

- adopted baseline-managed files match the intended canonical baseline content;
- local overrides remain discoverable and explicit;
- generic files contain no repository-specific policy/path leakage;
- root → generic router → local router → task/memory navigation still resolves;
- the reserved `meta/` path remains stable;
- task/claim state was not accidentally replaced by template seed state;
- no step depends on unimplemented updater/version machinery.

Run any repository-specific checks required by local policy.

### 6. Commit

Commit the update through ordinary repository history with a coherent, reviewable diff.

Rollback is ordinary Git revert or another additive corrective commit. Do not create special baseline rollback state in v1.

## Adopting into a repository without template ancestry

Do **not** pretend the repository was generated from the template and do not manufacture ancestry/version metadata.

Instead:

1. inspect the repository's existing agent instructions, task/work tracking, knowledge, and Git history;
2. classify existing material as generic normative policy, repository-local normative policy, memory/history, executable-work state, or unrelated project content;
3. introduce the baseline/local/memory separation without overwriting useful existing material;
4. copy the current baseline-managed files from `M-A-X-I-N/baseline`;
5. create/adapt root `AGENTS.md` and `.agents/local/` for the repository's actual identity and routing;
6. reserve exact lowercase `meta/` and deliberately map existing task/reminder/initiative state into the baseline project-control model when applicable;
7. preserve existing repository-specific policy as explicit local policy;
8. validate the same ownership/routing invariants as a normal update;
9. commit the adoption as ordinary new repository history.

Adoption is a semantic migration, not a Git merge from the template.

## When a consumer discovers a generic improvement

A consumer may reveal a rule that belongs everywhere.

Before copying it fleet-wide:

1. separate the generic rule from the consumer-specific circumstance that exposed it;
2. update and validate the generic rule in `M-A-X-I-N/baseline`;
3. adopt that canonical change back into the originating consumer and any other desired consumers through the manual update procedure.

This keeps the baseline repository authoritative without making it an invisible runtime dependency.

## When to revisit automation

Manual v1 is a deliberate experiment, not a claim that automation can never help.

Reopen the broader archived synchronization/versioning research when repeated real use shows one or more of these problems:

- determining a consumer's effective old baseline repeatedly becomes ambiguous or expensive;
- the number of consumers makes manual comparison materially burdensome;
- updates require ordered structural migrations or schema compatibility;
- generic shared runtime/workflow pins create lifecycle state that Git content comparison alone handles poorly;
- local edits repeatedly leak into baseline-managed files and reconciliation becomes error-prone;
- repository-setting synchronization becomes a real requirement;
- manual update mistakes or review effort become a recurring cost rather than a hypothetical concern.

Until then, prefer the transparent manual workflow over manifests, bots, Copier/Cruft machinery, or fleet orchestration.
