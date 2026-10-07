# FactoryLens repository-local policy

This file is repository-owned normative policy for FactoryLens. It extends the generic baseline without changing baseline-managed files.

## Repository purpose

FactoryLens is Satisfactory-first developer tooling, with Rider as the intended primary frontend.

Reusable C++/Unreal pieces are welcome where they fall out naturally, but genericity must not drive unnecessary abstraction or weaken the Satisfactory use case.

## Local source-of-truth map

Use these repository-owned surfaces for FactoryLens-specific truth:

- current source and configuration own implemented behavior;
- root `README.md` owns product identity and current high-level status;
- `docs/` owns durable product architecture, setup, contracts, policy, and supported behavior;
- `research/` owns experiments, feasibility evidence, historical probes, and experiment-specific limitations; the migrated B1–B8 External Call Map study under `research/external-call-map-feasibility/` is historical feasibility evidence, not the supported product layout;
- ignored `work/` owns generated compile databases, clangd indexes, logs, graph output, caches, downloaded analysis/tooling state, and scratch artifacts;
- ignored `.env` owns machine-local paths and configuration;
- `meta/tasks.md` owns executable-work scheduling, Dispatch, and Active claims;
- root `TASKS.md` is retained only as non-authoritative legacy roadmap input pending a separate task-system migration; do not execute, schedule, or update work from it;
- `meta/tasks/` owns task specifications and tracked temporary task workspaces;
- `meta/reminders.md` and `meta/initiatives/` own non-executable future intent at their respective levels;
- `.agents/local/` owns FactoryLens-specific normative agent policy;
- `.agents/memory/` owns durable non-normative agent knowledge whose rediscovery would be wasteful.

Supported product code should live outside `research/`. Promotion of a research probe into supported product code is an explicit decision rather than an incidental file move.

Do not create competing authoritative task ledgers or duplicate durable policy across these surfaces.

## Architecture discipline

Prefer existing UnrealBuildTool, Clang/clangd, Unreal, and SML machinery over bespoke parsing when they can supply the needed semantics.

Keep these responsibilities separated unless implementation evidence justifies changing the boundary:

```text
UBT compile truth
  -> semantic backend
     -> Satisfactory/Unreal/SML framework-entry adapters
        -> graph/model layer
           -> Rider frontend
```

Rider is a frontend/host rather than the sole semantic source of truth. Do not make Rider UI code responsible for compiler semantics, and do not make framework rules parse arbitrary C++. Generalize only where reuse is cheap or the implementation demonstrates a natural reusable seam.

Supported FactoryLens code should expose explicit interfaces, tests, error handling, and compatibility behavior appropriate to its supported role rather than retaining research-probe assumptions.

## External-project safety

FactoryLens analyzes external Satisfactory/SML/Unreal projects read-only by default.

Do not modify target projects, SML Starter Projects, Unreal Engine installations, dependency source, generated UHT output, or IDE configuration merely to make analysis easier unless the human explicitly authorizes that mutation.
