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

_No non-terminal tasks are currently defined._

## Completed tasks

| Task | State | Title | Summary |
|---|---|---|---|
| `FL-BASELINE-A-010` | `COMPLETE` | Import baseline | Preserve pre-existing baseline-path conflicts as `.orig`, import the canonical baseline tree, and establish the baseline task control plane. |
| `FL-BASELINE-A-020` | `COMPLETE` | Reconcile legacy root agent guide | Preserve the FactoryLens-specific scope, source ownership, architecture boundaries, research-promotion rule, and external-project safety locally; supersede generic read/Git/provenance rules with baseline policy. |
| `FL-BASELINE-A-030` | `COMPLETE` | Reconcile legacy agent router | Supersede generic agent-directory/read-order guidance with the baseline router and retain the B1–B8 feasibility study's historical-evidence role in repository-local policy. |
| `FL-BASELINE-A-040` | `COMPLETE` | Reconcile legacy workflow | Supersede generic checkpoint/recovery/provenance policy with baseline rules; preserve FactoryLens analyzer boundaries and human-run validation-script rules in local policy. |
| `FL-BASELINE-A-050` | `COMPLETE` | Finalize instruction migration | Validate canonical baseline identity, local routing, legacy-instruction retirement, repository consistency enforcement, and preservation of the untouched legacy task roadmap. |

## Task contract

Generic lifecycle, state meanings, Dispatch/claim behavior, recovery, dependency-vs-blocker distinction, deferred validation, and terminal advancement rules come from [`../.agents/baseline/WORKFLOW.md`](../.agents/baseline/WORKFLOW.md).

This index owns mutable scheduling metadata. Task specification files own full execution instructions. Do not duplicate mutable state in both places.
