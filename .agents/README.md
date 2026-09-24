# 1. Persisted agent context

This directory contains agent-specific procedure and recovery guidance for FactoryLens. It is not a second technical documentation tree.

## 1.1 What belongs here

Good candidates include checkpoint/recovery conventions, fresh-session reading order, workspace/history safety, and navigation guidance for agents.

Do not put task lists here: the canonical global roadmap is `../TASKS.md`. Also keep technical architecture in `docs/`, experiment evidence in `research/`, generated output out of Git, machine-local configuration in `.env`, and private chat history out of the repository.

## 1.2 Fresh-session reading order

For substantive work:

1. `../AGENTS.md`
2. `WORKFLOW.md`
3. `../README.md`
4. `../TASKS.md`
5. `../docs/README.md`
6. relevant material under `../research/`

The migrated B1-B8 feasibility study lives under `research/external-call-map-feasibility/`. Treat it as historical evidence for what was proven, not as the permanent supported product layout.
