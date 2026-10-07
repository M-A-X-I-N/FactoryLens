# Semantic validation scar tissue

This is non-normative FactoryLens memory. Current source, docs, and task state remain authoritative.

## RSS2 path canonicalization

The configured SML workspace exposes RSS2 logically beneath:

```text
<workspace>/Mods/GameFeatures/RSS/Source/RSS
```

but the real development setup may route that tree through a symlink/junction into the older
`satisfactory-wiremod-rss-integration/vendor/rss-current` physical tree. clangd has returned the
canonical physical URI rather than the lexical workspace URI.

Consequences already reflected in supported code:

- source-realm classification must compare lexical and real-path equivalents;
- navigation should preserve the URI clangd actually returned rather than rewriting it;
- a physical `vendor/rss-current` URI is not evidence that clangd analyzed the wrong project.

## Cold background-index behavior

Foreground clangd semantics and background-index coverage have different readiness properties.

A real FL-B130 run prepared and expanded
`URssBlueprintFunctionLibrary::IsSignDataSafe` while `background_index=RUNNING`. The foreground
query returned target-local calls such as `IsSafeNumber`, but did not yet return the historically
proven cross-file edge:

```text
URssBlueprintFunctionLibrary::IsSignDataSafe
  -> URssDownloadImage::IsStructurallySafeRemoteImageUrl
```

The B3 research had already proven that cross-file edge with clangd 20.1.8. Do not interpret its
absence from an immediate cold foreground query as proof that the edge is invalid. Validation that
specifically requires cross-file/index-backed coverage should explicitly account for index
readiness rather than silently weakening the semantic claim.

## Windows PowerShell validation trap

Windows PowerShell 5.1 promotes native stderr to PowerShell error records in some invocation
patterns. Java writes `java -version` to stderr even on success.

This previously caused FL-B110/B130 validation to appear failed or frozen when combined with
`$ErrorActionPreference = "Stop"` and nested/captured PowerShell execution.

Supported scripts now avoid that trap by:

- using `System.Diagnostics.ProcessStartInfo` for native version probes;
- keeping B130/B150 validation stages in one PowerShell process;
- streaming child/Gradle/UBT/clangd progress instead of buffering a nested validation script.

Do not reintroduce nested captured PowerShell merely to recover environment values.

## Real clangd external-override naming

Real clangd call-hierarchy items for RSS2 overrides may expose only the method name, for example:

```text
Tick
GetRotationStep
IsValidHitResult
```

rather than a class-qualified display name such as `ARssDataManagerSubsystem::Tick`.

The stable semantic `SymbolDescriptor` must remain unchanged so its `SymbolId` stays compatible
with outgoing-call expansion. Provider-specific human identity belongs in `RootDescriptor.label`.

## First supported FL-B150 real scan

The first real supported FL-B150 RSS2 scan (before the root-label/generated-base correction) is
useful diagnostic history:

- 30 target headers scanned;
- discovery returned `COMPLETE`;
- 211 roots were emitted before generated/UHT base filtering;
- every emitted root had concrete external-base evidence;
- the configured ordinary-local negative controls remained absent;
- known positive override methods were visibly present semantically;
- the validation gate falsely reported all class-qualified positive controls missing because the
  semantic symbols carried unqualified method names;
- many roots pointed at generated/UHT declarations, demonstrating that `GENERATED` must not count
  as a generic external-framework base;
- clangd background indexing was still running and shutdown completed cleanly.

The corrective product changes add `RootDescriptor.label`, preserve the exact prepared semantic
descriptor, reject generated/UHT base realms for the generic override provider, mirror real
unqualified naming in the fake-clangd fixture, and compact the real validation output.

The authoritative remaining execution state for FL-B150 belongs in `meta/tasks/FL-B150.md`, not
in this memory note.
