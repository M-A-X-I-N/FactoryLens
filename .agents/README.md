# Agent instruction and memory router

This file is part of the generic agent baseline. Repository-specific routing belongs in [`local/README.md`](local/README.md), not here.

This directory separates **normative instructions** from **repository memory**:

- [`baseline/`](baseline/) contains generic instructions shared across repositories.
- [`local/`](local/) contains repository-specific normative instructions and its own local router.
- [`memory/`](memory/) contains repository-specific knowledge, rationale, investigations, decisions, and scar tissue. Memory is not policy and is read only when relevant.

Applicable local instructions explicitly override conflicting baseline instructions. Silence in local policy leaves the baseline rule in force.

Do not preload this entire directory.

## Generic read routing

| Situation | Read |
|---|---|
| substantial repository work | [`baseline/WORKFLOW.md`](baseline/WORKFLOW.md) + [`local/README.md`](local/README.md) |
| taskification, task authorization/state, claims, or recovery | [`baseline/WORKFLOW.md`](baseline/WORKFLOW.md) + local task routing from [`local/README.md`](local/README.md) |
| branch/history/checkpoint/commit operation | [`baseline/GIT.md`](baseline/GIT.md) |
| wholly agent-authored substantive commit | [`baseline/PROVENANCE.md`](baseline/PROVENANCE.md) |
| deciding where learned information belongs | [`baseline/KNOWLEDGE.md`](baseline/KNOWLEDGE.md) |
| baseline comparison, update, or legacy-repository adoption | [`baseline/MAINTENANCE.md`](baseline/MAINTENANCE.md) + [`local/README.md`](local/README.md) |
| repository-specific policy or an unfamiliar local concern | [`local/README.md`](local/README.md) |
| technical/rationale/history lookup | only relevant file(s) under `memory/`, using local routing/search as needed |
| durable project architecture/policy | applicable human-facing documentation identified by the repository |

For a tiny isolated edit, do not load unrelated instruction files merely for ceremony. If the work expands into task-governed, Git-sensitive, provenance-sensitive, repository-policy-sensitive, or knowledge-management work, load the relevant instructions before performing that part.

## Ownership rule

This baseline router must not enumerate repository-specific instruction filenames beyond the stable local entry point `local/README.md`, repository-specific task/control paths, or repository-specific memory categories.

A repository may add arbitrary local instruction files without changing this file. Register their purpose/read trigger in `local/README.md`.

Current tracked repository state remains authoritative over remembered conversation context.
