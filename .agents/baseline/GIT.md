# Baseline Git and checkpoint policy

Read this file before manipulating branches/history or creating substantive repository checkpoints.

Repository-local instructions may explicitly override or tighten these defaults.

## History safety

Preserve reachable history and unique work by default.

Normal correction is additive:

- new corrective commits;
- revert commits;
- fast-forward ref movement;
- new branches/checkpoints.

Do not force-move refs, rebase/drop/squash established history, reset away unique work, or otherwise rewrite/discard reachable history without explicit human authorization identifying the affected history/ref and operation.

Large/destructive history cleanup may be separately authorized by local policy/tasking, but is never inferred from ordinary cleanup authority.

## Checkpoints

Keep checkpoints coherent and independently understandable/recoverable.

Choose boundaries for implementation, validation, dependency clarity, rollback, and cross-context survival—not merely to maximize or minimize commit count.

Persist meaningful completed checkpoints promptly.

Use the smallest validation that genuinely proves the changed surface before claiming the checkpoint complete.

## Branches and lineages

Follow `WORKFLOW.md` for agent lineage ownership and recovery authority.

Do not infer permission to mutate a discovered agent branch merely from its existence.

Direct default-branch changes are valid when repository-local policy or the nature of coordination/bookkeeping makes them the natural choice.

## Commit summaries

Default agent-authored commit summary format:

```text
[Kind][Scope] Imperative summary
```

`Scope` is optional when it adds no useful information.

Baseline kinds:

- `Feature`
- `Fix`
- `Research`
- `Documentation`
- `Test`
- `CI`
- `Build`
- `Refactor`
- `Chore`
- `CBA` — human-selected only; agents must never self-select it.

Repository-local policy may add or constrain kinds/scopes.

Wholly agent-authored substantive commits must also follow `PROVENANCE.md`.

## Push/recovery discipline

Before pushing or moving refs:

- verify the intended branch/ref and current head when ambiguity exists;
- prefer fast-forward movement;
- preserve concurrent/unexpected work;
- do not silently absorb unrelated commits into an authorized lineage.

If repository state differs materially from the expected checkpoint, reconcile before mutating it.
