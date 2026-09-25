# 1. Agent workflow and recovery conventions

## 1.1 Checkpoint discipline

For repository-changing work:

1. inspect current `main`;
2. keep each checkpoint narrow enough to explain and revert independently;
3. run the relevant validation;
4. commit/push meaningful completed work promptly;
5. inspect CI when the changed surface is covered.

`../TASKS.md` is the canonical global roadmap/task ledger. Keep durable global status there. Chat may decompose the current global task into temporary minitasks, but do not create competing task ledgers elsewhere in the repository.

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

## 1.7 Human-run validation scripts

When a human must run repository validation manually, prefer a committed script under `scripts/` instead of pasting a multi-command procedure into chat.

- Task-specific validation scripts must include the task ID in the filename, for example `FL-B100-validate-compile-metadata.ps1`.
- Generic validation/tooling scripts that are not tied to one roadmap task do not need a task ID.
- A genuinely single-line command may be given directly without creating a script.
- Prefer CI or other automated validation over asking the human to run scripts; manual validation should be a close-to-last-resort path for environment-specific evidence.
- Manual validation scripts are exempt from the general preference for cross-platform/Python tooling when a platform-specific language is the practical way to exercise the target environment (for example, PowerShell for a Windows-only validation).
- Keep the script narrow, reproducible, and suitable for rerunning after a fresh checkout when practical.

## 1.8 Commit provenance

Use the subject convention from `../AGENTS.md`. Agents must not self-select `CBA`.

For wholly authored commits from this ChatGPT lineage, the trailer is exactly:

```text
Agent-authored-by: Gippity, Cartographer of Questionable Call Stacks
  (OpenAI ChatGPT, GPT-5.6 Sol)
```
