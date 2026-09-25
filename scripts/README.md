# 1. Repository tooling

This directory is for supported FactoryLens repository/development tooling.

It is distinct from `research/`, which contains historical feasibility probes and experiment-specific analysis scripts.

Current tools:

- `check_repository_consistency.py` — validates required repository structure, generated-state boundaries, task-ledger policy, and tracked Python syntax.
- `FL-B100-validate-compile-metadata.ps1` — runs the real Windows FL-B100 compile-metadata/mutation-audit validation with JDK 25 selected for the Gradle process; accepts `-SmlRoot`/`-EngineRoot`, otherwise uses environment/`.env` and prompts for the SML root when still unconfigured.

Task-specific human-run validation scripts include their roadmap task ID in the filename. Generic tools need no task prefix. Prefer automated/CI validation; manual scripts are a close-to-last-resort path and may use a platform-specific language when that is the practical way to validate the target environment.
