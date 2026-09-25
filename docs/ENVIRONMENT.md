# 1. Development environment

FactoryLens is currently in architecture/tooling bootstrap rather than production-plugin development.

## 1.1 Current research baseline

The completed External Call Map feasibility study was validated against:

```text
Satisfactory 1.2 / CL491125+
Unreal Engine 5.6.1-CSS
SML 3.12.x
Windows + Visual Studio 2022 toolchain
clangd 20.1.8 for the proven call-hierarchy path
Python 3.10+ for the research probes
```

This is an evidence baseline, not yet a permanent FactoryLens compatibility matrix.

## 1.2 Machine-local configuration

Copy:

```text
.env.example -> .env
```

The real `.env` is ignored.

Current keys:

```text
SML_PROJECT_ROOT=
FACTORYLENS_ENGINE_ROOT=
FACTORYLENS_CLANGD=
```

`SML_PROJECT_ROOT` should point to the SML Starter Project root containing `FactoryGame.uproject`.

`FACTORYLENS_ENGINE_ROOT` is optional. On Windows, supported workspace code otherwise resolves the `FactoryGame.uproject` `EngineAssociation` through the per-user Unreal Engine registry.

`FACTORYLENS_CLANGD` should point to the clangd executable selected for supported semantic analysis. The current FL-B110 compatibility policy accepts clangd major 20 and rejects other majors explicitly because the B3 evidence found clangd 19 missing outgoing call hierarchy and clangd 22 incompatible with the current UE/Clang-19-flavored workspace. The task-specific Windows validation harness may bootstrap the official portable clangd 20.1.8 release into ignored `work/factorylens/toolchains/` when no compatible installed binary exists; this does not replace or modify a system LLVM installation.

The migrated B1-B7 research probes use this path to access the real Unreal/SML workspace. Generated compile databases, clangd indexes, logs, and graph output belong under ignored `work/`.

### Supported FL-B100 compile-metadata path

The supported workspace module resolves the configured SML workspace and engine, then obtains the default real Windows analysis view through UnrealBuildTool:

```text
FactoryEditor Win64 Development
-Compiler=VisualStudio2022
-Mode=GenerateClangDatabase
-NoExecCodeGenActions
-OutputDir=<FactoryLens repository>/work/factorylens/compile-metadata/msvc
```

Real Windows validation on 2026-09-25 produced 2,175 translation-unit entries, matching the migrated B1 feasibility baseline.

The pre/post mutation audit found that `-NoExecCodeGenActions` removes the broader response-file/code-generation churn observed during the first validation pass when the requested compiler view matches the workspace's current UBT build state. The remaining 36 mutations were all existing `**/Intermediate/Build/**/UHT/Timestamp` bookkeeping files: 19 changed bookkeeping contents and 17 changed filesystem metadata only.

A later real FL-B110 validation switched that same workspace from its MSVC compile view to the Clang compile view. UBT then rotated 4,601 additional existing generated build-state files under `Intermediate/Build`: 2,269 `.rsp` + 2,269 `.rsp.old`, 31 `Definitions.h` + 31 `Definitions.h.old`, and one `TargetMetadata.dat`. Every response-file and Definitions active/backup pair swapped hashes exactly, while UBT still ran with `-NoExecCodeGenActions`. A second consecutive Clang-view acquisition immediately afterward returned to the stable 36 accepted UHT bookkeeping rewrites with zero unexpected mutations. The exact existing-file families are therefore a second separately documented exception in [`MVP_CONTRACT.md`](MVP_CONTRACT.md); mutations outside the documented exception families remain unexpected.

## 1.3 clangd

The feasibility study found clangd behavior to be version-sensitive:

- clangd 19 lacked the required outgoing call-hierarchy method;
- clangd 22 showed incompatibilities with the tested Clang-19-flavored Unreal workspace;
- clangd 20.1.8 produced the successful semantic call-map results.

Do not translate that experiment into a permanent "20.1.8 forever" product rule. Future supported tooling should pin/test a compatible backend against the Satisfactory/UE/SML baseline it claims to support.

### Supported FL-B110 session behavior

The supported semantic backend starts one persistent external clangd process for a configured workspace/compile database, performs the LSP initialization handshake once, verifies required call-hierarchy capability, and reuses that process for subsequent semantic requests until the session is closed or restarted by a later analyzer layer.

The default B110 validation input is the B3-proven Clang compiler view:

```text
work/factorylens/compile-metadata/clang/compile_commands.json
```

clangd starts with background indexing enabled. Its on-disk background index is therefore retained under:

```text
work/factorylens/compile-metadata/clang/.cache/clangd/index/
```

next to the compilation database, keeping persistent index state under FactoryLens-controlled ignored `work/`. This location follows clangd's documented background-index cache behavior: <https://clangd.llvm.org/design/indexing>.

Backend version and capability mismatches are explicit startup failures rather than degraded empty analysis. Raw JSON-RPC/LSP messages remain internal to `semantic-clangd`; product/domain callers must not depend on them.

Real Windows FL-B110 validation on 2026-09-25 used clangd 20.1.8 from the WinGet link and the 2,175-entry UBT Clang compile database. Supported FactoryLens code initialized the backend to `READY`, observed `callHierarchyProvider=true`, reported the configured background-index cache under FactoryLens `work/`, and shut the same process down cleanly to `STOPPED`. The task-specific validation script exercised this path without invoking any migrated BX research probe.

## 1.4 Rider

FL-A020 selects the stable Rider 2026.2 line as the initial MVP target. FL-A030 should begin by pinning Rider 2026.2.2 for repeatable development/CI sandboxing unless implementation evidence requires another 2026.2 patch.

FactoryLens will use a Kotlin/JVM IntelliJ Platform plugin frontend plus IDE-independent Kotlin/JVM analyzer modules. No ReSharper/.NET backend plugin or separate FactoryLens analyzer daemon is planned for the MVP.

## 1.5 Production build toolchain

FL-A030 establishes the initial supported product build with:

```text
Rider target:                    2026.2.2
Java toolchain:                  25
Kotlin:                          2.4.0
Gradle wrapper:                  9.5.0
IntelliJ Platform Gradle plugin: 2.19.0
```

See [`BUILDING.md`](BUILDING.md) for canonical build commands.

## 1.6 Repository validation

Run:

```text
python scripts/check_repository_consistency.py
python -m unittest discover -s tests -v
./gradlew check
./gradlew buildPlugin verifyPluginProjectConfiguration
```

CI runs the same repository checks on Windows and Ubuntu and compiles tracked Python sources for syntax validation.
