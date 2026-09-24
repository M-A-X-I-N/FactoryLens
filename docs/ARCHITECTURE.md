# 1. FactoryLens architecture

Status: **initial architecture direction**, derived from the completed B1-B8 External Call Map feasibility study and the accepted FL-A020 implementation-boundary decision.

This document describes responsibility boundaries and data flow. FL-A020 now chooses Kotlin/JVM for the Rider frontend and reusable analyzer core, with clangd as a persistent external process and no separate FactoryLens daemon or ReSharper backend for the MVP. See [`IMPLEMENTATION_BOUNDARY.md`](IMPLEMENTATION_BOUNDARY.md). Packaging details and the long-term public API remain open.

## 1.1 Architectural goal

FactoryLens should help a developer understand Satisfactory mod execution without pretending ordinary C++ caller search can see every Unreal/SML framework boundary.

The core direction is:

```text
UnrealBuildTool compile truth
  -> semantic backend
     -> framework-entry adapters
        -> bounded cached graph/model
           -> Rider frontend
```

The difficult semantic work should remain testable independently from Rider.

## 1.2 Layer 1 — compile/workspace truth

**Owner:** UnrealBuildTool and FactoryLens workspace integration.

Responsibilities:

- identify the Satisfactory/SML Unreal project;
- select the intended target/platform/configuration;
- obtain real translation-unit compile commands;
- preserve UBT-provided include paths, defines, response-file arguments, generated-header paths, and module boundaries;
- expose the project/source boundaries FactoryLens should treat as local versus external.

The B1/B2 research established that reconstructing Unreal compilation rules ourselves is unnecessary and undesirable.

This layer should not:

- infer call edges;
- decide which framework callbacks matter;
- build the graph;
- contain Rider presentation logic.

## 1.3 Layer 2 — semantic backend

**Initial proven backend:** clangd/Clang semantics.

The semantic layer should provide operations equivalent to:

```text
resolve symbol
resolve definition/declaration
prepare symbol/call-hierarchy identity
enumerate outgoing calls
inspect type hierarchy
inspect foreground AST facts
resolve semantic target at a registration site
```

The product-facing contract should describe semantic operations rather than exposing clangd LSP response shapes everywhere.

Why:

- clangd versions and protocol quirks may change;
- some correctness checks may need direct Clang/AST information;
- Rider should not need to know whether a fact came from an index, a foreground AST, or another compatible backend.

### Known semantic caveat

BX demonstrated that the background index can be useful while still incomplete.

Therefore FactoryLens must distinguish:

```text
useful semantic evidence
!=
proof of whole-program completeness
```

Forward/outgoing traversal is currently the strongest proven call-map path. Reverse/incoming-reference features must not silently claim completeness until separately proven.

## 1.4 Layer 3 — framework entry-point adapters

Ordinary C++ semantics answer questions such as "what does this function call?"

Framework adapters answer a different question:

> **Why can execution enter this project method without an ordinary project-local caller?**

Each adapter should contribute roots or root metadata using the cheapest trustworthy evidence available.

### Proven adapter families

#### External virtual/framework overrides

Evidence:

- semantic type hierarchy;
- external base declaration;
- reverse-index candidate discovery where useful;
- foreground Clang AST override relation when authoritative verification is needed.

Representative categories:

- Unreal Actor/component lifecycle;
- module lifecycle;
- framework-defined virtual callbacks.

#### Unreal dynamic delegates

Evidence:

- a narrow registration shape such as `AddDynamic`;
- semantic resolution of the callback member-function target;
- registration location/provenance.

B7 proved this on all 13 current RSS2 registrations in the configured specimen.

### Future adapter families

#### SML native hooks

Likely evidence:

- explicit `SUBSCRIBE_METHOD*` registration;
- semantic target and handler resolution.

The architecture is analogous to delegate registration, but BX did not contain a real RSS2 specimen and therefore did not claim support.

#### UHT/reflection dispatch

Examples:

- Server/Client/NetMulticast RPCs;
- RepNotify / `ReplicatedUsing`;
- BlueprintNativeEvent;
- BlueprintImplementableEvent.

These require explicit UHT/generated/reflection metadata or carefully scoped annotation handling. Plain ordinary C++ call hierarchy is not sufficient evidence.

#### Blueprint/asset execution

Actual Blueprint implementations and Blueprint graph execution require asset/generated-class information beyond the initial C++ semantic core.

This should remain an enrichment layer rather than a prerequisite for the first useful FactoryLens product.

## 1.5 Layer 4 — graph/model

The graph layer owns navigation state rather than compiler semantics.

Responsibilities:

- stable project-local node identity;
- directed call edges;
- root identity and root provenance;
- project-boundary filtering;
- duplicate/shared-node collapse;
- cycle detection/markers;
- lazy expansion;
- depth limiting;
- caching;
- external/boundary edge suppression and optional reveal;
- confidence/evidence metadata.

A root or edge should be able to explain **why** FactoryLens believes it exists.

Examples:

```text
manual-confirmed
semantic outgoing call
semantic external override
foreground-AST override proof
Unreal AddDynamic registration
future SML hook registration
future UHT/reflection metadata
```

The model should not flatten all of these into a boolean "known root" flag.

## 1.6 Layer 5 — Rider frontend

Rider is the intended first-class frontend.

The frontend owns:

- project/user interaction;
- tool-window or editor presentation;
- expand/collapse behavior;
- filtering;
- progress/background-state presentation;
- jump-to-source navigation;
- user confirmation/suppression of candidate roots;
- display of provenance/confidence;
- settings appropriate to the IDE experience.

The frontend should not be the only place where FactoryLens semantic truth exists.

The analyzer/graph behavior should remain testable without starting Rider.

## 1.7 Satisfactory-first generalization

FactoryLens should **design for extraction without prematurely extracting**.

The conceptual shape may eventually resemble:

```text
semantic/graph core
       |
       +-- generic C++ fallback
       |
       +-- Unreal-aware adapters
               |
               +-- Satisfactory/SML adapters
```

but this is not permission to build three framework layers before one Satisfactory workflow exists.

The first implementation may keep Satisfactory/Unreal concerns close together while maintaining clean interfaces around the semantic and graph layers. Extract shared Unreal or generic-C++ packages only when real duplication or another supported use case justifies it.

## 1.8 Performance model

B5 showed that eager whole-subtree traversal is technically possible but not the correct interaction model.

The intended product behavior is:

```text
persistent semantic process/index
  -> discover/cache roots
  -> show shallow project-local children
  -> expand one node/subtree on demand
  -> cache semantic results
  -> collapse shared nodes
  -> preserve cycle markers
  -> hide engine/external noise by default
```

Cold indexing may be expensive. Normal navigation should amortize that cost rather than intentionally re-indexing the workspace for every interaction.

## 1.9 Data provenance and honesty

FactoryLens should preserve the distinction between:

- **established semantic fact** — e.g. Clang resolves a specific call or override;
- **framework inference** — e.g. a known registration construct implies a callback boundary;
- **candidate** — e.g. a semantically valid external override that may be uninteresting as a product root;
- **unsupported/unresolved** — e.g. runtime Blueprint behavior not represented in the available static evidence.

UI wording and APIs should not promote weaker evidence into stronger claims.

## 1.10 Initial product boundary

The first useful FactoryLens product is C++-semantic-first.

A credible initial feature set is:

```text
discover Satisfactory/Unreal framework roots
  -> explain root provenance
  -> traverse project-local outgoing calls
  -> lazy-expand a tree/graph
  -> deduplicate shared paths
  -> mark cycles
  -> navigate to source
```

Deep Blueprint graph reconstruction, universal C++ framework analysis, perfect reverse-call completeness, and broad IDE support are not prerequisites.

## 1.11 Decisions intentionally still open

FL-A020 resolved the implementation language/process questions for the MVP. The following remain open:

- exact Gradle/module/source layout and pinned dependency versions;
- exact product-facing analyzer/domain interfaces;
- exact clangd LSP client/transport implementation;
- cache persistence format/location;
- packaging/updating strategy;
- exact UHT metadata ingestion route;
- public extension API for third-party framework adapters.

Choose these through focused implementation/design tasks rather than guessing them into the repository bootstrap.
