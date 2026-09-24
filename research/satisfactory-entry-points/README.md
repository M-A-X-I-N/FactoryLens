# 1. Satisfactory/Unreal/SML entry-point model

> **Research reference.** Derived from `M-A-X-I-N/satisfactory-wiremod-rss-integration/docs/ENTRY_POINTS.md` at source commit `8d0ee54f80a9123142cba13f00a175be1161244f` when the FactoryLens feasibility work moved into this repository.
>
> The original document also contained integration-project-specific skeleton/source-anchor sections. Those were intentionally not copied here because FactoryLens needs the reusable entry-point model, not the private source project's current implementation description.

This document preserves the conceptual model that motivated FactoryLens: in Unreal/SML code, a method can be externally reachable even when ordinary C++ source contains no literal caller.


## 1.2 Registration vs callback

A large fraction of Unreal/SML control flow comes in **pairs**:

```text
something runs once
    ↓
registers / binds / subscribes something
    ↓
time passes
    ↓
engine/game/framework event occurs
    ↓
registered callback runs
```

For example:

```text
StartupModule()
    ↓
SUBSCRIBE_METHOD(...)
    ↓
Satisfactory later calls the hooked function
    ↓
our hook handler runs
```

The hook handler is an entry point into our behavior, but it does not become active merely because the handler function exists. Some earlier code must register it.

The same applies to delegates:

```text
BeginPlay()
    ↓
SomeDelegate.AddUObject(...)
    ↓
event happens later
    ↓
HandleSomething()
```

When tracing unfamiliar code, identify **both halves**:

1. **Who registers/discovers this callback?**
2. **Who invokes it later?**

## 1.3 Quick lookup table

| You see | Who normally calls it | Mental analogy |
| --- | --- | --- |
| `StartupModule()` | Unreal Module Manager | library/plugin startup; closest thing to a mod-level `main()` |
| `ShutdownModule()` | Unreal Module Manager | library/plugin teardown |
| `DispatchLifecycleEvent(...)` on an SML Mod Module | SML | framework lifecycle callback |
| `BeginPlay()` | Unreal Actor/component lifecycle | "this runtime object is now live" |
| `Tick(...)` / `TickComponent(...)` | Unreal each enabled tick | game-loop callback |
| `EndPlay(...)` | Unreal | runtime object teardown |
| subsystem `Initialize(...)` / `Deinitialize()` | Unreal subsystem framework | service lifecycle |
| SML `AModSubsystem::Init()` | SML subsystem manager | mod-world service initialization |
| delegate callback | whichever owner broadcasts the delegate | event listener / `onclick=` |
| `SUBSCRIBE_METHOD(...)` handler | SML hook machinery when the target function is called | interceptor / monkey-patch callback |
| `BlueprintImplementableEvent` implementation | C++/Unreal code invoking the reflected event | overridable event |
| Game Feature Action/Data | Unreal Game Features system | manifest/data-driven activation |
| RPC implementation | Unreal networking | network message handler |
| `OnRep_...` function | Unreal replication system on clients | replicated-value-change callback |
| input binding callback | Unreal input system | key/button event handler |
| widget lifecycle callback | Unreal/UMG | UI lifecycle callback |

This table is a map, not a complete Unreal API catalogue.

## 1.4 Unreal C++ module lifecycle

An Unreal **C++ module** is the closest project-level concept to the startup of a normal library/program.

Do not confuse this with an [SML Mod Module](#15-sml-mod-module-lifecycle); Unreal unfortunately uses the word "module" for both.

A plugin descriptor declares the C++ module:

```json
"Modules": [
  {
    "Name": "Maxin_WiremodRssIntegration",
    "Type": "Runtime",
    "LoadingPhase": "Default"
  }
]
```

The implementation is registered with Unreal using a macro such as:

```cpp
IMPLEMENT_GAME_MODULE(
    FMaxin_WiremodRssIntegrationModule,
    Maxin_WiremodRssIntegration
);
```

The macro is **registration**, not the arbitrary startup body itself. Unreal's Module Manager loads the module at the configured loading phase and drives its lifecycle.

A module class can override:

```cpp
class FExampleModule : public FDefaultGameModuleImpl
{
public:
    virtual void StartupModule() override;
    virtual void ShutdownModule() override;
};
```

Conceptually:

```text
Unreal starts
    ↓
reads .uplugin
    ↓
reaches configured LoadingPhase
    ↓
loads module binary
    ↓
constructs registered module implementation
    ↓
StartupModule()
    ↓
...module remains loaded...
    ↓
ShutdownModule()
```

### What `StartupModule()` means

It means approximately:

> "This C++ module has loaded."

It does **not** mean:

> "A player is in a save and a normal gameplay world is ready."

That makes `StartupModule()` useful for module-wide registration such as installing hooks or registering delegates whose prerequisites already exist. It is often the wrong place for code that assumes a gameplay world, player, save, or specific actor exists.

## 1.5 SML Mod Module lifecycle

SML has a separate system named **Mod Modules**.

Its base class is:

```cpp
UModModule
```

SML dispatches three lifecycle phases:

```cpp
ELifecyclePhase::CONSTRUCTION
ELifecyclePhase::INITIALIZATION
ELifecyclePhase::POST_INITIALIZATION
```

A C++ Mod Module can override:

```cpp
virtual void DispatchLifecycleEvent(ELifecyclePhase Phase) override;
```

For example:

```cpp
void UExampleGameWorldModule::DispatchLifecycleEvent(ELifecyclePhase Phase)
{
    Super::DispatchLifecycleEvent(Phase);

    if (Phase == ELifecyclePhase::POST_INITIALIZATION)
    {
        // Framework-lifecycle work.
    }
}
```

SML's own `UModModule` implementation records each received phase, forwards it to child modules, and invokes its Blueprint lifecycle event.

### Root Mod Module types

| Type | Lifetime/context |
| --- | --- |
| `UGameInstanceModule` | Bound to the game-instance lifetime |
| `UGameWorldModule` | Bound to a normal gameplay world; SML explicitly skips menu worlds |
| `UMenuWorldModule` | Bound to menu-world behavior |

SML discovers root modules for mods and manages their lifecycle. A root module therefore does not require ordinary project code to manually call `DispatchLifecycleEvent(...)`.

Current Wiremod provides a concrete example:

```cpp
class UWiremodGameWorldModule : public UGameWorldModule
```

and performs setup during `ELifecyclePhase::POST_INITIALIZATION`.

When reading that code, the answer to:

> "Who calls `UWiremodGameWorldModule::DispatchLifecycleEvent`?"

is:

> **SML's Mod Module lifecycle machinery.**

## 1.6 Actor and component lifecycle

Many Unreal entry points are simply **virtual functions on objects whose lifetime Unreal owns**.

For an Actor:

```cpp
class AExampleActor : public AActor
{
    ...
};
```

common runtime callbacks include:

```cpp
BeginPlay()
Tick(...)
EndPlay(...)
```

The conceptual flow is roughly:

```text
Actor created/spawned
    ↓
Unreal initializes it and its components
    ↓
BeginPlay()
    ↓
Tick() repeatedly, if ticking is enabled
    ↓
EndPlay()
    ↓
destruction/cleanup
```

The key reading rule is:

> If a function is marked `override`, inspect the parent class before searching the repository for a direct call.

The engine may be calling the base-class virtual function through polymorphism.

### Constructors are not equivalent to `BeginPlay()`

Unreal constructors can run while creating class-default objects, loading assets, working in the editor, spawning runtime instances, and other engine-managed contexts. Code that requires a live gameplay world generally belongs in an appropriate later lifecycle callback.

### Components

`UActorComponent`-derived types have their own lifecycle, including callbacks such as:

```text
OnRegister()
BeginPlay()
TickComponent(...)
EndPlay(...)
```

Unreal calls these because the component participates in the engine-managed object/component lifecycle.

## 1.7 Subsystems

Unreal's subsystem framework is a managed-service pattern.

Common families include:

```text
UEngineSubsystem
UGameInstanceSubsystem
UWorldSubsystem
ULocalPlayerSubsystem
```

A subsystem commonly implements:

```cpp
Initialize(...)
Deinitialize()
```

The relevant Unreal subsystem collection creates/manages it and invokes those callbacks.

For example, SML's mod-loading library derives from `UGameInstanceSubsystem` and overrides `Initialize(FSubsystemCollectionBase& Collection)`, so the GameInstance subsystem framework is what starts it.

### SML `AModSubsystem`

SML also provides an Actor-based mod subsystem:

```cpp
AModSubsystem
```

It has a replication policy and a framework-managed `Init()` callback. SML guarantees `Init()` is dispatched before the child class receives `BeginPlay()`.

Conceptually:

```text
game world
    ↓
SML subsystem manager spawns/registers mod subsystem
    ↓
Init()
    ↓
BeginPlay()
```

## 1.8 Delegates and events

Delegates are one of Unreal's main event systems.

They are conceptually close to JavaScript event listeners:

```text
register callback
    ↓
event owner later broadcasts
    ↓
callback runs
```

Typical binding shapes include concepts such as:

```cpp
SomeDelegate.AddUObject(this, &UMyObject::HandleSomething);
```

or lambdas:

```cpp
SomeDelegate.AddLambda(MyLambdaHandler);
```

When tracing a delegate callback, searching only for direct calls to `HandleSomething()` may find nothing. Instead search for:

1. where `HandleSomething` is **bound**;
2. what delegate it is bound to;
3. where that delegate is **broadcast**.

Delegates can be single-cast, multicast, reflected/dynamic, native-only, etc. Those distinctions affect syntax and capabilities, but the control-flow model remains registration followed by later invocation.

## 1.9 SML native hooks

SML can intercept existing native C++ functions with helpers such as:

```cpp
SUBSCRIBE_METHOD(...)
SUBSCRIBE_METHOD_AFTER(...)
SUBSCRIBE_UOBJECT_METHOD(...)
SUBSCRIBE_UOBJECT_METHOD_AFTER(...)
```

A simplified mental model:

```text
our setup code
    ↓
SUBSCRIBE_METHOD(TargetFunction, OurHandler)
    ↓
SML installs hook/interceptor
    ↓
time passes
    ↓
normal game/mod code calls TargetFunction(...)
    ↓
SML hook machinery intercepts it
    ↓
OurHandler(...)
    ↓
original function continues, unless hook semantics alter that
```

"Before" hooks run in the call chain before the original function. "After" hooks run after it.

Depending on the signature, SML's call-scope machinery can support behavior such as forwarding the original call, cancelling a `void` call, or overriding a returned value.

### A hook handler is not self-starting

Writing a handler does nothing by itself. Execution must first reach the subscription expression.

Every hook design therefore has two entry-point questions:

```text
Where do we INSTALL the hook?
        +
When does the TARGET function get called?
```

The first may be `StartupModule()`, an SML lifecycle callback, subsystem initialization, or another suitable one-time setup point.

## 1.10 Blueprint callbacks and Blueprint hooks

Unreal reflection lets C++ expose functions/events to Blueprint.

For example:

```cpp
UFUNCTION(BlueprintImplementableEvent)
void DoSomething();
```

means the C++ declaration defines an event contract that Blueprint may implement.

The Blueprint graph is not running merely because the asset exists. Some C++/Unreal code must eventually invoke the reflected function/event:

```text
C++ / engine calls reflected event
    ↓
Unreal reflection dispatch
    ↓
Blueprint implementation executes
```

`BlueprintNativeEvent` is related but allows a native C++ implementation as well as Blueprint override behavior.

### SML Blueprint hooks and mixins

SML also has machinery for altering/intercepting Blueprint-generated classes and attaching mixin behavior.

That is different from SML's native C++ hooks:

```text
native hook
    -> intercept compiled/native C++ function

Blueprint hook/mixin
    -> alter or extend Blueprint-generated behavior/classes
```

SML's `UGameInstanceModule` can register Blueprint hooks/mixins as part of framework-managed setup.

## 1.11 Game Features

Satisfactory 1.2/SML 3.12 uses Unreal's **Game Features** system for much mod content registration.

This project currently has:

```text
Maxin_WiremodRssIntegration.uplugin
    BuiltInInitialFeatureState = Active

Content/
    Maxin_WiremodRssIntegration.uasset
        class = FGGameFeatureData
```

This is an entry path into the mod, but primarily a **declarative** one.

Conceptually:

```text
plugin/Game Feature becomes active
    ↓
Unreal locates its FGGameFeatureData
    ↓
configured Game Feature Actions / asset scan rules are processed
    ↓
content becomes registered/available
```

The data asset is therefore closer to a manifest/configured activation graph than a `main()` function.

## 1.12 Networking and replication callbacks

Unreal networking introduces more framework-driven entry points.

### RPCs

Functions declared as server/client/multicast RPCs are invoked through Unreal's networking machinery.

```text
code invokes RPC
    ↓
Unreal serializes/routes network call as required
    ↓
remote/local target receives it
    ↓
RPC implementation executes
```

The exact call path depends on authority, owning connection, RPC type, and whether the call is local or remote.

### RepNotify / `OnRep`

A replicated property can name a notification function, commonly `OnRep_Something()`.

```text
server authoritative property changes
    ↓
Unreal replication sends update
    ↓
client applies value
    ↓
OnRep_... callback
```

This can make a function appear to have no caller in ordinary C++ source because the caller is the replication framework.

## 1.13 Input, UI, and specialized callbacks

Many subsystem-specific APIs follow the same callback pattern.

### Input

Input code normally binds an action/key to a function:

```text
setup/bind input
    ↓
player presses key/button
    ↓
input framework invokes callback
```

### UMG/widgets

Widgets have engine-managed lifecycle callbacks/events such as construction, ticking, and destruction. Native widget subclasses may override native lifecycle functions; Blueprint widgets expose corresponding Blueprint events.

### Chat commands, content registries, and similar SML systems

SML framework types can register specialized classes—chat commands, Remote Call Objects, content types, etc.—through Mod Module configuration or framework registries.

The same reading rule applies:

```text
class/config is registered with a framework
    ↓
framework-specific event occurs
    ↓
framework instantiates/invokes that class or handler
```

Do not assume every class in a mod must be explicitly constructed by another source file in the same mod.

## 1.15 How to answer "who calls this?"

When a function looks like it has no caller, use this order.

### Step 1: Is it an `override`?

Example:

```cpp
virtual void BeginPlay() override;
```

If yes, inspect the parent class. The caller may be Unreal/SML invoking the base virtual interface rather than a source line calling the derived function by name.

### Step 2: Is it a known framework lifecycle name?

Examples:

```text
StartupModule
ShutdownModule
Initialize
Deinitialize
BeginPlay
Tick
EndPlay
DispatchLifecycleEvent
Init
OnRep_...
```

If so, identify the owning framework/class family.

### Step 3: Is it a reflected Unreal function/event?

Look for `UFUNCTION(...)` and modifiers such as:

```text
BlueprintImplementableEvent
BlueprintNativeEvent
Server
Client
NetMulticast
```

Reflection/network/Blueprint machinery may be the caller.

### Step 4: Is it bound to a delegate?

Search for registrations such as:

```text
AddUObject
AddDynamic
AddLambda
Bind...
```

Then trace the delegate's broadcast.

### Step 5: Is it a hook handler?

Search for:

```text
SUBSCRIBE_METHOD
SUBSCRIBE_METHOD_AFTER
SUBSCRIBE_UOBJECT_METHOD
```

Then separately trace:

1. when the subscription executes;
2. when the hooked target executes.

### Step 6: Is it driven by data/assets rather than a direct call?

Check for:

```text
.uplugin
FGGameFeatureData
root SML Mod Module
Blueprint class
asset registry discovery
content registration
```

Framework discovery can make "grep for caller" the wrong question.

### Step 7: Only then search for ordinary direct calls

If none of the framework mechanisms apply, ordinary C++ call tracing is probably appropriate:

```text
SomeFunction(...)
Object->SomeFunction(...)
ClassName::SomeFunction(...)
```

This ordering avoids wasting time looking for a literal caller that cannot exist because the engine invokes the function indirectly.

## 1.16 Common traps

### "Module" has at least two important meanings here

```text
Unreal C++ module
    -> DLL/build/module-loader concept
    -> StartupModule / ShutdownModule

SML Mod Module
    -> UObject/framework lifecycle concept
    -> CONSTRUCTION / INITIALIZATION / POST_INITIALIZATION
```

Always determine which one a document or class is talking about.

### Existence is not registration

A callback function can compile perfectly and never execute because nothing registered/discovered it.

### Registration is not invocation

Seeing `SUBSCRIBE_METHOD`, `AddUObject`, or input binding tells you how the callback becomes reachable. It does not tell you when the underlying event/target occurs.

### Constructor is not "gameplay has started"

Unreal constructors can run in contexts where there is no live gameplay world.

### `StartupModule()` is not "player entered a save"

It is module-loading lifecycle.

### `BeginPlay()` is per-object, not global

Every relevant runtime Actor/component instance can receive its own `BeginPlay()`.

### `Tick()` is not automatic for every object

The type must support ticking and ticking must be enabled/configured appropriately.

### Blueprint assets are executable behavior, not merely data

A Blueprint can supply event implementations and generated classes whose calls are driven by Unreal even when no obvious C++ file names the implementation.

### Game Feature Data is not a C++ function

It is declarative engine-owned activation/registration data.

### A hook needs a safe installation lifetime

Installing a hook "somewhere that runs" is not enough. The chosen registration point must also have the correct lifetime, prerequisites, and cleanup behavior.
