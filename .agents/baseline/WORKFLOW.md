# Baseline workflow, tasks, and recovery

Read this file for substantial planned repository work, task lifecycle changes, coordination, or interrupted-session recovery.

Repository-local instructions may explicitly override or tighten a rule here. Silence in local policy leaves this baseline rule in force.

## Working autonomy

Once the human authorizes a bounded task or explicitly bounded task range, carry that authorized work through its normal checkpoints without repeatedly asking for permission between ordinary implementation steps.

Interrupt only when progress genuinely requires human judgment or input, for example:

- a material architecture/design choice not already decided;
- conflicting authority that cannot be resolved from the repository;
- a safety/destructive-action decision requiring explicit authorization;
- missing information only the human can supply;
- a validation failure that changes the intended design or cannot be responsibly resolved within the authorized task.

Do not interrupt for routine implementation choices, recoverable corrections, normal validation, or ordinary bookkeeping.

Do not begin work beyond the explicitly authorized task/range merely because the next task is known.

## Executable-work authority

The repository-local instructions identify the authoritative task ledger and storage.

A task ledger's **Dispatch** is the ordered executable authorization list. A known task, old branch, reminder, initiative, or apparent unfinished work is not executable merely because it exists.

Claiming dispatched work normally:

1. changes the task from `QUEUED` to `IN_PROGRESS`;
2. removes it from Dispatch;
3. records the authorized lineage/working branch in **Active claims**;
4. commits/persists that coordination checkpoint before substantive work when practical.

An Active claim is a coordination lock, not authority to adopt somebody else's lineage.

## Task states

The baseline lifecycle supports:

- `QUEUED`
- `IN_PROGRESS`
- `BLOCKED`
- `FROZEN`
- `AWAITING_DEFERRED_CI`
- `COMPLETE`
- `CANCELLED`
- `SUPERSEDED`

A dependency is structurally required prior work. A temporary technical/external/human impediment is a blocker rather than a dependency.

`FROZEN` is an intentional priority/policy hold rather than a technical blocker.

`AWAITING_DEFERRED_CI` means implementation and advancement-blocking validation are complete while explicitly deferred analysis remains required. A task is not `COMPLETE` until its required deferred validation succeeds.

## Task granularity

Unless the human specifies task boundaries, choose task size for coherent implementation, validation, dependency clarity, recoverability, and cross-context survival.

Do not optimize for small or large tasks as an end in itself.

An isolated repository change that is obviously one natural checkpoint and is not part of a larger active block need not become a task. Do not use this exception to bypass existing task state or meaningful multi-checkpoint work.

## Checkpoint progression

For repository-changing task work:

1. inspect the current repository/branch state;
2. inspect Dispatch, task state, and the linked detailed task specification;
3. claim authorized work before substantive execution when the task system requires it;
4. preserve intermediate reasoning/research needed across contexts in the task workspace;
5. keep meaningful checkpoints understandable and recoverable;
6. run the smallest validation that genuinely proves the changed surface;
7. persist meaningful completed work promptly;
8. when a task completes, update task state and release its Active claim;
9. populate Dispatch only with the next work actually authorized/eligible;
10. preserve expensive reusable discoveries in the appropriate durable location.

Do not leave substantial completed work only in an ephemeral tool session.

## Active claims

Claims record which authorized lineage currently owns active execution.

Typical lifecycle:

- `QUEUED → IN_PROGRESS`: create claim and remove from Dispatch;
- `IN_PROGRESS`, `BLOCKED`, and `AWAITING_DEFERRED_CI` may retain claims while the same lineage owns continuation;
- freezing active work normally releases its claim unless explicit policy says otherwise;
- terminal tasks must not retain claims;
- explicit handoff changes ownership only after the receiving lineage is authorized.

If a claim is stale, inconsistent, missing its branch, or conflicts with current conversation authority, inspect and reconcile it. Do not silently steal/delete it or treat it as recovery permission.

Git history is the historical record of old claims; released claims do not need a second permanent claim archive.

## Agent lineages and recovery authority

Normal substantive development should use a recoverable agent lineage namespace unless repository-local policy or the nature of the change makes direct default-branch work more appropriate.

Default lineage convention:

- identifier: `{name}_YYMMDD-HHmmss`;
- `name`: four lowercase ASCII letters forming a female, neutral, or fantasy-style human-readable name;
- namespace: `agent/{identifier}/*`;
- canonical branch: `agent/{identifier}/main`.

A lineage owns its namespace while active.

**Discovering a lineage is not authority to adopt it.**

A different chat/agent may adopt an existing lineage only when:

1. the human explicitly requests recovery/adoption of that lineage/branch; or
2. the current conversation already established ownership of that lineage and the human gives an unambiguous continuation/recovery instruction referring to it.

Finding an `agent/**` branch, `IN_PROGRESS` task, or Active claim alone is insufficient authority.

After authorized recovery, compare actual branch history/state with the expected checkpoint before writing. Unexpected unrelated work requires reconciliation rather than overwrite/absorption.

## Interrupted-session recovery

Recovery answers two separate questions:

1. **What actually reached the repository?**
2. **Who is authorized to continue it?**

Do not trust the last narrated chat action as proof that a commit/ref/file exists.

Inspect:

- relevant branch/ref heads and history;
- task ledger state;
- active or archived task specifications;
- task workspace evidence;
- actual changed files/checkpoints;
- validation state.

Distinguish committed work from reasoning-only/orphaned work.

Inspecting a lineage for evidence is allowed; mutating/adopting it still requires recovery authority.

Prefer recovering already-created correct Git objects over manually recreating them.

## Completion, archival, and advancement

Terminal states are `COMPLETE`, `CANCELLED`, and `SUPERSEDED`.

When every task in a block is terminal:

- move/archive the block according to the repository-local task storage contract;
- preserve task IDs and block/task workspace evidence;
- remove terminal rows from the active scheduling surface if that is the repository contract;
- release any remaining claim.

Do not archive an incomplete block.

Do not mark later ordered work complete past an unresolved earlier task whose completion is structurally required.

## Reminders and initiatives

A **reminder** is lightweight deliberately non-executable future intent.

An **initiative** is structured multi-phase unfinished context/debt that is more concrete than a reminder but still non-executable.

Neither is authorization.

Promote either into tasks deliberately when the work becomes sufficiently specified and the human authorizes executable work.

## Validation

Use real checks that match the changed surface.

Documentation/policy changes may be proven by rereading, link/path checks, tree inspection, semantic consistency review, and targeted repository tests.

Do not invent ceremonial validation merely to say something was tested.

Substantive validation failure must be resolved before claiming completion. If the failure exposes a real architecture/policy decision outside existing authority, ask the human.

## Source-of-truth discipline

Prefer the source that owns the subject.

General order:

1. tracked source/configuration for implemented behavior;
2. human-facing architecture/policy for durable project design;
3. authoritative task ledger/specs/workspaces for executable work;
4. initiatives/reminders for explicitly non-executable intent;
5. root/local/baseline agent instructions for agent operating policy;
6. agent memory for supporting knowledge/rationale/history.

Repository-local policy may refine this map.
