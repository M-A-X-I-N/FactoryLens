# 1. FactoryLens agent guide

FactoryLens is a **Satisfactory-first developer-tooling project** with Rider as the planned primary frontend. Reuse and generalization are welcome where they are cheap, but they must not drive unnecessary abstraction or weaken the Satisfactory use case.

## 1.1 Before substantive work

Read, in order:

1. this file;
2. `.agents/README.md` and `.agents/WORKFLOW.md`;
3. the root `README.md`;
4. `TASKS.md` for global priorities/status;
5. `docs/README.md`;
6. only the research material relevant to the task.

Treat current repository state as authoritative over remembered chat context.

## 1.2 Repository ownership

- `docs/` owns durable architecture, setup, policy, and supported behavior.
- `research/` owns experiments, evidence, feasibility studies, and historical probes.
- generated compile databases, clangd indexes, logs, graph output, caches, and scratch state belong under ignored `work/`;
- machine-local paths belong in ignored `.env`;
- `TASKS.md` is the canonical repository-level roadmap/task ledger. Do not create competing TODO/task ledgers elsewhere; use chat for temporary substeps beneath the active global task.

Supported product code should eventually live outside `research/`; promotion from a research probe is an explicit decision.

## 1.3 Architecture discipline

Prefer existing UnrealBuildTool, Clang/clangd, Unreal, and SML machinery over bespoke parsing.

Keep responsibilities separated unless evidence justifies merging them:

```text
UBT compile truth
  -> semantic backend
     -> framework-entry adapters
        -> graph/model layer
           -> Rider frontend
```

Rider should initially be a frontend/host rather than the sole semantic source of truth.

## 1.4 Safety and recoverability

Preserve Git history and recoverability. Prefer small coherent commits and additive corrections over rewriting established history.

Analysis of external Satisfactory/SML/UE projects is read-only by default. Do not modify target projects or dependency source merely to make analysis easier unless explicitly authorized.

## 1.5 Commit messages

Use:

```text
[Kind][Scope] Imperative summary
```

Approved kinds are `Feature`, `Fix`, `Research`, `Documentation`, `Test`, `CI`, `Build`, `Refactor`, `Chore`, and human-selected-only `CBA`.

For wholly authored commits from this ChatGPT lineage, use exactly:

```text
Agent-authored-by: Gippity, Cartographer of Questionable Call Stacks
  (OpenAI ChatGPT, GPT-5.6 Sol)
```
