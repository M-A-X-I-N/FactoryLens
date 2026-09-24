# 1. Agent workflow and recovery conventions

## 1.1 Checkpoint discipline

For repository-changing work:

1. inspect current `main`;
2. keep each checkpoint narrow enough to explain and revert independently;
3. run the relevant validation;
4. commit/push meaningful completed work promptly;
5. inspect CI when the changed surface is covered.

Task sequencing belongs in chat, not in a repository TODO ledger.

## 1.2 Interrupted-session recovery

Do not assume the last narrated action reached `main`. Inspect branch head/history first, distinguish committed work from orphaned objects or reasoning-only work, and validate recovered state before continuing.

Never repair an interruption by destructive history rewriting unless the human explicitly authorizes it.

## 1.3 Research versus product code

Research probes may be narrow and environment-specific, and may write ignored artifacts under `work/`.

Supported FactoryLens code should eventually have explicit interfaces, tests, error handling, and compatibility policy. Moving a probe out of `research/` is a deliberate promotion decision.

## 1.4 Architecture discipline

Keep these responsibilities separate:

```text
UBT compile metadata
  -> Clang/clangd semantic queries
     -> Satisfactory/Unreal/SML entry-point adapters
        -> graph/cache/traversal model
           -> Rider frontend
```

Do not make Rider UI code responsible for compiler semantics. Do not make framework rules parse arbitrary C++. Generalize only where the seam is already natural or reuse is cheap.

## 1.5 External project safety

FactoryLens analyzes other projects. Analysis is read-only by default.

Do not modify SML Starter Projects, target mods, Unreal Engine installations, dependency source, generated UHT output, or IDE configuration merely to obtain evidence unless explicitly authorized.

## 1.6 Documentation ownership

Use:

- root `README.md` for product identity and current high-level status;
- `docs/` for durable architecture, setup, contracts, and supported behavior;
- `research/` for experiments, evidence, feasibility studies, and historical probes;
- root `AGENTS.md` for concise operating rules;
- `.agents/` for agent procedure/recovery context.

If a conversation changes a durable project fact, update the owning document.

## 1.7 Commit provenance

Use the subject convention from `../AGENTS.md`. Agents must not self-select `CBA`.

For wholly authored commits from this ChatGPT lineage, the trailer is exactly:

```text
Agent-authored-by: Gippity, Cartographer of Questionable Call Stacks
  (OpenAI ChatGPT, GPT-5.6 Sol)
```
