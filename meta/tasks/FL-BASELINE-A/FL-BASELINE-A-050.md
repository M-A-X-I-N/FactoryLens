# FL-BASELINE-A-050 — Finalize instruction migration

## Temporary migration authority rule

Until this instruction-migration block is fully complete or the human explicitly says otherwise:

- only instructions supplied by the imported baseline template are authoritative;
- legacy/unmigrated instruction files are evidence to reconcile, not instructions to follow;
- `.agents/local/**` may be edited as migration output but must not be followed as instructions yet;
- root `TASKS.md` is outside this block and must remain untouched.

## Description

Perform the final integration and conformance pass for the FactoryLens instruction migration after all three legacy instruction sources have been reconciled.

Completion of this task completes the temporary instruction-migration authority period unless the human explicitly extends it.

## Requirements

- Review the resulting root `AGENTS.md`, canonical generic router/baseline policy, and repository-owned `.agents/local/` output as one navigation system.
- Ensure every local instruction file is discoverable through `.agents/local/README.md`.
- Replace any remaining baseline seed placeholders in local policy that are necessary for coherent FactoryLens routing/ownership.
- Ensure the repository-specific source-of-truth map correctly identifies `meta/tasks.md` as executable-work authority.
- Preserve root `TASKS.md` unchanged as legacy task-roadmap input for a later dedicated migration; do not convert it in this task.
- Ensure no legacy instruction migration inputs remain: `AGENTS.md.orig`, `.agents/README.md.orig`, and legacy `.agents/WORKFLOW.md` must all be absent.
- Verify canonical baseline-managed files remain byte-identical to `M-A-X-I-N/baseline`.
- Check for stale instruction links/references that still route agents through removed legacy instruction paths.

## Constraints / non-goals

- Do not migrate, reinterpret, or edit root `TASKS.md`.
- Do not undertake unrelated repository standardization, product development, or documentation cleanup.
- Until this task is actually complete, continue ignoring `.agents/local/**` as authoritative instructions.

## Acceptance criteria

- Root → canonical generic router → repository-local router → task/documentation routes resolve coherently.
- All baseline-managed generic files match the canonical baseline.
- Local policy contains no unresolved baseline seed placeholders required for normal substantial work.
- No removed legacy instruction source is still referenced as authoritative.
- Root `TASKS.md` is byte-identical to its pre-block state.
- The instruction migration is complete and a fresh agent can operate from the baseline/local architecture without reading legacy instruction files.

## Validation

- Compare baseline-managed blob identities with `M-A-X-I-N/baseline`.
- Search tracked instruction/control files for stale references to `AGENTS.md.orig`, `.agents/README.md.orig`, legacy `.agents/WORKFLOW.md`, and authoritative use of root `TASKS.md`.
- Verify local router links.
- Verify root `TASKS.md` has not changed since the bootstrap checkpoint.
- Run only repository checks materially relevant to changed instruction/control-plane files.
