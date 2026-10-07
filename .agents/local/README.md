# Local instruction router

This file is repository-owned. It indexes repository-specific normative instructions, executable-work locations, and local memory organization.

Adding a repository-specific instruction file does **not** require a matching baseline file. Register the new file and its read trigger here; do not modify the baseline router merely to make the local file discoverable.

## Always for substantial repository work

Read [`REPOSITORY.md`](REPOSITORY.md).

It owns the repository-specific identity, source-of-truth map, engineering conventions, and local task/reminder/initiative locations.

## Subject-triggered local policy

Add repository-specific instruction files and their read triggers here as they become necessary.

Do not create local instruction files merely to mirror baseline filenames.

## Reserved meta namespace

The baseline reserves the exact lowercase top-level path [`../../meta/`](../../meta/) for repository project-control and collaboration state.

Do not rename or recase `meta/` merely to match a repository's source-code naming convention. Local casing/style rules do not apply to this reserved integration path.

The seed layout uses:

- ledger / Dispatch / Active claims: [`../../meta/tasks.md`](../../meta/tasks.md);
- active task specifications/workspaces: [`../../meta/tasks/`](../../meta/tasks/);
- reminders: [`../../meta/reminders.md`](../../meta/reminders.md);
- initiatives: [`../../meta/initiatives/`](../../meta/initiatives/).

Repositories may add repository-control material beneath `meta/` when useful. Do not repurpose the reserved top-level directory for unrelated application/source content.

## Local memory organization

Repository-specific non-normative knowledge belongs under `../memory/`.

Do not preload memory and do not create empty taxonomy for appearance. Add categories only when useful knowledge actually needs them.
