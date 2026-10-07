# Baseline knowledge and context placement

Read this file when deciding where learned information, instructions, research, or durable context belongs.

## Three agent-facing classes

### Baseline instructions

Generic normative behavior shared across repositories.

They live under `.agents/baseline/`.

Repository-local policy must not customize baseline behavior by editing baseline files.

### Local instructions

Repository-specific normative behavior under `.agents/local/`.

Applicable local instructions may extend, tighten, or explicitly override baseline instructions.

When overriding, identify the baseline behavior being changed clearly enough that an agent can understand the conflict without diff archaeology.

Silence in local policy leaves baseline policy active.

The repository-specific instruction router is `.agents/local/README.md`. Adding or removing a local instruction file should update that local router; it must not require editing baseline-owned routing merely for discoverability.

### Memory

Repository-specific non-normative knowledge under `.agents/memory/`.

Memory may contain:

- architecture implementation notes;
- decisions/rationale;
- investigations;
- platform/application/tool quirks;
- failed approaches;
- diagnostics;
- historical migration evidence;
- expensive-to-rediscover facts.

Memory is **not policy** merely because an agent wrote it.

Historical memory never overrides current baseline/local instructions.

## What to preserve

If meaningful investigation/tool effort discovered something likely to matter again, preserve it at the narrowest durable useful scope.

Record:

- what was learned;
- whether it is verified, inferred, or uncertain;
- enough context to reuse it;
- useful reproduction/validation commands;
- failed approaches when repeating them would waste time.

Prefer updating an existing note over creating a competing note.

## Task workspaces versus memory

Tracked task/block/workstream workspaces hold temporary/intermediate knowledge needed to survive context/session boundaries while executable work is active.

They are not mandatory startup context and are less permanent than agent memory.

Use the narrowest practical workspace scope. Before archival/retirement, promote durable conclusions that would be expensive to rediscover.

Do not copy entire task workspaces into permanent memory merely because the task completed.

## Human-facing documentation

Durable project architecture/policy relevant to humans must not exist only in agent memory.

Promote such conclusions to the appropriate human-facing documentation while retaining agent-specific scar tissue only when it remains useful.

## Read discipline

Do not preload memory simply because it exists.

Dated investigations, migration records, and historical decision snapshots are on-demand references.

Prefer current source/tests and human-facing architecture for implemented behavior; use memory when rationale, diagnostic history, platform scar tissue, or expensive prior research is relevant.

## Exclusions

Never store:

- secrets;
- tokens/passwords/private keys;
- unrelated personal/chat history;
- disposable generated output;
- mutable machine-local state that belongs outside tracked repository truth;
- duplicate competing authoritative documentation.
