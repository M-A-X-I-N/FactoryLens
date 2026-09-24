# 1. Architecture

FactoryLens currently follows the layer direction established by the completed B8 feasibility decision:

```text
UnrealBuildTool compile truth
  -> Clang/clangd semantic service
     -> Satisfactory/Unreal/SML entry-point adapters
        -> bounded cached graph/model
           -> Rider frontend
```

This document is the durable architecture landing page. The next repository checkpoint expands the responsibilities and boundaries of these layers.
