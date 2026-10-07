# FL-BASELINE-A-020 — Reconcile legacy root agent guide

## Temporary migration authority rule

Until this instruction-migration block is fully complete or the human explicitly says otherwise:

- only instructions supplied by the imported baseline template are authoritative;
- legacy/unmigrated instruction files are evidence to reconcile, not instructions to follow;
- `.agents/local/**` may be edited as migration output but must not be followed as instructions yet;
- root `TASKS.md` is outside this block and must remain untouched.

## Description

Reconcile the preserved pre-baseline root agent guide at `AGENTS.md.orig` into the imported baseline ownership model.

## Requirements

- Compare `AGENTS.md.orig` semantically against the canonical root `AGENTS.md` and applicable files under `.agents/baseline/`.
- Baseline behavior wins whenever the legacy rule is generic, redundant, or conflicting unless the human has explicitly established a FactoryLens-specific exception.
- Preserve still-useful FactoryLens-specific normative behavior under `.agents/local/`, using the narrowest sensible repository-owned file.
- When uncertain whether a legacy rule is genuinely redundant, preserve it locally for later review rather than silently losing it.
- Keep baseline-managed files canonical; do not edit `.agents/README.md` or `.agents/baseline/*` to retain FactoryLens-specific behavior.
- Remove `AGENTS.md.orig` once every meaningful legacy rule has been accounted for.

## Constraints / non-goals

- Do not reconcile `.agents/README.md.orig` or `.agents/WORKFLOW.md`; they have separate tasks.
- Do not edit or migrate root `TASKS.md`.
- Do not perform general documentation/product cleanup.
- Do not follow newly written `.agents/local/**` instructions during this task.

## Acceptance criteria

- Every meaningful statement in `AGENTS.md.orig` has been classified as baseline-covered, intentionally superseded, or preserved locally.
- Repository-specific residue that remains useful is discoverable under `.agents/local/`.
- `AGENTS.md.orig` no longer exists.
- Canonical baseline-managed files remain unchanged.

## Validation

- Re-read the legacy source against the resulting baseline/local split before removal.
- Verify baseline-managed blob identity against `M-A-X-I-N/baseline`.
- Verify links/routing for any local file added or changed.
