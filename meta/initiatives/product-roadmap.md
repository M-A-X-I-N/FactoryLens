# FactoryLens product roadmap

Status: **active context, non-executable**

## Goal

FactoryLens must first become a useful working Satisfactory/Rider product before broad hardening, packaging, deep Blueprint support, or generic extraction becomes priority work.

This initiative records roadmap/gate context only. It is not Dispatch and does not duplicate mutable lifecycle state from `meta/tasks.md`.

## Working-product gate

Before release polish, licensing, broad genericity, or deep Blueprint support becomes a priority, FactoryLens must demonstrate this end-to-end workflow on the configured Satisfactory/SML environment:

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

The gate passes only when this uses supported FactoryLens code rather than manually run B1–B7 research scripts or pre-generated JSON.

At minimum, validate against:

- RSS2, the proven feasibility specimen;
- Wiremod/Circuitry, where early semantic access was proven but equivalent deep supported traversal still needs validation;
- the current Satisfactory / Unreal Engine / SML development environment documented in `docs/ENVIRONMENT.md`.

FactoryLens remains read-only toward analyzed projects by default.

## Roadmap shape

### Supported analyzer foundation

The supported headless analyzer must provide reliable workspace/compile metadata, persistent semantics, source-realm boundaries, outgoing calls, graph traversal, useful framework roots, development harnesses, and controlled tests before it is treated as the stable foundation for the Rider workflow.

Related tasks: `FL-B150` through `FL-B190`.

### Rider MVP

Once the supported analyzer gate is satisfied, Rider work connects project lifecycle to the analyzer and provides root exploration, lazy call-tree navigation, source navigation, provenance/filtering, and a complete end-to-end product-gate exercise.

Related tasks: `FL-C200` through `FL-C260`.

### Post-gate hardening and enrichment

Durable caching, SML native hooks, UHT/reflection enrichment, Blueprint investigation, generic extraction, packaging/compatibility, and release polish are intentionally post-gate work.

Related tasks: `FL-D300` through `FL-D360`.

## Evidence boundary

The migrated B1–B8 feasibility work proves that the semantic direction is viable with known blind spots. It does not itself satisfy the working-product gate.

Supported-product task acceptance must use supported FactoryLens code and record limitations honestly rather than treating partial semantic evidence as complete coverage.

## Explicit non-prerequisites for the working-product gate

None of the following should become an accidental prerequisite for `FL-C260` unless the human deliberately changes scope:

- perfect incoming-call / reverse-reference completeness;
- deep Blueprint graph reconstruction;
- universal Unreal-project support;
- universal generic-C++ support;
- support for IDEs other than Rider;
- a public third-party adapter SDK;
- polished graph visualization beyond what the first usable navigation workflow needs.

These are product-boundary constraints, not hidden tasks.

## Promotion / closure criteria

This initiative remains useful until the working-product gate is passed and the post-gate roadmap is deliberately reconsidered.

Closure does not itself authorize any related task; executable authority remains in `meta/tasks.md` Dispatch.
