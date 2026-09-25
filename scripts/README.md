# 1. Repository tooling

This directory is for supported FactoryLens repository/development tooling.

It is distinct from `research/`, which contains historical feasibility probes and experiment-specific analysis scripts.

Current tools:

- `check_repository_consistency.py` — validates required repository structure, generated-state boundaries, task-ledger policy, and tracked Python syntax.
- `FL-B100-validate-compile-metadata.ps1` — runs the real Windows FL-B100 compile-metadata/mutation-audit validation with JDK 25 selected for the Gradle process; accepts `-SmlRoot`/`-EngineRoot`, otherwise uses environment/`.env` and prompts for the SML root when still unconfigured.
- `FL-B110-validate-semantic-backend.ps1` — generates the B3-proven UBT Clang compile view and then starts/initializes/shuts down the supported persistent clangd backend; it accepts `-Clangd`, reuses `FACTORYLENS_CLANGD`/`.env`, or searches PATH, WinGet links, common side-by-side LLVM installs, and Visual Studio LLVM locations for clangd major 20. If none exists, it downloads the official portable clangd 20.1.8 Windows release into ignored `work/factorylens/toolchains/` and verifies its pinned SHA-256 before extraction.
- `FL-B130-validate-outgoing-calls.ps1` — reuses the complete FL-B110 Windows environment/backend validation, then runs the supported one-level clangd outgoing-call adapter against the proven RSS2 `IsSignDataSafe` specimen and requires the known project-local `IsStructurallySafeRemoteImageUrl` edge.

Task-specific human-run validation scripts include their roadmap task ID in the filename. Generic tools need no task prefix. Prefer automated/CI validation; manual scripts are a close-to-last-resort path and may use a platform-specific language when that is the practical way to validate the target environment.
