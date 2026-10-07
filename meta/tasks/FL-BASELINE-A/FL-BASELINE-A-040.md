# FL-BASELINE-A-040 — Reconcile legacy workflow

## Temporary migration authority rule

Until this instruction-migration block is fully complete or the human explicitly says otherwise:

- only instructions supplied by the imported baseline template are authoritative;
- legacy/unmigrated instruction files are evidence to reconcile, not instructions to follow;
- `.agents/local/**` may be edited as migration output but must not be followed as instructions yet;
- root `TASKS.md` is outside this block and must remain untouched.

## Description

Reconcile the legacy FactoryLens workflow file at `.agents/WORKFLOW.md` into the imported baseline ownership model.

## Requirements

- Compare each legacy workflow rule against canonical baseline workflow, Git, provenance, and knowledge-placement policy as applicable.
- Baseline behavior wins whenever the legacy rule is generic, redundant, or conflicting unless the human has explicitly established a FactoryLens-specific exception.
- Preserve still-useful FactoryLens-specific workflow/engineering rules under `.agents/local/`.
- Prefer extending existing local files over creating unnecessary one-to-one mirrors of baseline filenames.
- When uncertain whether a rule is redundant, preserve it locally for later review.
- Remove the legacy `.agents/WORKFLOW.md` once every meaningful rule has been accounted for.

## Constraints / non-goals

- Do not edit or migrate root `TASKS.md`.
- Do not modify canonical baseline-managed files.
- Do not perform unrelated product/docs cleanup.
- Do not follow newly written `.agents/local/**` instructions during this task.

## Acceptance criteria

- Every meaningful rule from legacy `.agents/WORKFLOW.md` has been classified as baseline-covered, intentionally superseded, or preserved locally.
- Useful FactoryLens-specific workflow policy is discoverable through repository-owned local routing.
- Legacy `.agents/WORKFLOW.md` no longer exists.
- Canonical baseline-managed files remain unchanged.

## Validation

- Re-read the legacy workflow against the resulting baseline/local split before removal.
- Verify baseline-managed blob identity against `M-A-X-I-N/baseline`.
- Verify local router links for any local files added or changed.
