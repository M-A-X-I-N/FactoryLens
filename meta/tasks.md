# Tasks

This is the authoritative scheduling index for sufficiently specified executable agent work.

Full non-terminal task instructions and temporary tracked task workspaces live under [`tasks/`](tasks/). Reminders and initiatives are not executable work and do not belong in Dispatch.

## Dispatch

_No QUEUED task is currently dispatched._

Dispatch is an ordered authorization/priority list, not a lifecycle state. Only `QUEUED` tasks with satisfied dependencies belong here. Claiming a task changes it to `IN_PROGRESS`, removes it from Dispatch, and records a live claim below.

## Active claims

Claims are live coordination locks, not identity or recovery credentials. The task index remains authoritative for lifecycle state.

| Task | Lineage | Canonical branch | Claimed at (UTC) | Notes |
|---|---|---|---|---|

## Active task index

| Task | State | Dependencies | Title | Summary |
|---|---|---|---|---|
| [`FL-BASELINE-A-020`](tasks/FL-BASELINE-A/FL-BASELINE-A-020.md) | `QUEUED` | — | Reconcile legacy root agent guide | Reconcile `AGENTS.md.orig` against the canonical baseline, preserving only still-useful FactoryLens-specific residue in repository-owned local policy. |
| [`FL-BASELINE-A-030`](tasks/FL-BASELINE-A/FL-BASELINE-A-030.md) | `QUEUED` | `FL-BASELINE-A-020` | Reconcile legacy agent router | Reconcile `.agents/README.md.orig` against the canonical baseline router and preserve only still-useful FactoryLens-specific residue locally. |
| [`FL-BASELINE-A-040`](tasks/FL-BASELINE-A/FL-BASELINE-A-040.md) | `QUEUED` | `FL-BASELINE-A-030` | Reconcile legacy workflow | Reconcile the legacy `.agents/WORKFLOW.md` against baseline workflow policy and preserve only still-useful FactoryLens-specific rules locally. |
| [`FL-BASELINE-A-050`](tasks/FL-BASELINE-A/FL-BASELINE-A-050.md) | `QUEUED` | `FL-BASELINE-A-040` | Finalize instruction migration | Validate the completed instruction migration, local routing, baseline byte identity, and retirement of legacy instruction inputs without migrating the legacy task roadmap. |

## Completed bootstrap

| Task | State | Title | Summary |
|---|---|---|---|
| `FL-BASELINE-A-010` | `COMPLETE` | Import baseline | Preserve pre-existing baseline-path conflicts as `.orig`, import the canonical baseline tree, and establish the baseline task control plane. |

## Migration authority boundary

Until `FL-BASELINE-A-050` completes or the human explicitly says otherwise:

- only instructions supplied by the imported baseline template are authoritative;
- legacy/unmigrated instruction files are migration inputs, not instructions;
- files under `.agents/local/**` may be written as migration output but must not be followed as instructions yet;
- root `TASKS.md` is outside this instruction-migration block and must not be migrated or edited here.

## Task contract

Generic lifecycle, state meanings, Dispatch/claim behavior, recovery, dependency-vs-blocker distinction, deferred validation, and terminal advancement rules come from [`../.agents/baseline/WORKFLOW.md`](../.agents/baseline/WORKFLOW.md).

This index owns mutable scheduling metadata. Task specification files own full execution instructions. Do not duplicate mutable state in both places.
