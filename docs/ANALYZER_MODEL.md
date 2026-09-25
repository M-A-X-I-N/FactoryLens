# 1. Supported analyzer protocol and domain model

Status: **initial supported model** for FL-A040.

This document defines the first product-facing data contract between FactoryLens frontends and the supported analyzer/core.

The corresponding Kotlin types live under:

```text
core/src/main/kotlin/dev/maxin/factorylens/core/
├─ api/
└─ model/
```

The contract is intentionally **FactoryLens-owned**. Rider objects and raw clangd/LSP structures must be adapted at the boundary rather than leaking through the rest of the product.

## 1.1 Design goals

The model must let Rider, the headless CLI, tests, and future frontends talk about the same analysis result without knowing which backend produced it.

The first contract therefore models:

- workspace and selected analysis target;
- semantic symbol identity;
- source/navigation locations;
- entry-point roots;
- ordinary call edges;
- graph nodes;
- evidence/provenance and confidence;
- result completeness;
- diagnostics;
- analyzer errors;
- analyzer state/progress;
- supported session operations.

It deliberately does **not** model every clangd LSP message or every Unreal/SML framework family.

## 1.2 Identity

FactoryLens uses small string-backed IDs:

```text
WorkspaceId
AnalysisTargetId
SymbolId
RootId
```

These are domain identities, not Rider PSI pointers or clangd protocol objects.

### Symbol identity and graph identity

A graph node represents one semantic symbol.

Therefore:

> **The node identity is the symbol's `SymbolId`.**

FactoryLens does not introduce a second unrelated `GraphNodeId` for the same semantic entity.

A tree-oriented UI may create path-local presentation rows later, but those rows must resolve back to the same underlying symbol identity. This is what allows the graph layer to recognize shared nodes and cycles consistently.

### Root identity

A root has its own `RootId` because one symbol may become externally reachable through more than one mechanism.

For example, a method could theoretically be both a framework override and a separately registered callback. Those are distinct root/evidence records even though they lead to the same symbol node.

## 1.3 Source locations

Source references use:

```text
SourceUri
SourcePosition
SourceRange
SourceLocation
NavigationTargets
```

### Coordinate convention

`SourcePosition.line` and `SourcePosition.column` are **zero-based**.

`SourceRange.end` is an exclusive/end coordinate in the same style expected by common editor/LSP adapters.

The product model uses a URI string wrapper rather than:

- Rider `VirtualFile`;
- PSI objects;
- clangd/LSP `Location` objects;
- `java.nio.file.Path` as the cross-layer identity.

This keeps the core suitable for normal file URIs today and less hostile to future remote/non-file source schemes.

### Navigation priority

`NavigationTargets.preferred()` uses:

```text
trustworthy call site
  -> definition
     -> declaration
```

A missing call site is valid and expected when clangd does not provide enough URI information to map a returned range honestly.

FactoryLens must not manufacture a source URI merely to make navigation look complete.

## 1.4 Symbols and source realms

`SymbolDescriptor` carries:

- `SymbolId`;
- display name;
- optional qualified name;
- semantic symbol kind;
- current source-realm classification;
- navigation targets.

The initial `SourceRealm` vocabulary is:

```text
TARGET
DEPENDENCY_MOD
SML
FACTORY_GAME
UNREAL_ENGINE
GENERATED
OTHER_EXTERNAL
UNKNOWN
```

FL-B120 owns actually classifying real project sources into these realms.

The supported Satisfactory classifier is workspace-aware and target-aware. It classifies normalized file paths with this precedence:

```text
GENERATED
  -> TARGET
     -> UNREAL_ENGINE
        -> SML
           -> FACTORY_GAME
              -> DEPENDENCY_MOD
                 -> OTHER_EXTERNAL
```

`UNKNOWN` is reserved for source references that cannot be represented as a local file path, such as unsupported/non-file URIs.

The initial realm rules are:

- `GENERATED` — paths under an `Intermediate` directory, plus recognized generated-source filename forms such as `*.generated.h`, `*.gen.cpp`, and `*.ispc.generated.h`; generated classification wins even when the file sits beneath the selected target, SML, FactoryGame, or Unreal Engine;
- `TARGET` — a path beneath any `AnalysisTarget.sourceRoots` file URI;
- `UNREAL_ENGINE` — a path beneath the resolved Unreal Engine installation root;
- `SML` — a path beneath the SML mod root at `<workspace>/Mods/SML`;
- `FACTORY_GAME` — authored Satisfactory project source beneath `<workspace>/Source`, including modules such as `FactoryGame`, `FactoryDedicatedClient`, and `FactoryDedicatedServer`;
- `DEPENDENCY_MOD` — another path beneath `<workspace>/Mods` after the selected target and SML have been excluded;
- `OTHER_EXTERNAL` — any other local file path, including other workspace plugins and sources outside both the workspace and resolved engine root.

The classifier preserves the source URI actually reported by the semantic backend, but ownership checks compare both normalized lexical paths and real-path equivalents when those paths exist. This matters because clangd may canonicalize a file through a symlink or Windows junction before returning its URI. A file therefore remains in the configured target realm when the configured target root and clangd-reported path refer to the same physical tree. Targets may still declare more than one source root when a supported project boundary genuinely spans multiple trees.

The model establishes the vocabulary early so graph, filtering, and UI layers do not invent incompatible boundary enums independently.

## 1.5 Evidence and provenance

Every externally meaningful root or call edge carries one or more `EvidenceRecord` values.

Evidence has two orthogonal dimensions.

### Evidence kind

The initial kinds are:

```text
SEMANTIC_CALL
SEMANTIC_EXTERNAL_OVERRIDE
FOREGROUND_OVERRIDE_VERIFICATION
UNREAL_DYNAMIC_DELEGATE_REGISTRATION
FRAMEWORK_RULE
MANUAL_CONFIRMATION
UNRESOLVED
```

These identify **what produced the evidence**.

### Confidence

The initial confidence levels are:

```text
CONFIRMED
INFERRED
CANDIDATE
UNRESOLVED
```

These identify **how strongly FactoryLens may present the conclusion**.

FactoryLens must not infer confidence only from the evidence kind. A future provider may have both confirmed and candidate results of the same broad mechanism.

Each evidence record can also retain:

- a human-readable summary;
- a relevant source location;
- related symbol IDs.

This is the product-level answer to "why does FactoryLens believe this exists?"

## 1.6 Roots

`RootDescriptor` identifies an externally/framework-reachable entry point.

The initial root kinds are:

```text
EXTERNAL_OVERRIDE
DYNAMIC_DELEGATE
OTHER_FRAMEWORK
CANDIDATE
```

and the initial presentation priority is:

```text
PRIMARY
SECONDARY
```

Priority is not semantic confidence.

A root can be semantically confirmed while still being secondary/low-value for the default UI. That distinction is required by the MVP rule that generic external overrides should not drown out useful lifecycle/framework roots.

A root must always contain evidence.

## 1.7 Graph nodes and call edges

`GraphNode` wraps a `SymbolDescriptor`.

`CallEdge` carries:

- caller `SymbolId`;
- callee `SymbolId`;
- zero or more trustworthy call-site locations;
- one or more evidence records.

Zero call sites does **not** mean "no call occurred." It means FactoryLens has semantic edge evidence but does not have a trustworthy product-level call-site location to expose.

A call edge must always contain evidence.

### Cycles and shared paths

Cycle/shared-node state is intentionally **not stored on the semantic edge itself**.

Whether an edge closes a cycle depends on the current traversal path. FL-B140 will derive presentation/traversal markers from stable symbol identity rather than permanently labelling a semantic edge as "cycle."

## 1.8 Result completeness

Root discovery and call expansion carry:

```text
COMPLETE
PARTIAL
UNKNOWN
```

This is deliberately explicit because BX demonstrated that useful clangd results can coexist with incomplete index coverage.

FactoryLens must not convert "some correct results were returned" into an implicit claim that the result set is complete.

## 1.9 Diagnostics versus operation failure

A successful result may still include `AnalyzerDiagnostic` entries.

Diagnostics represent information such as:

- partial/degraded coverage;
- skipped source;
- unsupported evidence family;
- recoverable backend warning.

Operation-level failure uses `AnalyzerError`.

Initial error codes cover:

```text
INVALID_WORKSPACE
INVALID_TARGET
BACKEND_NOT_FOUND
BACKEND_INCOMPATIBLE
BACKEND_START_FAILED
COMPILE_METADATA_UNAVAILABLE
BACKEND_PROTOCOL_ERROR
QUERY_FAILED
CANCELLED
INTERNAL
```

An error also records whether retry/restart is expected to be meaningful.

This prevents frontends from interpreting "empty roots" as equivalent to "analysis failed."

## 1.10 Analyzer state and progress

The supported analyzer state vocabulary matches the MVP UI contract:

```text
NOT_CONFIGURED
STARTING
INDEXING
READY
QUERYING
DEGRADED
ERROR
STOPPED
```

Progress reports identify a coarse analyzer stage:

```text
CONFIGURATION
STARTING_BACKEND
INDEXING
DISCOVERING_ROOTS
EXPANDING_CALLS
STOPPING
```

Optional completed/total units may be supplied when a backend has honest measurable work units.

FactoryLens must not fabricate percentages for operations where the backend cannot estimate completion.

## 1.11 Supported analyzer session API

The initial IDE-independent protocol is represented by:

```text
FactoryLensAnalyzer
  -> openSession(...)

FactoryLensAnalyzerSession
  -> state()
  -> discoverRoots(...)
  -> expandOutgoingCalls(...)
  -> restart(...)
  -> close()
```

The asynchronous operations are Kotlin `suspend` functions.

The interface deliberately does not depend on IntelliJ coroutine scopes. Rider owns the project/plugin lifecycle scope; the core only exposes cancellable suspend operations.

Progress crosses the boundary through the small `ProgressReporter` interface.

### Session scope

A session binds:

- one `WorkspaceDescriptor`;
- one selected `AnalysisTarget`.

Switching analysis target may therefore create/swap analyzer sessions at the Rider adapter layer while still allowing an underlying semantic backend/index to be reused by later implementation where practical.

The API does not require a new clangd process per target.

## 1.12 Analyzer result envelope

Supported operations return:

```text
AnalyzerResult.Success<T>
AnalyzerResult.Failure
```

Expected analysis degradation belongs in result completeness/diagnostics.

A total operation failure belongs in `Failure(AnalyzerError)`.

This separation gives Rider and the CLI the same semantics for:

- successful complete result;
- successful partial/degraded result;
- operation failure.

## 1.13 Backend isolation

The `semantic-clangd` module will adapt clangd/LSP responses into this model.

Raw protocol types must not become the API of:

- Rider UI;
- graph/root providers;
- CLI output model;
- tests of domain behavior.

If clangd is replaced, upgraded, or supplemented by direct Clang foreground analysis, callers should continue speaking FactoryLens domain types.

This is also the seam that makes a future out-of-process FactoryLens analyzer possible without redefining the product model.

## 1.14 Stability boundary

"Supported" here means these types are the first production contract the upcoming Phase B code should implement against.

It does **not** mean the public API is frozen forever.

Changes are allowed when implementation evidence exposes a poor abstraction, but they should be deliberate architecture changes with call sites/docs/tests updated together—not ad-hoc leakage of backend types around the established model.

## 1.15 Deferred model areas

FL-A040 intentionally does not design detailed product types for:

- SML native hooks;
- UHT/RPC/RepNotify metadata;
- Blueprint graph nodes/edges;
- durable cross-session cache serialization;
- third-party adapter SDKs;
- generic non-Satisfactory framework metadata.

Those should extend the evidence/root model when the corresponding roadmap task becomes real rather than speculating every future protocol now.
