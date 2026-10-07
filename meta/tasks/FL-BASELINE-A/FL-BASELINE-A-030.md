# FL-BASELINE-A-030 — Reconcile legacy agent router

## Temporary migration authority rule

Until this instruction-migration block is fully complete or the human explicitly says otherwise:

- only instructions supplied by the imported baseline template are authoritative;
- legacy/unmigrated instruction files are evidence to reconcile, not instructions to follow;
- `.agents/local/**` may be edited as migration output but must not be followed as instructions yet;
- root `TASKS.md` is outside this block and must remain untouched.

## Description

Reconcile the preserved legacy agent router at `.agents/README.md.orig` into the imported baseline ownership model.

## Requirements

- Compare `.agents/README.md.orig` semantically against canonical `.agents/README.md` and the baseline knowledge/routing model.
- Baseline routing and knowledge-placement semantics win for generic behavior.
- Preserve only still-useful FactoryLens-specific normative routing or ownership information under `.agents/local/`.
- If a useful statement is already represented by local policy produced by an earlier task, do not duplicate it.
- When uncertain whether a legacy statement is redundant, preserve it locally for later review.
- Remove `.agents/README.md.orig` once every meaningful legacy statement has been accounted for.

## Constraints / non-goals

- Do not reconcile the legacy `.agents/WORKFLOW.md`; that is `FL-BASELINE-A-040`.
- Do not edit or migrate root `TASKS.md`.
- Do not edit canonical baseline-managed files.
- Do not follow newly written `.agents/local/**` instructions during this task.

## Acceptance criteria

- Every meaningful statement in `.agents/README.md.orig` has been classified as baseline-covered, intentionally superseded, or preserved locally.
- Useful FactoryLens-specific routing/ownership residue is represented once in repository-owned local policy.
- `.agents/README.md.orig` no longer exists.
- Canonical baseline-managed files remain unchanged.

## Validation

- Re-read the legacy source against the resulting baseline/local split before removal.
- Verify baseline-managed blob identity against `M-A-X-I-N/baseline`.
- Verify local router links for any local files added or changed.
