# 1. FactoryLens documentation

This directory contains durable product, architecture, environment, and supported-behavior documentation. Global task status lives in [`../TASKS.md`](../TASKS.md).

Experimental evidence belongs under [`../research/`](../research/), not here.

## 1.1 Current documentation

- [`MVP_CONTRACT.md`](MVP_CONTRACT.md) — exact first working-product workflow, required evidence/behavior, validation specimens, and explicit non-goals.
- [`IMPLEMENTATION_BOUNDARY.md`](IMPLEMENTATION_BOUNDARY.md) — initial Rider/Kotlin/analyzer/process boundary and why no ReSharper backend or FactoryLens daemon is needed for the MVP.
- [`ANALYZER_MODEL.md`](ANALYZER_MODEL.md) — supported IDE-independent analyzer protocol/domain model for symbols, roots, calls, evidence, state, errors, progress, and source navigation.
- [`PRODUCT_SCOPE.md`](PRODUCT_SCOPE.md) — what FactoryLens is for and how far generalization should go.
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — current architectural direction derived from the completed feasibility work.
- [`ENVIRONMENT.md`](ENVIRONMENT.md) — current development-environment assumptions and machine-local configuration.
- [`BUILDING.md`](BUILDING.md) — pinned JVM/Rider/Gradle toolchain, production module layout, canonical build commands, and CI contract.

## 1.2 Evidence versus product guarantees

The B1-B8 feasibility study under [`../research/external-call-map-feasibility/`](../research/external-call-map-feasibility/) proves that the semantic direction is worth building on.

It does not mean every research probe is supported product code, every Unreal dispatch mechanism is already handled, or every Satisfactory mod will produce a complete call graph.

When a research result becomes supported FactoryLens behavior, document that supported contract here.

## 1.3 Source-of-truth rule

When documentation conflicts:

1. current source/configuration owns what the repository actually does;
2. this `docs/` tree owns durable product architecture/setup/policy;
3. `research/` owns historical evidence and experiment-specific limitations;
4. `AGENTS.md` and `.agents/` own agent procedure.
