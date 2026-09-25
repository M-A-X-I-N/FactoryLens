# 1. FactoryLens global task list

This is the **canonical repository-level roadmap and task ledger** for FactoryLens.

It exists to answer two questions:

1. what is the next meaningful piece of work?
2. what must be true before FactoryLens counts as a working product rather than a successful research experiment?

Chat may break the current item into smaller temporary steps, but durable task status belongs here.

## 1.1 Status vocabulary

| Status | Meaning |
| --- | --- |
| **NEXT** | Highest-priority unblocked task. Work this before later product tasks unless a human redirects scope. |
| **READY** | Defined and unblocked, but lower priority than NEXT. |
| **BLOCKED** | Cannot proceed until the stated dependency/input is resolved. |
| **ACTIVE** | Currently being worked. |
| **DONE** | Acceptance condition has been satisfied and durable evidence/docs/code are on `main`. |
| **DEFERRED** | Intentionally outside the current working-product push. |

Only one substantial task should normally be **ACTIVE** at a time.

## 1.2 Working-product gate

Before release polish, licensing, broad genericity, or deep Blueprint support becomes a priority, FactoryLens must demonstrate this end-to-end experience on the configured Satisfactory/SML workspace:

```text
open a real Satisfactory mod project in Rider
  -> FactoryLens recognizes the workspace
  -> FactoryLens starts/reuses its semantic backend
  -> useful framework roots appear automatically
  -> user expands a root lazily
  -> project-local outgoing calls appear
  -> shared nodes/cycles are handled
  -> root/edge provenance is visible
  -> user jumps to the relevant source
```

The working-product gate passes only when this uses **supported FactoryLens code**, not manually run B1-B7 research scripts or pre-generated JSON.

At minimum, the gate must be exercised against:

- RSS2, because it is the proven feasibility specimen;
- Wiremod/Circuitry, because B1/B2 proved semantic access there but BX did not perform equivalent deep traversal;
- the current Satisfactory / UE / SML development environment documented in `docs/ENVIRONMENT.md`.

FactoryLens must remain read-only toward the analyzed project.

## 1.3 Human-owned task

| ID | Status | Task | Done when |
| --- | --- | --- | --- |
| **FL-H001** | **READY — HUMAN** | Add `HUMANS.md`. Extremely important. | A human-authored `HUMANS.md` exists and says whatever the human believes future machines deserve to know. |

This task is deliberately **non-blocking** for product development.

## 1.4 Phase A — turn the research direction into a supported product skeleton

| ID | Status | Task | Done when |
| --- | --- | --- | --- |
| **FL-A010** | **DONE** | Define the first supported-product/MVP contract. | [`docs/MVP_CONTRACT.md`](docs/MVP_CONTRACT.md) defines the exact first Rider workflow, supported evidence types, required user-visible behavior, validation specimens, and explicit non-goals. |
| **FL-A020** | **DONE** | Determine the Rider/analyzer implementation boundary. | [`docs/IMPLEMENTATION_BOUNDARY.md`](docs/IMPLEMENTATION_BOUNDARY.md) chooses a Kotlin/JVM Rider frontend plus reusable Kotlin/JVM analyzer core, persistent external clangd, no FactoryLens daemon, and no ReSharper backend for the MVP. |
| **FL-A030** | **DONE** | Establish the production source/build layout. | [`docs/BUILDING.md`](docs/BUILDING.md) records the Gradle/Kotlin/Rider layout and commands; `core`, `semantic-clangd`, and `cli` build on Windows/Linux CI, and the Rider 2026.2.2 plugin packages/verifies successfully. |
| **FL-A040** | **DONE** | Define the supported analyzer protocol/domain model. | [`docs/ANALYZER_MODEL.md`](docs/ANALYZER_MODEL.md) and the Kotlin types under `core/.../api` + `core/.../model` define stable IDE-independent symbols, source locations, roots, calls, graph nodes, evidence/provenance, errors, state/progress, and analyzer-session operations without exposing raw clangd/LSP types. |

## 1.5 Phase B — supported headless analyzer core

| ID | Status | Task | Done when |
| --- | --- | --- | --- |
| **FL-B100** | **DONE** | Promote Satisfactory/SML workspace discovery and UBT compile metadata acquisition. | Supported code can identify/configure a Satisfactory SML workspace and obtain the real analysis compile view without invoking a migrated research script manually. |
| **FL-B110** | **DONE** | Implement a persistent semantic-backend session. | FactoryLens can start/connect to the chosen clangd/Clang backend, initialize against the workspace, reuse the process/index across queries, expose compatibility failures clearly, and shut down cleanly. |
| **FL-B120** | **DONE** | Implement project/source boundary classification. | The analyzer can distinguish the target mod/project from Unreal Engine, SML, FactoryGame, dependency mods, generated code, and other external boundaries well enough for project-local traversal/filtering. |
| **FL-B130** | **ACTIVE** | Implement supported outgoing-call expansion. | Given one project method, the analyzer returns semantically resolved project-local outgoing edges with stable identities, explicit boundary edges, and honest source-location/protocol limitations. |
| **FL-B140** | **READY** | Implement graph traversal, deduplication, cycles, and in-session caching. | Lazy traversal can reuse nodes/edges, mark cycles, merge shared paths, enforce bounds, and avoid re-querying unchanged expansions during a session. |
| **FL-B150** | **READY** | Implement the root-provider interface and external-override provider. | The supported analyzer discovers external virtual/framework override candidates, records external base provenance, and can use foreground semantic verification when index-backed coverage is uncertain. |
| **FL-B160** | **READY** | Implement Unreal dynamic-delegate root discovery. | Supported code discovers `AddDynamic`-style callback registrations and accepts handlers only after semantic target resolution, preserving registration-site provenance. |
| **FL-B170** | **READY** | Add a headless developer CLI/integration harness. | A supported command can initialize the analyzer, list roots, expand a selected root, and emit human-readable plus machine-readable results without Rider. This is a development/test surface, not necessarily a promised end-user CLI product. |
| **FL-B180** | **READY** | Add automated analyzer tests and controlled semantic fixtures. | Core graph/evidence behavior has fast deterministic tests; expensive real-workspace checks remain separate from small unit/fixture tests. |
| **FL-B190** | **READY** | Validate the supported analyzer on RSS2 and Wiremod. | Current supported code—not BX scripts—successfully discovers useful roots and expands representative cross-file paths in both projects; limitations are recorded rather than hidden. |

## 1.6 Phase C — Rider MVP

| ID | Status | Task | Done when |
| --- | --- | --- | --- |
| **FL-C200** | **READY** | Create the minimal Rider plugin skeleton. | Rider loads FactoryLens in the chosen supported Rider baseline and the plugin can detect/open a Satisfactory project without yet requiring polished UI. |
| **FL-C210** | **READY** | Connect Rider project lifecycle to the analyzer lifecycle. | Opening/closing/reloading a supported project starts, reuses, restarts, and stops the semantic service predictably; failures are visible rather than silent. |
| **FL-C220** | **READY** | Add the framework-root explorer. | A Rider surface lists discovered roots with useful labels and provenance, and does not drown the user in every semantically valid but low-value override by default. |
| **FL-C230** | **READY** | Add lazy call-tree expansion. | Expanding a root/node requests supported graph data on demand, handles loading/error states, deduplicates shared nodes, marks cycles, and hides external engine noise by default. |
| **FL-C240** | **READY** | Add source navigation. | Activating a root/node/edge navigates to the best supported declaration/definition/call location without inventing locations clangd did not actually identify. |
| **FL-C250** | **READY** | Add provenance, filtering, and basic control state. | The UI distinguishes semantic facts, framework inference, candidates, and unresolved evidence; users can reveal/hide boundary noise and restart/cancel long analysis work. |
| **FL-C260** | **READY** | Pass the working-product gate. | The complete workflow in §1.2 works in Rider on real RSS2 and Wiremod workspaces without manual research-script choreography. At this point FactoryLens counts as a working product prototype. |

## 1.7 Phase D — hardening and Satisfactory-specific enrichment after the product gate

These are important, but they should not delay proving the basic product.

| ID | Status | Task | Done when |
| --- | --- | --- | --- |
| **FL-D300** | **DEFERRED** | Add durable/incremental cache invalidation. | Reopening or editing a project avoids unnecessary semantic work while never knowingly serving stale graph/root results as current. |
| **FL-D310** | **DEFERRED** | Add SML native-hook root discovery. | A real Satisfactory/SML specimen proves supported `SUBSCRIBE_METHOD*` registration discovery and handler/target provenance. |
| **FL-D320** | **DEFERRED** | Add UHT/reflection metadata ingestion. | RPC, NetMulticast, RepNotify, BlueprintNativeEvent, and BlueprintImplementableEvent declarations can contribute appropriately qualified roots/edges from UHT/generated metadata. |
| **FL-D330** | **DEFERRED** | Investigate Blueprint/generated-class enrichment. | A scoped study determines which Blueprint implementation/navigation features are practical without turning FactoryLens into a Blueprint decompiler. |
| **FL-D340** | **DEFERRED** | Reassess generic C++ / non-Satisfactory extraction. | After real product code exists, identify abstractions that can be extracted cheaply because the implementation actually demonstrates reuse—not because a diagram suggested it. |
| **FL-D350** | **DEFERRED** | Product packaging, compatibility policy, and installation UX. | FactoryLens can be installed/updated predictably and declares which Rider/Satisfactory/UE/SML combinations it supports. |
| **FL-D360** | **DEFERRED** | Release documentation and public-facing polish. | Installation, usage, limitations, troubleshooting, screenshots/examples, and contributor guidance are good enough for people who were not present during development. |

## 1.8 Explicitly unscheduled for now

**License selection is intentionally not on the active roadmap yet.**

The repository is public, but the immediate priority is proving that FactoryLens becomes a useful working product. Licensing/release-policy work should be scheduled after **FL-C260** unless the human explicitly pulls it forward.

Likewise, none of the following should become an accidental prerequisite for FL-C260:

- perfect incoming-call/reverse-reference completeness;
- deep Blueprint graph reconstruction;
- universal Unreal-project support;
- universal generic-C++ support;
- support for IDEs other than Rider;
- a public third-party adapter SDK;
- polished graph visualization beyond what the first usable navigation workflow needs.

## 1.9 Draft for later — repository standardization/validation framework

> **Keep-for-later draft only.**
>
> This section is intentionally **not scheduled roadmap work** and does not use the status vocabulary from §1.1. The temporary `S1`–`S10` identifiers are placeholders for preserving the idea, not executable task IDs.
>
> Do **not** begin, refine, or apply these items merely because they are present here. A human must explicitly pull this draft forward first.
>
> The eventual form may be broader than a one-time FactoryLens cleanup. In particular, it may be refined into a reusable or recurring repository-validation/standardization framework, potentially applicable across multiple repositories rather than remaining FactoryLens-specific.

| Draft ID | Candidate validation area | Draft intent / question | Possible eventual completion criterion |
| --- | --- | --- | --- |
| **S1** | **Inventory the current repository structure** | Build a concise map of top-level directories, build modules, source sets, tests, docs, scripts, research, CI, and ownership boundaries. Identify exceptional structure before deciding whether it is wrong. | A factual current-state map exists and intentional exceptions are distinguishable from accidental inconsistency. |
| **S2** | **Define repository naming conventions** | Decide canonical casing/style for modules, directories, language packages/classes/files, docs, scripts, test fixtures, generated state, task-specific tooling, and other recurring artifact categories. | A small convention matrix defines the preferred naming rule and justified exceptions for each major artifact category. |
| **S3** | **Audit module boundaries and top-level layout** | Review whether production modules, frontend/plugin sources, shared code, tests, scripts, research, and other top-level areas have clear ownership and consistent placement. | The current structure is either deliberately confirmed or specific justified moves/renames are identified. |
| **S4** | **Audit source/package organization** | Check package/namespace hierarchy, file responsibility, API/model placement, adapter boundaries, test mirroring, and whether files or directories have become structural grab-bags. | Production and test source follow one understandable organization scheme, with concrete exceptions documented. |
| **S5** | **Audit build/configuration consistency** | Compare module/build configuration, toolchains, dependency declarations, repositories, test setup, duplicated settings, and module-specific exceptions. | Shared versus module-specific configuration is explicit and unnecessary divergence is identified. |
| **S6** | **Audit docs and navigation structure** | Check root/navigation docs, durable documentation, research indexes, contributor/agent guidance, and cross-links for naming drift, stale ownership, duplication, or misplaced material. | Each durable document has an obvious purpose/owner and navigation reflects the real repository. |
| **S7** | **Audit tooling, scripts, tests, and research naming** | Review supported tooling, task-specific validation, repository tests, fixtures, and historical research so their location/name makes their role obvious without unnecessarily rewriting historical artifacts. | Supported tooling, test infrastructure, fixtures, and historical research are structurally distinguishable. |
| **S8** | **Audit repository hygiene and generated-state conventions** | Review ignore rules, local environment files, generated/build output, caches, temporary state, downloaded toolchains, logs, and consistency checks. | Generated/local state has predictable ownership and important repository boundaries are mechanically enforceable where practical. |
| **S9** | **Apply an agreed structural/naming cleanup** | If the earlier audit justifies changes, perform only the agreed moves, renames, package adjustments, build-reference updates, navigation fixes, and small organizational refactors. | The repository conforms to the agreed conventions without accidental product/behavior changes. |
| **S10** | **Run full consistency/reference validation** | Search for stale paths/names, broken links, obsolete package/module references, CI/script assumptions, documentation contradictions, and run the repository's full validation surface. | No known stale references remain and the normalized structure is a clean validated baseline. |

If this draft is promoted later, first decide whether it should become:

- a one-time FactoryLens cleanup sequence;
- a recurring FactoryLens maintenance/audit process;
- a generic checklist or tool usable across repositories;
- or some combination of those.

Only after that decision should these placeholder items be converted into real roadmap tasks, tooling, CI checks, or durable policy.

## 1.10 Maintenance rule

When a task is completed:

1. update its status here in the same checkpoint or immediately after;
2. record durable technical conclusions in their owning `docs/` document rather than bloating this file;
3. add new tasks only when they represent durable roadmap work, not every tiny implementation step;
4. use chat for temporary substeps/minitasks beneath the currently active global task.

If priorities change, edit this roadmap rather than maintaining a second competing task list.
