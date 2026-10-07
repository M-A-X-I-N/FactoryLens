# Repository standardization / validation initiative

Status: **parked, non-executable**

## Goal

Preserve the legacy S1–S10 repository-standardization idea without silently turning any part of it into executable work.

This initiative is deliberately parked. Its existence does not authorize investigation, cleanup, tooling, CI changes, renames, or structural refactors.

## Current state

The idea originated as a keep-for-later draft inside the legacy root task ledger. It may eventually become:

- a one-time FactoryLens cleanup sequence;
- a recurring FactoryLens maintenance/audit process;
- a generic checklist/tool usable across repositories;
- or some combination of those.

That design decision must happen before any candidate area is promoted into tasks.

## Candidate areas

### S1 — Inventory the current repository structure

Build a concise map of top-level directories, build modules, source sets, tests, docs, scripts, research, CI, and ownership boundaries. Identify exceptional structure before deciding whether it is wrong.

Possible closure evidence: a factual current-state map where intentional exceptions can be distinguished from accidental inconsistency.

### S2 — Define repository naming conventions

Decide canonical casing/style for modules, directories, language packages/classes/files, docs, scripts, test fixtures, generated state, task-specific tooling, and other recurring artifact categories.

Possible closure evidence: a small convention matrix with justified exceptions.

### S3 — Audit module boundaries and top-level layout

Review whether production modules, frontend/plugin sources, shared code, tests, scripts, research, and other top-level areas have clear ownership and consistent placement.

Possible closure evidence: deliberate confirmation of the structure or a bounded list of justified moves/renames.

### S4 — Audit source/package organization

Check package/namespace hierarchy, file responsibility, API/model placement, adapter boundaries, test mirroring, and structural grab-bags.

Possible closure evidence: one understandable organization scheme with concrete exceptions documented.

### S5 — Audit build/configuration consistency

Compare module/build configuration, toolchains, dependency declarations, repositories, test setup, duplicated settings, and module-specific exceptions.

Possible closure evidence: shared versus module-specific configuration is explicit and unnecessary divergence is identified.

### S6 — Audit docs and navigation structure

Check root/navigation docs, durable documentation, research indexes, contributor/agent guidance, and cross-links for naming drift, stale ownership, duplication, or misplaced material.

Possible closure evidence: every durable document has an obvious purpose/owner and navigation reflects reality.

### S7 — Audit tooling, scripts, tests, and research naming

Review supported tooling, task-specific validation, repository tests, fixtures, and historical research so their location/name makes their role obvious without unnecessarily rewriting historical artifacts.

Possible closure evidence: supported tooling, test infrastructure, fixtures, and historical research are structurally distinguishable.

### S8 — Audit repository hygiene and generated-state conventions

Review ignore rules, local environment files, generated/build output, caches, temporary state, downloaded toolchains, logs, and consistency checks.

Possible closure evidence: generated/local state has predictable ownership and important boundaries are mechanically enforceable where useful.

### S9 — Apply an agreed structural/naming cleanup

Only if the earlier audit justifies changes, perform the explicitly agreed moves, renames, package adjustments, build-reference updates, navigation fixes, and small organizational refactors.

Possible closure evidence: repository conforms to the agreed conventions without accidental product behavior changes.

### S10 — Run full consistency/reference validation

Search for stale paths/names, broken links, obsolete package/module references, CI/script assumptions, and documentation contradictions; run the appropriate validation surface.

Possible closure evidence: no known stale references remain and the normalized structure is a clean validated baseline.

## Deliberate boundaries

- S1–S10 are candidate labels, not executable task IDs.
- Do not refine or implement them merely because this initiative exists.
- Do not place this initiative or any candidate area in Dispatch without deliberate promotion.
- Existing CI work/validation is not implicitly pulled forward by S5/S10; per current human direction, CI concerns remain in the “not now” bucket until explicitly revisited.

## Promotion criteria

Before promotion, decide the intended scope/model (one-time, recurring, generic, or mixed) and create sufficiently specified baseline tasks for only the authorized work.
