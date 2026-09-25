# 1. Building FactoryLens

This document defines the canonical development/build commands for the supported FactoryLens product skeleton.

**FL-A030 validation:** the initial skeleton was verified on 2026-09-24 with successful Windows and Ubuntu analyzer-module builds plus successful Rider 2026.2.2 plugin packaging and `verifyPluginProjectConfiguration` on Ubuntu.

Research probes under `research/` keep their own experiment-specific commands and are not part of the supported Gradle product build.

## 1.1 Pinned toolchain

The initial FL-A030 build pins:

```text
Rider target:                    2026.2.2
IntelliJ Platform Gradle plugin: 2.19.0
Kotlin Gradle plugin:            2.4.0
Gradle wrapper:                  9.5.0
Java toolchain / bytecode:       25
```

Why these versions:

- Rider/IntelliJ Platform 2026.2 requires Java 25.
- IntelliJ Platform Gradle Plugin 2.x is required for modern platform targets; 2.19.0 was current when FL-A030 was established.
- Rider 2026.2 bundles Kotlin stdlib 2.4.0, so FactoryLens compiles plugin-facing Kotlin against that same stdlib line rather than assuming newer runtime APIs.
- Gradle 9.5.0 is within Kotlin 2.4.0's explicitly supported Gradle range and satisfies IntelliJ Platform Gradle Plugin 2.x requirements.

Version updates are allowed later, but should be deliberate compatibility changes rather than floating build inputs.

## 1.2 Production module layout

```text
FactoryLens/
├─ build.gradle.kts                 Rider plugin build (root product)
├─ settings.gradle.kts
├─ gradle.properties
├─ gradlew / gradlew.bat
├─ src/
│  └─ main/                         Rider/IntelliJ frontend source/resources
├─ core/
│  └─ src/main/kotlin/              IDE-independent FactoryLens domain/graph core
├─ semantic-clangd/
│  └─ src/main/kotlin/              IDE-independent clangd semantic adapter
├─ workspace-satisfactory/
│  └─ src/main/kotlin/              SML workspace / Unreal Engine / UBT compile-metadata adapter
└─ cli/
   └─ src/main/kotlin/              headless developer/integration harness
```

The root Rider plugin depends on `core`, `semantic-clangd`, and `workspace-satisfactory`.

The CLI depends on the same IDE-independent modules and therefore provides a path for testing analyzer/workspace behavior without starting Rider.

The architecture rule is more important than module count:

> `core` and semantic logic must not depend on IntelliJ/Rider APIs.

## 1.3 Prerequisites

For normal local product builds:

- JDK 25 available to Gradle;
- network access for Gradle/Maven/JetBrains IDE dependencies on the first build;
- no local Rider installation is required merely to compile/package the plugin because the Gradle plugin resolves the pinned Rider distribution.

The Gradle wrapper is committed. Do not require a separately installed Gradle.

## 1.4 Canonical commands

POSIX shell:

```bash
./gradlew check
./gradlew :cli:installDist
./gradlew buildPlugin
./gradlew verifyPluginProjectConfiguration
```

PowerShell / cmd-compatible wrapper:

```powershell
.\gradlew.bat check
.\gradlew.bat :cli:installDist
.\gradlew.bat buildPlugin
.\gradlew.bat verifyPluginProjectConfiguration
```

### Headless scaffold

Run the current CLI scaffold with:

```bash
./gradlew :cli:run
```

or on Windows:

```powershell
.\gradlew.bat :cli:run
```

FL-B170 will turn this scaffold into the real analyzer integration harness.

### Rider development sandbox

Once the local machine has the required environment and the plugin reaches the relevant implementation stage:

```bash
./gradlew runIde
```

The pinned target is Rider, so `runIde` prepares a Rider development instance.

FL-C200 owns proving the plugin loads and behaves correctly inside that sandbox; FL-A030 only establishes that the plugin compiles/packages.

## 1.5 CI contract

The repository uses two complementary validation layers:

### Repository checks

Existing Python/repository checks validate documentation structure, generated-state boundaries, research Python syntax, and repository conventions on Windows and Ubuntu.

### JVM/product build

The JVM workflow:

1. installs Java 25;
2. validates/builds `core`, `semantic-clangd`, `workspace-satisfactory`, and `cli` on Windows and Ubuntu;
3. builds the Rider plugin distribution on Ubuntu;
4. runs IntelliJ Platform plugin-project configuration verification.

This makes a broken production Gradle/plugin skeleton a CI failure even when the research/repository checks remain green.

## 1.6 Kotlin stdlib handling

`kotlin.stdlib.default.dependency=false` is intentional.

The Rider plugin runs inside a platform that already supplies Kotlin stdlib. The plugin-facing modules therefore compile against stdlib without bundling a duplicate copy.

The standalone CLI explicitly includes Kotlin stdlib because it runs outside Rider.

Keep this distinction when dependencies/modules are expanded.

## 1.7 Generated output

Gradle/Kotlin generated state is ignored:

```text
.gradle/
.kotlin/
**/build/
```

FactoryLens semantic/indexing artifacts remain under ignored `work/`.

Do not commit Rider sandboxes, Gradle caches, compiled plugin distributions, or analyzer indexes.

## 1.8 Upstream references used for the initial build

Checked 2026-09-24:

- IntelliJ Platform Gradle Plugin:
  https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- IntelliJ Platform Gradle plugin structure:
  https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-plugins.html
- Rider Gradle target:
  https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
- IntelliJ Platform Java/build ranges:
  https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
- Kotlin plugin guidance / bundled stdlib:
  https://plugins.jetbrains.com/docs/intellij/using-kotlin.html
- Kotlin 2.4.0 / Gradle compatibility:
  https://kotlinlang.org/docs/whatsnew24.html
- JetBrains IntelliJ Platform plugin template:
  https://github.com/JetBrains/intellij-platform-plugin-template
