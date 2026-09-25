# 1. First supported-product / MVP contract

Status: **accepted initial contract** for the first FactoryLens working-product gate.

This document defines what FactoryLens must do before the project can claim it has a **working product prototype** rather than only successful feasibility research.

It deliberately does **not** decide the implementation language, process/IPC boundary, Rider plugin SDK structure, packaging model, or long-term compatibility policy. Those are owned by later roadmap tasks.

## 1.1 Product statement

The first FactoryLens MVP should let a developer using Rider answer:

> **Where can execution enter this Satisfactory mod, what project code can it reach from there, why does FactoryLens believe that path exists, and can I jump to the relevant source?**

The MVP is successful when that question can be answered interactively from supported FactoryLens code on real Satisfactory mod sources.

## 1.2 Supported workspace shape

The first MVP targets the current Satisfactory/SML development shape:

```text
SML Starter Project
  -> FactoryGame.uproject
  -> FactoryEditor / Win64 / Development analysis view
  -> one selected mod/source boundary
```

FactoryLens may use the whole Unreal/SML workspace to obtain compile truth and resolve external symbols, but the user-facing call map analyzes **one selected mod/source boundary at a time**.

Examples from the feasibility work:

```text
RSS2:
Mods/GameFeatures/RSS/Source/RSS/

Wiremod / Circuitry:
Mods/FicsitWiremod/Source/FicsitWiremod/
```

These paths are specimens, not product hard-coding requirements.

### Target selection

For the MVP:

- FactoryLens may infer the analysis target from the active file when that ownership is unambiguous;
- if more than one plausible mod/source boundary exists, FactoryLens must let the user choose explicitly;
- the current target must be visible in the UI;
- switching targets must not require restarting Rider;
- FactoryLens must not silently analyze every mod/dependency in the Starter Project as though they form one product graph.

## 1.3 Required end-to-end Rider workflow

A working MVP must support this sequence:

```text
1. developer opens the configured Satisfactory/SML project in Rider

2. FactoryLens recognizes or is pointed at the workspace
   -> target mod/source boundary is inferred or selected

3. FactoryLens starts/reuses its semantic backend
   -> progress/status is visible
   -> failures are visible

4. FactoryLens presents automatically discovered framework roots
   -> useful roots are shown by default
   -> provenance/category is visible

5. developer expands a root
   -> project-local outgoing calls are queried lazily
   -> children appear without eager whole-project traversal

6. developer expands deeper nodes
   -> shared nodes are recognized
   -> cycles do not recurse forever
   -> external/engine noise is hidden by default

7. developer inspects why a root/edge exists
   -> semantic/framework evidence is distinguishable

8. developer activates a node/root
   -> Rider navigates to the best supported source location
```

No manual invocation of migrated B1-B7 research scripts may be required for this workflow.

## 1.4 Required automatic root families

The MVP must support the two root families already proven by BX.

### 1.4.1 External virtual/framework overrides

FactoryLens must be able to identify project methods that override methods declared outside the selected project boundary.

Required evidence includes enough of the following to explain the classification:

- project method identity/location;
- external base method identity/location;
- semantic type/override relationship;
- whether the relation came from index-backed candidate discovery or stronger foreground semantic verification when needed.

The UI should prefer high-value framework/lifecycle roots by default.

Semantically valid but low-value generic overrides may be available behind filtering/secondary presentation; the MVP is **not** required to dump every external override into the primary root list.

### 1.4.2 Unreal dynamic-delegate callbacks

FactoryLens must identify supported `AddDynamic`-style callback registrations when:

1. a narrow supported registration form is recognized; and
2. semantic resolution confirms the referenced callback method belongs to the selected project boundary.

The root evidence must retain the registration site so FactoryLens can explain **where the callback became externally reachable**.

A textual registration match without semantic target resolution is not enough to present a confirmed callback root.

## 1.5 Required call-edge evidence

The MVP call tree is based on **semantic outgoing-call information**.

For each project-local edge, FactoryLens must preserve:

- caller identity;
- callee identity;
- callee declaration/definition location when available;
- evidence source sufficient to distinguish a semantic call from a framework-inferred root relationship.

FactoryLens must not claim whole-program call-graph completeness.

### Source-location honesty

BX exposed a clangd call-hierarchy limitation where a caller item can be canonicalized to a header while returned call ranges originate in an implementation body without a corresponding per-range URI.

The MVP must therefore:

- use a call-site location only when the semantic backend actually identifies enough information to map it reliably;
- otherwise navigate to a trustworthy declaration/definition;
- never invent a source URI merely to make navigation look complete.

## 1.6 Required graph behavior

The first UI may be an expandable **tree-oriented view**. A force-directed/general graph visualization is not required.

The graph/model layer must support:

- lazy expansion;
- stable node identity within an analysis session;
- project-boundary filtering;
- duplicate/shared-node recognition;
- cycle detection/markers;
- depth/node safety bounds;
- in-session reuse/caching of completed queries;
- explicit external/boundary edges even when hidden from the default view.

### Default presentation

By default:

- show project-local nodes/edges;
- hide ordinary Engine/container/template/logging noise;
- expose a control to reveal external/boundary calls;
- do not eagerly expand every root or subtree.

## 1.7 Evidence/provenance visible to the user

FactoryLens must not flatten all evidence into a single boolean "root" or "edge" label.

The MVP must be able to distinguish at least:

| Evidence class | Meaning |
| --- | --- |
| **Semantic call** | Clang/clangd reports an ordinary outgoing C++ call relationship. |
| **Semantic external override** | Project method semantically overrides a method declared outside the selected project boundary. |
| **Foreground override verification** | Foreground Clang AST/semantic information verifies an override where index-backed coverage is uncertain. |
| **Unreal dynamic-delegate registration** | A supported `AddDynamic` registration resolves semantically to the project callback. |
| **Candidate / lower-confidence classification** | Semantic evidence exists, but FactoryLens does not claim the item is a high-value framework entry point. |
| **Unresolved / unsupported** | FactoryLens lacks sufficient evidence to make a stronger claim. |

The exact visual design is open, but the user must be able to inspect the provenance of a displayed root and understand that not all categories mean the same thing.

## 1.8 Required status/error behavior

FactoryLens analysis work can be expensive. The Rider UI must remain responsive.

The MVP must visibly distinguish at least:

```text
not configured / unsupported workspace
starting semantic backend
indexing / preparing
ready
query in progress
degraded / partial evidence
error
```

A semantic-backend failure, incompatible backend/toolchain, or missing compile metadata must produce a useful error state rather than an empty root list that looks like "no entry points exist."

The MVP must support restarting/retrying the analysis session without restarting Rider.

## 1.9 Read-only contract

FactoryLens is a developer-analysis tool.

The first MVP must not modify:

- the analyzed mod source;
- the SML Starter Project;
- Unreal Engine or FactoryGame source;
- dependency mods;
- generated UHT source;
- Rider project configuration;

merely to make analysis work.

FactoryLens-generated indexes, caches, logs, and intermediate analysis state belong in FactoryLens-controlled generated/cache locations.

### Built/generated-state exception policy

Authored source and project-defining configuration remain the strict read-only baseline. Built, generated, or disposable toolchain state may be modified or regenerated when a supported analysis path genuinely requires it, including state previously created by FactoryLens or by the normal Unreal/SML build toolchain.

That permission is **not blanket permission** to mutate `Intermediate/`, generated files, or mod-local build state. Every such occurrence must have its own documented exception that records:

- the exact file/path class or generated-state family involved;
- which supported FactoryLens operation causes the mutation or regeneration;
- why the write is necessary or materially simpler/safer than avoiding it;
- the evidence that the side effect is bounded and does not alter authored/project-defining state;
- the validation or audit rule that distinguishes the accepted mutation from unexpected writes.

Regenerating disposable build output is acceptable under the same rule when regeneration is the supported toolchain behavior. A previous exception does not automatically authorize a new generated-state write merely because both live under `Intermediate/` or another ignored directory.

### Narrow UBT bookkeeping exception

Compile-metadata acquisition may invoke UnrealBuildTool against an already-built SML workspace. The supported `GenerateClangDatabase` path uses `-NoExecCodeGenActions`, but UBT still refreshes its existing generated bookkeeping files matching:

```text
**/Intermediate/Build/**/UHT/Timestamp
```

Those files are not authored source or generated UHT source; they are UnrealBuildTool-owned timestamp/bookkeeping state. Rewriting an **existing** file in that exact class is an accepted exception to the read-only baseline.

The exception is deliberately narrow:

- FactoryLens must continue auditing the workspace before and after compile-metadata acquisition;
- the file must exist both before and after the UBT invocation;
- additions or removals are not accepted by this exception;
- generated headers/source, project configuration, authored source, plugin descriptors, and every other workspace mutation remain unexpected unless covered by another separately documented exception below;
- unexpected mutations must fail the validation path rather than being silently ignored.

This exception exists because real FL-B100 validation showed that `-NoExecCodeGenActions` reduces UBT side effects to UHT timestamp bookkeeping while still producing the real compile view. It is one concrete exception under the built/generated-state policy above and must not be generalized into permission for FactoryLens to write arbitrary generated or mod-local files.

### UBT compile-metadata build-state exception

Switching the supported `GenerateClangDatabase` compiler view between Visual Studio/MSVC and Clang may rewrite existing UnrealBuildTool-owned build state under:

```text
**/Intermediate/Build/**/*.rsp
**/Intermediate/Build/**/*.rsp.old
**/Intermediate/Build/**/Definitions.h
**/Intermediate/Build/**/Definitions.h.old
**/Intermediate/Build/**/TargetMetadata.dat
```

Rewriting an **existing** file in one of those exact generated-state families is an accepted exception during compile-metadata acquisition. Additions and removals are not accepted by this exception, and it does not permit generated `.cpp`/`.h` source, object files, authored source, project configuration, plugin descriptors, or unrelated `Intermediate` files to change.

This exception is grounded in real FL-B110 validation of the Clang compiler view after the workspace had most recently held the MSVC view. The audit observed 4,601 otherwise unexpected changes, all under `Intermediate/Build`: 2,269 `.rsp` files, 2,269 matching `.rsp.old` files, 31 `Definitions.h` files, 31 matching `Definitions.h.old` files, and one `TargetMetadata.dat`. Every response-file pair and every Definitions pair was an exact before/after SHA-256 swap between the active file and its `.old` backup, demonstrating UBT rotating compiler-specific build metadata rather than regenerating authored or UHT source. The UBT invocation still used `-NoExecCodeGenActions`.

A second consecutive Clang-view acquisition on the same workspace then produced only the previously accepted 36 UHT timestamp-bookkeeping rewrites and zero unexpected mutations. That repeatability check confirms the larger 4,601-file event is tied to compiler-view switching rather than ordinary repeated FactoryLens analysis.

The workspace audit must continue to report this class separately from UHT timestamp bookkeeping and must fail on every mutation outside the two documented exception families.

## 1.10 Validation specimens

The working-product gate must be exercised against real source in the configured development environment.

### RSS2

RSS2 is the primary proven feasibility specimen.

The supported MVP must demonstrate:

- automatically discovered useful external-override roots;
- automatically discovered supported dynamic-delegate callback roots;
- lazy multi-level project-local outgoing expansion;
- at least one cross-file semantic path;
- cycle/shared-node handling on real source;
- source navigation and visible provenance.

### Wiremod / Circuitry

Wiremod is the second required product specimen because BX proved compile/semantic access there but did not perform the same deep traversal work.

The supported MVP must demonstrate:

- at least one automatically discovered useful framework root supported by the implemented root providers;
- a real multi-level project-local outgoing path containing a cross-file edge;
- source navigation and provenance;
- no assumption that RSS2-specific source layout or symbols are required.

If Wiremod exposes a new compatibility problem in the supported analyzer, that problem must be resolved or explicitly narrow the claimed MVP support before FL-C260 can pass.

## 1.11 Performance contract

The MVP does not currently impose arbitrary millisecond thresholds that BX never measured.

It **does** impose behavioral requirements:

- maintain/reuse a semantic session rather than intentionally cold-starting for every query;
- do not re-index the whole workspace for every node expansion;
- perform expensive analysis asynchronously from Rider UI interaction;
- cache completed in-session expansion results;
- make progress visible during long operations;
- allow a failed/stuck analysis session to be restarted.

A design where ordinary node expansion routinely performs the equivalent of the old multi-minute eager B5 experiment is not an acceptable MVP even if it eventually returns correct data.

## 1.12 Explicit MVP non-goals

The first working product does **not** require:

- perfect incoming-call/reverse-reference completeness;
- perfect whole-program call-graph completeness;
- SML native-hook support;
- RPC / NetMulticast root discovery;
- RepNotify / `OnRep` discovery;
- BlueprintNativeEvent / BlueprintImplementableEvent enrichment;
- Blueprint implementation/graph reconstruction;
- durable cross-session graph cache invalidation;
- generic support for arbitrary Unreal projects;
- generic support for arbitrary C++ projects;
- IDEs other than Rider;
- a public third-party adapter SDK;
- polished force-directed graph visualization;
- marketplace/release packaging;
- final compatibility promises across many Rider/Satisfactory/SML versions;
- license selection.

Those are later roadmap concerns unless a dependency discovered during implementation forces one to move earlier.

## 1.13 What counts as the first working product

FactoryLens counts as a **working product prototype** when FL-C260 can be checked off with supported FactoryLens code and the complete Rider workflow in §1.3 succeeds on both RSS2 and Wiremod.

That milestone means:

> FactoryLens is useful enough to help a developer navigate real Satisfactory mod execution from framework entry points inside Rider.

It does **not** mean FactoryLens is release-ready, feature-complete, or semantically complete.

## 1.14 Relationship to later design tasks

This contract intentionally leaves these decisions to FL-A020 and later tasks:

- Rider plugin technology and supported Rider baseline;
- analyzer implementation language/runtime;
- in-process versus out-of-process analyzer;
- IPC/transport if needed;
- exact semantic-backend lifecycle;
- production source/build layout;
- product-facing domain/protocol types;
- cache persistence;
- packaging and installation.

Those decisions should be evaluated by how directly and robustly they satisfy this MVP contract.
