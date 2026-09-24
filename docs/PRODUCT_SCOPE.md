# 1. Product scope

## 1.1 Primary goal

FactoryLens should help a developer **make and understand Satisfactory mods using Rider**.

That is the product test against which early features should be judged.

## 1.2 Satisfactory-first, not Satisfactory-hardcoded

The architecture should preserve obvious reusable seams, but it should not spend large amounts of effort proving genericity before the Satisfactory experience exists.

Preferred order:

```text
Satisfactory use case
  -> reusable semantic/graph core where natural
  -> shared Unreal support when it genuinely reduces duplication
  -> generic C++ support when it falls out cheaply
```

A generic design that makes Satisfactory-specific Unreal/SML behavior substantially harder to express is the wrong trade.

## 1.3 Rider's role

Rider is the intended first-class frontend.

FactoryLens should not assume that Rider/ReSharper must own semantic truth. The completed feasibility work shows that UBT plus clangd/Clang can provide a viable independent semantic foundation.

That keeps open inexpensive future uses such as a CLI, CI analysis, or another frontend without requiring those products to be built now.

## 1.4 Initial capability family

The first flagship feature is an **external/framework-aware call map**.

FactoryLens should also preserve room for adjacent Satisfactory-development assistance such as entry-point/lifecycle discovery, SML/Unreal framework-aware navigation, hook/delegate/RPC/replication provenance, UHT/reflection-aware navigation, and project/environment understanding.

These are product directions, not an implementation checklist.

## 1.5 Initial non-goals

FactoryLens does not initially promise perfect reconstruction of every Unreal runtime call, deep Blueprint graph reconstruction, universal C++ framework support, a replacement C++ parser/compiler, or replacement of Rider's ordinary navigation/refactoring features.
