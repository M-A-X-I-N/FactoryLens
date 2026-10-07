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
| `FL-TASKS-A-080` | `lyra_261007-025300` | `agent/lyra_261007-025300/main` | `2026-10-07 04:37` | Authorized through FL-TASKS-A-110; migrating unscheduled future intent. |

## Active task index

| Task | State | Dependencies | Title | Summary |
|---|---|---|---|---|
| [`FL-B150`](tasks/FL-B150.md) | `FROZEN` | — | Implement the root-provider interface and external-override provider | Pre-migration active task; intentionally frozen by human decision after migration. |
| [`FL-B160`](tasks/FL-B160.md) | `QUEUED` | `FL-B150` | Implement Unreal dynamic-delegate root discovery | Add semantically verified AddDynamic-style callback roots with registration provenance. |
| [`FL-B170`](tasks/FL-B170.md) | `QUEUED` | `FL-B150`, `FL-B160` | Add a headless developer CLI/integration harness | Exercise supported root discovery and expansion without Rider. |
| [`FL-B180`](tasks/FL-B180.md) | `QUEUED` | `FL-B150`, `FL-B160` | Add automated analyzer tests and controlled semantic fixtures | Build deterministic supported analyzer coverage separate from expensive real-workspace checks. |
| [`FL-B190`](tasks/FL-B190.md) | `QUEUED` | `FL-B150`, `FL-B160`, `FL-B170`, `FL-B180` | Validate the supported analyzer on RSS2 and Wiremod | Prove the supported analyzer across both required real Satisfactory mod specimens. |
| [`FL-C200`](tasks/FL-C200.md) | `QUEUED` | `FL-B190` | Create the minimal Rider plugin skeleton | Establish the supported Rider-side entry surface after analyzer validation. |
| [`FL-C210`](tasks/FL-C210.md) | `QUEUED` | `FL-C200` | Connect Rider project lifecycle to the analyzer lifecycle | Manage supported analyzer sessions predictably with Rider project lifetime. |
| [`FL-C220`](tasks/FL-C220.md) | `QUEUED` | `FL-C210` | Add the framework-root explorer | Present useful framework roots with provenance without default override noise. |
| [`FL-C230`](tasks/FL-C230.md) | `QUEUED` | `FL-C210` | Add lazy call-tree expansion | Present bounded cached graph traversal as lazy Rider navigation. |
| [`FL-C240`](tasks/FL-C240.md) | `QUEUED` | `FL-C230` | Add source navigation | Navigate roots/nodes/edges to trustworthy source locations. |
| [`FL-C250`](tasks/FL-C250.md) | `QUEUED` | `FL-C220`, `FL-C230`, `FL-C240` | Add provenance, filtering, and basic control state | Expose evidence quality, filtering, and restart/cancel controls. |
| [`FL-C260`](tasks/FL-C260.md) | `QUEUED` | `FL-B190`, `FL-C220`, `FL-C230`, `FL-C240`, `FL-C250` | Pass the working-product gate | Prove the complete supported Rider workflow on RSS2 and Wiremod. |
| [`FL-D300`](tasks/FL-D300.md) | `FROZEN` | `FL-C260` | Add durable/incremental cache invalidation | Post-gate performance/correctness hardening. |
| [`FL-D310`](tasks/FL-D310.md) | `FROZEN` | `FL-C260` | Add SML native-hook root discovery | Post-gate SML native-hook enrichment. |
| [`FL-D320`](tasks/FL-D320.md) | `FROZEN` | `FL-C260` | Add UHT/reflection metadata ingestion | Post-gate reflection/generated metadata enrichment. |
| [`FL-D330`](tasks/FL-D330.md) | `FROZEN` | `FL-C260` | Investigate Blueprint/generated-class enrichment | Post-gate scoped Blueprint/generated-class research. |
| [`FL-D340`](tasks/FL-D340.md) | `FROZEN` | `FL-C260` | Reassess generic C++ / non-Satisfactory extraction | Post-gate extraction based on demonstrated reuse. |
| [`FL-D350`](tasks/FL-D350.md) | `FROZEN` | `FL-C260` | Product packaging, compatibility policy, and installation UX | Post-gate distribution/compatibility work. |
| [`FL-D360`](tasks/FL-D360.md) | `FROZEN` | `FL-C260` | Release documentation and public-facing polish | Post-gate release polish. |
| [`FL-TASKS-A-080`](tasks/FL-TASKS-A/FL-TASKS-A-080.md) | `IN_PROGRESS` | `FL-TASKS-A-020` | Migrate explicitly unscheduled intent | Preserve licensing timing and product non-goal boundaries as reminders/initiative context rather than executable tasks. |
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
