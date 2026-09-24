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
```

`SML_PROJECT_ROOT` should point to the SML Starter Project root containing `FactoryGame.uproject`.

`FACTORYLENS_ENGINE_ROOT` is optional. On Windows, supported workspace code otherwise resolves the `FactoryGame.uproject` `EngineAssociation` through the per-user Unreal Engine registry.

The migrated B1-B7 research probes use this path to access the real Unreal/SML workspace. Generated compile databases, clangd indexes, logs, and graph output belong under ignored `work/`.

## 1.3 clangd

The feasibility study found clangd behavior to be version-sensitive:

- clangd 19 lacked the required outgoing call-hierarchy method;
- clangd 22 showed incompatibilities with the tested Clang-19-flavored Unreal workspace;
- clangd 20.1.8 produced the successful semantic call-map results.

Do not translate that experiment into a permanent "20.1.8 forever" product rule. Future supported tooling should pin/test a compatible backend against the Satisfactory/UE/SML baseline it claims to support.

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
