# Legacy TASKS.md migration map

This workspace is the semantic coverage contract for the one-time migration from root `TASKS.md` to the baseline `meta/` control plane.

It is migration evidence, not executable authority. Mutable scheduling state remains in `../../tasks.md`.

## Global state conversion

| Legacy concept | Baseline destination |
|---|---|
| `DONE` executable task | archived terminal task record with state `COMPLETE` |
| `ACTIVE` `FL-B150` | active task spec with state `FROZEN`; no claim; no Dispatch |
| `READY` agent-executable task | active task spec with state `QUEUED`; no automatic Dispatch |
| `DEFERRED` defined executable task | active task spec with state `FROZEN` |
| `READY — HUMAN` | non-agent reminder, preserving legacy ID |
| roadmap / working-product gate narrative | non-executable product roadmap initiative |
| S1–S10 draft | non-executable repository-standardization initiative |
| lightweight future intent such as licensing timing | reminder |
| durable non-goal / gate boundary | product roadmap initiative and/or owning docs |
| generic old maintenance/status vocabulary | superseded by baseline workflow; not migrated as local policy |

Per explicit human direction, `FL-B150` is intentionally frozen after migration.

Current/legacy CI workflow runs are deliberately non-gating for this conversion block and are not promoted into executable tasks merely because they exist.

## Section coverage

| Legacy section | Destination | Migration task |
|---|---|---|
| §1.1 Status vocabulary | superseded by baseline lifecycle; conversion rules retained in this map | A-010 |
| §1.2 Working-product gate | `meta/initiatives/product-roadmap.md` | A-020 |
| §1.3 Human-owned task | `meta/reminders.md` as `FL-H001` | A-030 |
| §1.4 Phase A | archived terminal records | A-040 |
| §1.5 Phase B | B100–B140 archived; B150–B190 active specs | A-040 / A-050 |
| §1.6 Phase C | active queued specs | A-060 |
| §1.7 Phase D | active frozen specs | A-070 |
| §1.8 Explicitly unscheduled | reminder + product-roadmap initiative boundaries | A-080 |
| §1.9 S1–S10 draft | `meta/initiatives/repository-standardization.md` | A-090 |
| §1.10 Maintenance rule | superseded by baseline workflow + existing local source-of-truth policy | A-110 validation |

## Task-row coverage

### Human-owned

| Legacy ID | Destination | Result |
|---|---|---|
| `FL-H001` | `meta/reminders.md` | human-owned, non-executable |

### Completed Phase A / Phase B history

| Legacy ID | Destination | Result |
|---|---|---|
| `FL-A010` | `meta/tasks/archive/product-history/FL-A010.md` | `COMPLETE` |
| `FL-A020` | `meta/tasks/archive/product-history/FL-A020.md` | `COMPLETE` |
| `FL-A030` | `meta/tasks/archive/product-history/FL-A030.md` | `COMPLETE` |
| `FL-A040` | `meta/tasks/archive/product-history/FL-A040.md` | `COMPLETE` |
| `FL-B100` | `meta/tasks/archive/product-history/FL-B100.md` | `COMPLETE` |
| `FL-B110` | `meta/tasks/archive/product-history/FL-B110.md` | `COMPLETE` |
| `FL-B120` | `meta/tasks/archive/product-history/FL-B120.md` | `COMPLETE` |
| `FL-B130` | `meta/tasks/archive/product-history/FL-B130.md` | `COMPLETE` |
| `FL-B140` | `meta/tasks/archive/product-history/FL-B140.md` | `COMPLETE` |

### Unfinished Phase B

| Legacy ID | Destination | Result |
|---|---|---|
| `FL-B150` | `meta/tasks/FL-B150.md` | `FROZEN` |
| `FL-B160` | `meta/tasks/FL-B160.md` | `QUEUED` |
| `FL-B170` | `meta/tasks/FL-B170.md` | `QUEUED` |
| `FL-B180` | `meta/tasks/FL-B180.md` | `QUEUED` |
| `FL-B190` | `meta/tasks/FL-B190.md` | `QUEUED` |

### Phase C

| Legacy ID | Destination | Result |
|---|---|---|
| `FL-C200` | `meta/tasks/FL-C200.md` | `QUEUED` |
| `FL-C210` | `meta/tasks/FL-C210.md` | `QUEUED` |
| `FL-C220` | `meta/tasks/FL-C220.md` | `QUEUED` |
| `FL-C230` | `meta/tasks/FL-C230.md` | `QUEUED` |
| `FL-C240` | `meta/tasks/FL-C240.md` | `QUEUED` |
| `FL-C250` | `meta/tasks/FL-C250.md` | `QUEUED` |
| `FL-C260` | `meta/tasks/FL-C260.md` | `QUEUED` |

### Phase D

| Legacy ID | Destination | Result |
|---|---|---|
| `FL-D300` | `meta/tasks/FL-D300.md` | `FROZEN` |
| `FL-D310` | `meta/tasks/FL-D310.md` | `FROZEN` |
| `FL-D320` | `meta/tasks/FL-D320.md` | `FROZEN` |
| `FL-D330` | `meta/tasks/FL-D330.md` | `FROZEN` |
| `FL-D340` | `meta/tasks/FL-D340.md` | `FROZEN` |
| `FL-D350` | `meta/tasks/FL-D350.md` | `FROZEN` |
| `FL-D360` | `meta/tasks/FL-D360.md` | `FROZEN` |

## Dependencies to encode

Migration preserves only structural dependencies that are useful for recovery/scheduling:

- `FL-B160` depends on `FL-B150` because it uses the root-provider composition introduced by B150.
- `FL-B170` depends on `FL-B150` and `FL-B160` so the supported developer harness can exercise both initial root mechanisms.
- `FL-B180` depends on `FL-B150` and `FL-B160`; it may be developed independently of B170.
- `FL-B190` depends on B150, B160, B170, and B180 because it is the supported analyzer validation gate.
- `FL-C200` depends on `FL-B190`: the migration keeps the product push linear at the supported-analyzer/Rider boundary.
- `FL-C210` depends on `FL-C200`.
- `FL-C220` depends on `FL-C210`.
- `FL-C230` depends on `FL-C210`.
- `FL-C240` depends on `FL-C230`.
- `FL-C250` depends on `FL-C220`, `FL-C230`, and `FL-C240`.
- `FL-C260` depends on `FL-C220`, `FL-C230`, `FL-C240`, `FL-C250`, and `FL-B190`.
- Every Phase D task depends on `FL-C260` because legacy policy explicitly places Phase D after the working-product gate.

## Archive convention

Terminal task specifications live beneath:

```text
meta/tasks/archive/
```

Use one stable-ID file per historical product task under `archive/product-history/`.

Completed migration blocks are archived as intact block directories under `archive/`, preserving their task specs and useful workspace evidence. Terminal tasks do not remain on the active task index.

Git history remains the record of released claims and intermediate state; do not duplicate claim history in archive files.
