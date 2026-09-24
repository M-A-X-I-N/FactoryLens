# FactoryLens

**Satisfactory-first developer tooling for Rider.**

FactoryLens exists to make Satisfactory mod code easier to understand, navigate, and develop where Unreal Engine, SML, reflection, delegates, hooks, and framework lifecycle make ordinary caller navigation incomplete.

The project is intentionally Satisfactory-first. Reusable C++/Unreal pieces are welcome where they fall out naturally, but genericity is not a goal worth large amounts of extra architecture by itself.

## Current status

The initial FactoryLens repository bootstrap is complete. The project is moving from completed feasibility research into product architecture/tooling design.

The completed B1-B8 External Call Map study classified the approach as **viable with known blind spots** without requiring a bespoke C++ parser or deep semantic coupling to Rider/ReSharper internals.

The first supported-product contract is defined in [`docs/MVP_CONTRACT.md`](docs/MVP_CONTRACT.md), and the Kotlin/JVM production skeleton now builds and packages in CI. No working analyzer behavior or polished Rider UI exists yet.

## Initial product direction

The first flagship capability is an external/framework-aware call map:

```text
external/framework root
  -> project-local semantic call edges
     -> lazy expandable graph/tree
```

The same foundation may later support entry-point discovery, SML/Unreal-aware navigation, hook/delegate/RPC/replication provenance, UHT/reflection-aware navigation, and project/environment assistance.

## Architecture direction

```text
UnrealBuildTool compile truth
  -> Clang/clangd semantic service
     -> Satisfactory/Unreal/SML entry-point adapters
        -> bounded cached graph/model
           -> Rider frontend
```

Rider is the intended first-class user experience, but the semantic/graph core should remain usable outside Rider when doing so is cheap.

See [`TASKS.md`](TASKS.md) for the global roadmap and [`docs/README.md`](docs/README.md) for durable project documentation.

## Proven so far

The migrated feasibility work demonstrated that UBT can provide real compile metadata, clangd can resolve representative RSS2/Wiremod/Unreal symbols, clangd 20.1.8 can return useful cross-file outgoing calls, bounded multi-root graphs are tractable with lazy/cached presentation, external virtual overrides can be discovered semantically, and Unreal `AddDynamic` callback roots can be recovered through a small registration rule plus semantic target resolution.

Known blind spots remain around incomplete background-index coverage, UHT/reflection dispatch, Blueprint implementations, unvalidated SML native-hook specimens, and full Wiremod-scale traversal.

The complete evidence trail lives under [`research/external-call-map-feasibility/`](research/external-call-map-feasibility/).

## Repository map

```text
FactoryLens/
├─ AGENTS.md
├─ TASKS.md
├─ src/                    Rider plugin frontend shell
├─ core/                   IDE-independent product core
├─ semantic-clangd/        clangd semantic adapter
├─ cli/                    headless developer harness
├─ docs/
├─ research/
├─ scripts/
├─ tests/
└─ work/                   ignored generated analysis/scratch state
```

## Development principle

> **Design for extraction; do not prematurely perform the extraction.**

A Satisfactory-specific implementation is fine when that is the cleanest solution. Shared Unreal or generic C++ layers should be extracted when they genuinely reduce duplication or broaden support cheaply.
