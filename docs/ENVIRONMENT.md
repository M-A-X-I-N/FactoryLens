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

Current key:

```text
SML_PROJECT_ROOT=
```

It should point to the SML Starter Project root containing `FactoryGame.uproject`.

The migrated B1-B7 research probes use this path to access the real Unreal/SML workspace. Generated compile databases, clangd indexes, logs, and graph output belong under ignored `work/`.

## 1.3 clangd

The feasibility study found clangd behavior to be version-sensitive:

- clangd 19 lacked the required outgoing call-hierarchy method;
- clangd 22 showed incompatibilities with the tested Clang-19-flavored Unreal workspace;
- clangd 20.1.8 produced the successful semantic call-map results.

Do not translate that experiment into a permanent "20.1.8 forever" product rule. Future supported tooling should pin/test a compatible backend against the Satisfactory/UE/SML baseline it claims to support.

## 1.4 Rider

Rider is the planned primary frontend, but FactoryLens does not yet require a particular Rider version because the plugin/frontend implementation has not started.

## 1.5 Repository validation

Run:

```text
python scripts/check_repository_consistency.py
python -m unittest discover -s tests -v
```

CI runs the same repository checks on Windows and Ubuntu and compiles tracked Python sources for syntax validation.
