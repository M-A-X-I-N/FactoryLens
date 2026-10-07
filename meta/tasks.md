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
| `FL-TASKS-A-040` | `lyra_261007-025300` | `agent/lyra_261007-025300/main` | `2026-10-07 04:12` | Authorized through FL-TASKS-A-110; archiving completed legacy product tasks. |

## Active task index

| Task | State | Dependencies | Title | Summary |
|---|---|---|---|---|
| [`FL-TASKS-A-040`](tasks/FL-TASKS-A/FL-TASKS-A-040.md) | `IN_PROGRESS` | `FL-TASKS-A-010` | Archive completed legacy product tasks | Preserve completed Phase A and B100–B140 task identities, outcomes, and acceptance evidence in task history without polluting the active ledger. |
| [`FL-TASKS-A-050`](tasks/FL-TASKS-A/FL-TASKS-A-050.md) | `QUEUED` | `FL-TASKS-A-010`, `FL-TASKS-A-020` | Migrate unfinished Phase B tasks | Create baseline task specs for B150–B190, with B150 explicitly `FROZEN` and later Phase B tasks `QUEUED` but undispatched. |
| [`FL-TASKS-A-060`](tasks/FL-TASKS-A/FL-TASKS-A-060.md) | `QUEUED` | `FL-TASKS-A-010`, `FL-TASKS-A-020` | Migrate Phase C Rider MVP tasks | Create baseline task specs for C200–C260 while preserving the working-product-gate semantics and avoiding invented authorization. |
| [`FL-TASKS-A-070`](tasks/FL-TASKS-A/FL-TASKS-A-070.md) | `QUEUED` | `FL-TASKS-A-010`, `FL-TASKS-A-020` | Migrate deferred Phase D tasks | Create baseline task specs for D300–D360 as `FROZEN` post-product-gate work. |
| [`FL-TASKS-A-080`](tasks/FL-TASKS-A/FL-TASKS-A-080.md) | `QUEUED` | `FL-TASKS-A-020` | Migrate explicitly unscheduled intent | Preserve licensing timing and product non-goal boundaries as reminders/initiative context rather than executable tasks. |
| [`FL-TASKS-A-090`](tasks/FL-TASKS-A/FL-TASKS-A-090.md) | `QUEUED` | `FL-TASKS-A-010` | Migrate repository-standardization draft | Move S1–S10 into a structured non-executable initiative without promoting any item to Dispatch. |
| [`FL-TASKS-A-100`](tasks/FL-TASKS-A/FL-TASKS-A-100.md) | `QUEUED` | `FL-TASKS-A-030`, `FL-TASKS-A-040`, `FL-TASKS-A-050`, `FL-TASKS-A-060`, `FL-TASKS-A-070`, `FL-TASKS-A-080`, `FL-TASKS-A-090` | Update task-system references and enforcement | Redirect current docs/policy/checks from root `TASKS.md` to the baseline control plane and remove transitional legacy-ledger assumptions. |
| [`FL-TASKS-A-110`](tasks/FL-TASKS-A/FL-TASKS-A-110.md) | `QUEUED` | `FL-TASKS-A-100` | Retire legacy TASKS.md and validate cutover | Delete the legacy root ledger only after semantic coverage is proven, validate the new task model, and archive the migration block per the established convention. |

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
