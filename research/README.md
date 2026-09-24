# 1. Research

This directory contains FactoryLens feasibility studies, experiments, source-backed investigations, and historical probes.

Research is evidence, not automatically supported product behavior.

## 1.1 Completed studies

### External Call Map feasibility

[`external-call-map-feasibility/`](external-call-map-feasibility/) contains the migrated B1-B8 experiment that established the initial FactoryLens direction.

The final classification was:

> **viable with known blind spots**

Key proven areas include UBT compile metadata, clangd/Clang semantic resolution, cross-file outgoing calls, bounded graph traversal, external-override roots, and Unreal dynamic-delegate callback roots.

The study originated in `M-A-X-I-N/satisfactory-wiremod-rss-integration` and was migrated after B8 completed.

## 1.2 Research rules

- Preserve measured limitations and failed experiments.
- Keep generated indexes/logs/output under ignored `work/`.
- Prefer small semantic/framework-specific experiments over broad custom parsing.
- Promote durable product conclusions into `../docs/`.
- Promote research code into supported code only through an explicit productization task.
