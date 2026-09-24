# 1. Rider / analyzer implementation boundary

Status: **accepted initial implementation decision** for FL-A020.

Decision date: 2026-09-24.

This document chooses the first FactoryLens implementation/runtime boundary needed to satisfy the MVP contract. It is intentionally optimized for getting a real Satisfactory workflow working in Rider with the fewest unnecessary moving parts.

## 1.1 Decision summary

The initial FactoryLens product will use:

```text
Rider frontend/plugin
  -> Kotlin/JVM
  -> IntelliJ Platform plugin APIs
  -> project-level FactoryLens service
  -> direct in-process calls into shared FactoryLens analyzer/core modules
       -> persistent clangd child process
          -> LSP / JSON-RPC over stdin/stdout
       -> transient UnrealBuildTool process when compile metadata must be acquired
```

There is **no separate FactoryLens analyzer daemon** in the first implementation.

There is **no ReSharper/.NET backend plugin** in the first implementation.

There is **no custom Rider<->FactoryLens IPC protocol** in the first implementation.

The headless developer CLI/integration harness will run the same analyzer/core modules in a normal JVM process without Rider.

## 1.2 Rider plugin technology

### Choice

Use a **Kotlin/JVM IntelliJ Platform plugin targeted at Rider**.

Initial development/support line:

```text
Rider 2026.2
```

The production build setup in FL-A030 should initially pin the current stable patch line, **Rider 2026.2.2**, for repeatable development/CI sandboxing unless that exact build exposes an implementation blocker.

Compatibility with Rider 2026.3 EAP is not an MVP requirement.

### Build technology

Use:

```text
Gradle Kotlin DSL
IntelliJ Platform Gradle Plugin 2.x
Kotlin 2.x compatible with the pinned Rider platform
```

Exact Gradle/Kotlin/IntelliJ-plugin versions belong to FL-A030, because they should be pinned together and validated by an actual build.

### Why Kotlin

Current IntelliJ Platform guidance recommends Kotlin for plugin development, and current platform APIs increasingly use Kotlin coroutines for asynchronous/cancellable work.

FactoryLens needs exactly that kind of behavior:

- project-scoped lifecycle;
- cancellable background indexing/query work;
- status/progress propagation;
- responsive tool-window interaction;
- clean cancellation when a project closes or the plugin unloads.

Using Kotlin for both the Rider plugin and the reusable analyzer core also avoids introducing a second product language before there is evidence that one helps.

## 1.3 Rider frontend ownership

The Rider plugin owns only IDE-facing concerns:

- workspace/project lifecycle integration;
- analysis-target selection;
- project-level FactoryLens session lifetime;
- tool-window/tree presentation;
- user actions and filters;
- progress/error/degraded-state presentation;
- jump-to-source navigation;
- Rider settings;
- adapting IntelliJ/Rider APIs to the IDE-independent analyzer interfaces.

The Rider plugin must **not** contain the only implementation of:

- call-graph traversal;
- graph identity/deduplication/cycle rules;
- root-provider logic;
- semantic evidence classification;
- clangd protocol interpretation;
- Satisfactory framework-root rules.

Those belong in reusable FactoryLens modules.

## 1.4 Analyzer/core runtime

### Choice

Implement the supported analyzer and graph logic as **plain Kotlin/JVM modules with no IntelliJ Platform dependency**.

The core should be usable from:

```text
Rider plugin
headless developer CLI
unit/integration tests
```

without starting Rider.

The core owns:

- FactoryLens domain/evidence types;
- semantic-backend abstraction;
- clangd/LSP response interpretation;
- target/project boundary classification;
- framework root providers;
- graph traversal;
- node/edge identity;
- deduplication and cycle handling;
- in-session caches;
- analyzer state/errors independent of IDE widgets.

The exact module names/package layout are FL-A030 concerns.

## 1.5 Process boundary

The MVP has three relevant process roles.

### Rider JVM process

Contains:

- FactoryLens Rider UI;
- Rider project service/lifecycle adapter;
- shared FactoryLens analyzer/core library.

### Persistent clangd process

Contains the expensive C++ semantic/indexing engine.

FactoryLens communicates with clangd through its normal LSP/JSON-RPC stdio channel.

One semantic session should normally exist per supported Rider project/workspace analysis session rather than per query.

The session must be restartable independently from restarting Rider.

### UnrealBuildTool process

UBT is an **on-demand external tool**, not a persistent FactoryLens backend.

FactoryLens invokes or consumes UBT compile metadata only as required by the supported workspace provider.

FL-B100 must reconcile the exact acquisition method with the MVP read-only contract. If a candidate UBT path mutates the analyzed Starter Project merely to generate FactoryLens metadata, it must not be silently accepted as compliant.

## 1.6 No FactoryLens daemon for the MVP

A separate FactoryLens analyzer process would provide useful isolation, but it adds:

- another packaged executable/runtime;
- process orchestration;
- a custom IPC contract;
- serialization/versioning work;
- another failure/restart boundary;
- more integration testing;
- more installation complexity.

The MVP already has the heavy semantic workload isolated in clangd.

Therefore the graph/root/domain logic stays inside the Rider JVM for now, executed asynchronously.

This decision is deliberately reversible because the analyzer core must not depend on IntelliJ APIs. If later evidence shows that analyzer CPU/memory, crash isolation, remote-development behavior, plugin classloader constraints, or multi-frontend reuse justify another process, the same core can be hosted by a daemon and an IPC adapter can be added without rewriting the semantic model.

## 1.7 No ReSharper/.NET backend plugin for the MVP

Rider has a split architecture where IntelliJ provides the frontend and ReSharper commonly provides language-specific backend features.

FactoryLens does **not** currently need ReSharper PSI/language semantics:

```text
FactoryLens C++ semantics
  -> clangd / Clang

FactoryLens UI/navigation
  -> IntelliJ Platform / Rider frontend
```

Adding a ReSharper backend now would require:

- a second implementation language (C#);
- a .NET plugin project;
- generated Rider protocol/RD models;
- frontend/backend synchronization;
- extra build/test/package complexity;

without replacing clangd or satisfying an MVP requirement that the Kotlin-only design cannot satisfy.

Therefore FactoryLens does not create a ReSharper backend merely because Rider supports one.

### When to reconsider

Add a Rider/ReSharper backend only if a later feature specifically needs data or extension points that are substantially better/only available there, for example:

- Rider/ReSharper-owned semantic state FactoryLens genuinely needs;
- a language feature that cannot be implemented reasonably from the frontend plus FactoryLens semantic backend;
- a Rider protocol integration whose benefit outweighs the two-language/backend complexity.

## 1.8 Communication boundaries

The MVP communication model is:

```text
Rider UI
  -> Kotlin project service / application API
     -> FactoryLens analyzer/core APIs
        -> semantic-backend abstraction
           -> clangd LSP JSON-RPC over stdio
```

No serialization is required between the Rider UI and FactoryLens core because they share the JVM.

The product-facing core API must still use FactoryLens domain types rather than raw clangd/LSP objects. FL-A040 owns those exact contracts.

### Headless CLI

The CLI uses:

```text
CLI:  -> same FactoryLens analyzer/core APIs
     -> same semantic-backend abstraction
        -> clangd LSP stdio
```

This gives FL-B170 a real end-to-end harness while keeping Rider out of analyzer correctness testing.

## 1.9 Concurrency and lifecycle

FactoryLens must not run semantic/index/query work on Rider's UI/event-dispatch thread.

The Rider adapter should use a **project-level service with a project/plugin-bound coroutine scope**.

That service owns:

- analyzer session creation;
- clangd process lifetime;
- cancellation;
- restart;
- progress/state flow toward the UI.

Closing the Rider project or unloading FactoryLens must cancel associated work and terminate/release the semantic session cleanly.

The analyzer core should expose suspending/asynchronous operations but should not depend on IntelliJ coroutine scopes itself.

## 1.10 Source navigation boundary

The analyzer core returns trustworthy FactoryLens source-location/domain objects.

The Rider plugin translates those into Rider/IntelliJ navigation operations.

This preserves the MVP rule that FactoryLens may fall back to a declaration/definition when clangd does not identify a trustworthy call-site URI.

The core should never need a Rider editor object in order to describe a source location.

## 1.11 Initial repository/build shape implied by this decision

FL-A030 should establish a Gradle multi-module layout equivalent in responsibility to:

```text
FactoryLens
├─ core / analyzer model + graph + root providers
├─ semantic-clangd / clangd transport/backend adapter
├─ cli / headless integration harness
└─ Rider plugin / IntelliJ Platform frontend
```

The exact names may differ.

The critical dependency rule is:

```text
core
  <- semantic backend adapter
  <- CLI

core + semantic adapter
  <- Rider plugin

core must not depend on Rider/IntelliJ
```

If separating `core` and the clangd adapter into distinct modules adds ceremony without practical value, FL-A030 may initially combine them as one IDE-independent analyzer module. The architectural boundary matters more than maximizing module count.

## 1.12 Initial Rider version policy

For the working-product push:

- develop against the stable **Rider 2026.2** line;
- initially pin **Rider 2026.2.2** for build/sandbox reproducibility in FL-A030;
- do not promise older Rider compatibility yet;
- do not spend MVP effort on 2026.3 EAP support;
- revisit compatibility/version ranges during FL-D350 packaging/compatibility work.

This keeps the implementation target concrete without turning early product work into a compatibility matrix exercise.

## 1.13 Alternatives considered

| Alternative | Decision | Reason |
| --- | --- | --- |
| Kotlin Rider plugin + in-process reusable Kotlin analyzer + external clangd | **Chosen** | Single product language, no custom IPC, analyzer remains headless-testable, heavy C++ work already isolated in clangd. |
| Kotlin Rider frontend + separate Kotlin/JVM FactoryLens daemon + clangd | Not now | Better isolation, but adds IPC/process/package complexity before evidence requires it. |
| Kotlin Rider frontend + C# ReSharper backend + clangd | Not now | Standard Rider split is useful for ReSharper language features, but FactoryLens semantics already come from clangd; adds C#, RD protocol, and backend packaging without MVP benefit. |
| Kotlin Rider frontend + Python analyzer | Rejected for product core | Would reuse research code fastest, but introduces Python runtime/distribution/versioning as an end-user product dependency. Research probes remain valuable as evidence/reference. |
| Kotlin Rider frontend + Rust/native analyzer | Rejected for MVP | Strong isolation/performance potential but requires a full semantic/product rewrite and native packaging without evidence that JVM orchestration is the bottleneck. |
| Deep integration with Rider/ReSharper C++ semantics instead of clangd | Rejected for initial implementation | BX already proved clangd/Clang semantics and the architecture goal is to avoid making Rider internals the only semantic source of truth. |

## 1.14 Reconsideration triggers

This boundary is an MVP decision, not a permanent law.

Reconsider a separate FactoryLens process if measured evidence shows:

- analyzer/core work meaningfully harms Rider responsiveness despite proper asynchronous execution;
- plugin/analyzer bugs need crash isolation;
- memory pressure inside the Rider JVM becomes material;
- remote-development topology makes local in-process analysis incorrect or impractical;
- another frontend becomes a real supported product rather than a theoretical possibility;
- packaging a standalone analyzer becomes simpler than sharing the JVM library.

Reconsider a ReSharper backend if a concrete product feature needs ReSharper/Rider backend APIs enough to justify the extra language/protocol boundary.

## 1.15 External evidence checked for this decision

Checked 2026-09-24 against current JetBrains documentation:

- Rider Plugin Development:
  https://plugins.jetbrains.com/docs/intellij/rider.html
- Rider/ReSharper frontend-backend plugin architecture and RD protocol:
  https://www.jetbrains.com/help/resharper/sdk/Rider.html
- IntelliJ Platform Gradle Plugin 2.x:
  https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- Rider as a supported IntelliJ Platform Gradle target:
  https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-types.html
- Kotlin plugin-development guidance:
  https://plugins.jetbrains.com/docs/intellij/using-kotlin.html
- Kotlin coroutines and project/plugin lifecycle:
  https://plugins.jetbrains.com/docs/intellij/kotlin-coroutines.html
  https://plugins.jetbrains.com/docs/intellij/coroutine-scopes.html
  https://plugins.jetbrains.com/docs/intellij/plugin-services.html
- External process support:
  https://plugins.jetbrains.com/docs/intellij/execution.html
- Rider 2026.2.2 current stable release:
  https://blog.jetbrains.com/dotnet/2026/09/16/rd-rs-2026-2-2/
