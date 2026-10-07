# Repository-local policy

Read this file for substantial work in this repository.

This file is repository-owned normative policy. It extends the generic baseline instructions under `.agents/baseline/`. Where this file explicitly conflicts with a baseline instruction, this local rule wins. Silence leaves the baseline rule in force.

Replace the placeholders below with the repository's actual durable policy rather than editing baseline-owned files.

## Repository purpose

TODO: describe what this repository owns and what successful work here means.

## Local source-of-truth map

The baseline reserves the exact lowercase top-level `meta/` path for project-control and collaboration state. Do not rename or recase it to match repository-specific source naming conventions.

The seed layout starts with:

- `meta/tasks.md` for executable-work scheduling metadata, Dispatch, and Active claims;
- `meta/tasks/` for task specifications and tracked temporary task workspaces;
- `meta/reminders.md` for deliberately non-executable lightweight future intent;
- `meta/initiatives/` for structured non-executable unfinished work/debt;
- `.agents/local/` for repository-specific normative agent policy;
- `.agents/memory/` for durable non-normative agent knowledge whose rediscovery would be wasteful.

TODO: add the repository's actual source/configuration, documentation, test, generated-artifact, and machine-local state authorities.

Do not create competing task ledgers or duplicate authoritative policy.

## Repository-specific engineering policy

TODO: record only durable repository-specific rules that materially affect implementation or review.

Add lower-frequency subject-specific instruction files under `.agents/local/` when that improves routing. Register each file's read trigger in `.agents/local/README.md`.
