# Hexweaves: design

Status: **Phase 6 implemented**: the Hex Loom edits whole weaves - several Threads, their links and
their layout - in an overview that zooms into the Phase 5 Thread editor (see
[Phase 6](#phase-6--multi-thread-loom); its GUI is not yet checked in a real client). Before that,
**Phase 5**: the Hex Loom's single-Thread editor (see [Phase 5](#phase-5--loom-thread-editor), since
checked in a real client and found usable). Before that,
**Phase 4**: the Woven Orb, the first player-usable host (see
[Phase 4](#phase-4--woven-orb)). Before that, **Phase 3** (versioned stored format, shared execution budget, dev storage) on top
of the Phase 2 execution proof of concept. Phase 1 (reconnaissance) was written 2026-09-28 against
Hexwright `master` @ `949f65c`; Phase 1 statements that the 0.11.4 check disproved are corrected in
place and marked **(corrected in Phase 2)**. See [Phase 2: implementation status](#phase-2-implementation-status)
and [Phase 3: implementation status](#phase-3-implementation-status).

**Shipped (2026-10-09).** The `HexwrightDev.HEXWEAVE` flag that used to keep all of this unregistered is
gone: the Woven Orb, the Hex Loom, their packets and `/hexwright weave` always register. Both are Essence
Forge recipes learned off Stone Tablets (`recipe_tablets.json`). Only the Loom's layout tooling is still a
dev feature (`HexwrightDev.LOOM_TOOLS`).

Every claim below is tagged:

- **[V]** verified by reading the source cited next to it;
- **[I]** architectural inference from verified facts;
- **[?]** unresolved, needs a test or a decision.

**Source note.** `AGENTS.md` points at `reference/mods`, which does not exist under `hexwright/`. The
reference checkouts live in the sibling project at `../manifestation/references/mods/` (HexMod-main,
Hexal-main, hexcassettes-main, hexical-main, Focalworks-main, ...). The Hex Casting trace below uses
`HexMod-main`. Its `gradle.properties` says `0.11.3`, but it already contains 0.11.4's `costModifier`
change, and its `CastingImage` constructor is `private` while Hexwright calls that constructor directly
against the 0.11.4 jar (`TalismanCasting.java:239`). So the checkout sits between 0.11.3 and 0.11.4 and
is **not** a byte-for-byte copy of what we ship. That is acceptable here because every Hex API this
design depends on is already called by Hexwright code compiled against 0.11.4 (listed under
[Hex Casting API surface used](#hex-casting-api-surface-used)). Hexwright declares
`"hexcasting": ">=0.11.4"` in `fabric.mod.json`, so compatibility with older 0.11.x releases does not
arise.

**Phase 2 verified the trace against the real 0.11.4.** The published 0.11.4 sources jar contains Java
only, no Kotlin, so the Kotlin classes (`CastingVM`, `CastingImage`, `OpHalt`, `FrameEvaluate`,
`SpellAction`, `ConstMediaAction`, `OperatorSideEffect`, `OpThanos`, the mishaps) were checked by `javap -c`
against the Mojmap-remapped 0.11.4 binary in `.gradle/loom-cache/remapped_mods/`, and the Java classes
(`PatternIota`, `CastingEnvironment`, `IotaType`, `EntityIota`, `ContinuationIota`, the env classes)
against the remapped 0.11.4 sources jar next to it. The differences that matter are listed under
[0.11.4 verification](#0114-verification); none of them changes the architecture.

---

## Concept

A **Hexweave** is a set of independently written Hex Casting programs (**Threads**) joined by directed
**links**. Each Thread is an ordinary hex. A Thread may have an **Ingress** (execution enters from
another Thread) and an **Egress** (execution leaves and activates every Thread linked from it). A
Thread without an Ingress is a **root**: it runs when whatever hosts the weave activates it.

A weave defines *what happens*. When it happens (use, hurt, ward trip, resonant message) is defined by
the host object. The weave has no triggers, event listeners or networking of its own.

## Invariants

These come from the brief and are treated as constraints.

1. A Thread contains ordinary Hex Casting logic.
2. A Thread has at most one Ingress.
3. A Thread has at most one Egress.
4. One Egress can connect to many Threads.
5. One Ingress can receive links from many Threads.
6. Multiple incoming links mean "any of these may activate me", never JOIN.
7. Threads stay structurally separate. A weave is never built by concatenating pattern lists.
8. Filtering, maths, entity queries and branching stay normal Hex Casting.
9. Interlacing is local execution composition. It does not replace the Resonant/Harmonic network.
10. Hexweaves do not listen to Minecraft events. Triggers belong to the host.

This design adds three derived rules:

- **D1.** A Thread invocation never runs inside another Thread's `CastingVM`. Each invocation gets its
  own VM, image and environment.
- **D2.** The degenerate weave (one root Thread, no Egress) behaves exactly like today's triggered
  hexes (Talisman, Warding Box, Resonant Ring): a fresh image seeded with the host's iota and a fresh
  environment.
- **D3.** The stored weave never references runtime objects (entities, levels, block entities). The only
  things that cross a link are serialised iota tags.

---

## Relevant existing architecture

### Hex Casting: what actually happens when one program runs another

**The VM loop** [V] (`api/casting/eval/vm/CastingVM.kt:41-95`)

- `queueExecuteAndWrapIotas(iotas, level)` starts from
  `SpellContinuation.Done.pushFrame(FrameEvaluate(iotas, false))`.
- The loop pops the top frame, calls `frame.evaluate(next, level, vm)` and gets back a `CastResult`
  (`newData`, `continuation`, `sideEffects`, `resolutionType`, `sound`).
- It then rejects oversized stacks with `MishapStackSize` (`isTooLargeToSerialize`), sets
  `image = newData`, calls `env.postExecution(result)` and **then** `performSideEffects`.
- **(corrected in Phase 2)** In 0.11.4 the op-budget check is here too, not in `PatternIota`: after
  every step, `newData.opsConsumed > env.maxOpCount()` replaces the result with a `DoMishap(MishapEvalTooMuch)`,
  `ERRORED` and `newData = null`. It therefore applies to every frame type, and the VM keeps the image
  from *before* the step that went over.
- The loop exits early as soon as `!resolutionType.success`. After the loop, `env.postCast(image)` runs
  once and an `ExecutionClientView` is returned.
- An exception thrown by a side effect is caught and turned into a `MishapInternalException` side
  effect. It does **not** stop the loop or change the resolution type.

**One iota** [V] (`CastingVM.executeInner`, `iota/PatternIota.java:71-136`)

- **(corrected in Phase 2)** In 0.11.4 there is no `handleParentheses`. `executeInner` handles only a
  pending Consideration (the iota is escaped onto the stack, or into the open list). Introspection,
  Retrospection, Evanition and Consideration are ordinary actions (`open_paren`, `close_paren`, `undo`,
  `escape`), and while a list is open every iota goes through `Iota.executeInParens`, which for a
  pattern calls `Action.operateInParens`.
- A pattern resolves through `PatternRegistryManifest.matchPattern`, then `env.precheckAction` (which
  sets `costModifier` and checks the denylist), then `action.operate(env, image, continuation)`.
- ~~After `operate`, `opsConsumed > env.maxOpCount()` throws `MishapEvalTooMuch`.~~ **(corrected in
  Phase 2)**: moved to the VM loop, see above.
- A `Mishap` is caught here and becomes a `DoMishap` side effect with resolution `ERRORED` or `INVALID`
  and the continuation unchanged. Mishaps never escape the VM as exceptions. In 0.11.4 a mishap inside a
  metacast (Hermes) also closes any open Introspection instead of leaving it open. Every Hex 0.11.4
  mishap's resolution type is unsuccessful (`ERRORED`, or `INVALID` for invalid, disallowed and
  unenlightened patterns).

**Nested evaluation is continuation manipulation inside the same VM** [V]

| Pattern | Class | What it does to the continuation |
|---|---|---|
| Hermes' Gambit | `common/casting/actions/eval/OpEval.kt` | Pushes a `FrameFinishEval` boundary (unless the top frame already is one, or a single pattern is being evaluated), then `FrameEvaluate(list, isMetacasting=true)`. Same image, stack, op budget and env. Costs 1 op. |
| Thoth's Gambit | `OpForEach.kt`, `vm/FrameForEach.kt` | A `FrameForEach` saves `baseStack` and re-pushes itself plus `FrameEvaluate(code)` once per datum, then pushes the accumulator list. |
| Charon's Gambit | `OpHalt.kt` | Pops frames until one whose `breakDownwards` returns `true` (`FrameFinishEval`, `FrameForEach`). If none does, it **clears the stack** and the cast ends. |
| Iris' Gambit | `iota/ContinuationIota.java:50` | Executing a `ContinuationIota` replaces the whole continuation with the saved one. |

So inside Hex, "running another sequence" always means pushing frames onto the one continuation of one
VM, sharing one `CastingImage` and one `CastingEnvironment`.

**The only multi-program structure in Hex is the spell circle** [V] (`api/casting/circles/CircleExecutionState.java`,
`common/blocks/circles/BlockSlate.java:79-101`)

- Each slate runs its pattern in a **new** `CastingVM(currentImage, env)`. The resulting image is carried
  to the next slate, and `withOverriddenUsedOps(0)` resets the op count per slate
  (`CircleExecutionState.java:286`).
- Exactly one valid exit is allowed; more than one halts the circle with "many exits".
- One slate runs every `max(2, 10 - n/3)` ticks, and the whole state (image included) is saved to NBT.

This is the closest upstream precedent to a weave: separate VMs, state passed along links, yield points
between nodes. It has no fan-out.

**Media** [V]

- `SpellAction.operate` (`castables/SpellAction.kt:38-72`) simulates `env.extractMedia(cost, true)`
  *before* returning, throws `MishapNotEnoughMedia` if the cost can't be paid, and queues `ConsumeMedia`
  and `AttemptSpell` side effects.
- `ConsumeMedia.performEffect` extracts for real and discards any unpaid remainder
  (`sideeffects/OperatorSideEffect.kt:43-47`).
- `AttemptSpell` calls `RenderedSpell.cast(env, image)`, which may replace the image.
- Stack-only actions cost no media.

**Mishaps** [V]

- `DoMishap.performEffect` sprays particles and runs `mishap.executeReturnStack(env, ctx, stack)`
  (`OperatorSideEffect.kt:56-71`), which may push Garbage, drown the caster (`MishapEvalTooMuch`), hurt
  them, and so on through `env.getMishapEnvironment()`.
- `PlayerBasedCastEnv.postExecution` also messages the player (`PlayerBasedCastEnv.java:84-102`).

**Limits** [V]

- `maxOpCount()`: default 100 000 (`HexConfig.java:84`). It is overridable per env (Hexwright's
  `FlightCastingEnvironment.maxOpCount()` overrides it) and checked after every action.
- Stack: `MAX_SERIALIZATION_TOTAL` 1024 iotas, `MAX_SERIALIZATION_DEPTH` 256 (`HexIotaTypes.java:25-26`),
  checked after every VM step.
- Circles: `maxSpellCircleLength` 1024.
- **Nothing in Hex limits how many VMs a mod creates.** Guarding that is the caller's job.

**Image state** [V] (`vm/CastingImage.kt`)

- Fields: `stack`, `parenCount`, `parenthesized`, `escapeNext`, `opsConsumed`, `userData`.
- `userData` holds the ravenmind (`hexcasting:ravenmind`) and Impulse's "already moved this cast" marks
  (`hexcasting:impulsed`, used for Impulse's surcharge in `OpAddMotion.kt:33`).
- Write images as `new CastingImage().copy(...)`. That form works whether or not the constructor is
  public, and it is what `WardingBoxCasting` and `StaffPowers` already do. (0.11.4's six-argument
  constructor is in fact public.)

**Environments hold per-instance state** [V]

- `CastingEnvironment` itself holds `costModifier` (set per action), the component lists added through
  `addExtension`, and the `createEventTriggered` flag.
- `PlayerBasedCastEnv` caches ambit and sentinel radii, and `PackagedItemCastEnv` accumulates a sound.
- Hexwright's own `RegionBatch` is keyed to an env instance.
- So an env is safe to use for one VM run. Reusing one across VMs is not something Hex promises.

**Serialisation** [V]

- `IotaType.serialize` produces a typed `{hexcasting:type, hexcasting:data}` tag. An oversized iota
  becomes `GarbageIota` (`IotaType.java:56-75`).
- `IotaType.deserialize(tag, level)` turns an unknown type into Garbage and an entity that is no longer
  in *that level* into `NullIota` (`EntityIota.java:74-86`).
- `CastingImage.serializeToNbt/loadFromNbt` and `SpellContinuation.serializeToNBT/fromNBT` round-trip
  images and continuations. A frame type that isn't registered becomes an empty `FrameEvaluate`.
- `HexPattern.serializeToNBT` is `{start_dir: byte, angles: byte[]}` and needs no level.
- `ResolvedPattern` is `{Pattern, OriginQ, OriginR, Valid}`: the pattern plus its grid origin. It is the
  format Hex already uses to persist a player's staff grid (`IXplatAbstractions.setPatterns`).

**Resuming mid-program is possible with public API** [V]
`new CastingVM(savedImage, env).queueExecuteAndWrapIota(new ContinuationIota(savedContinuation), level)`
continues a saved continuation. `ContinuationIota.execute` returns the saved continuation as the next
one. The one caveat is that at `parenCount > 0` the VM would escape the iota instead of executing it
(in 0.11.4, via `Iota.executeInParens`, whose default adds the iota to the open list).
This keeps future suspension open without mixins.

#### Hex Casting API surface used

Everything the recommended design touches is already used by Hexwright against 0.11.4 [V]:

- `new CastingVM(image, env)`, `queueExecuteAndWrapIotas`, `vm.getImage()` (`FlightRunner.java:96`);
- `CastingImage.copy(...)`, `getOpsConsumed()` (`FrameRegionForEach.java:148-151`);
- `ExecutionClientView` (`HexwrightNetworking.java:574`);
- `CastingEnvironment.maxOpCount()` (`FlightCastingEnvironment.java:282`);
- `addExtension` (`StaffCastEnvMixin.java:37`);
- `IotaType.serialize/deserialize` (`HarmonicDelivery.java:92,173`);
- `HexUtils.coordToPx`, `HexPattern.toLines` (`CloakFieldWard.java:265,289`).

The recommended design needs **no** new mixin, no custom `ContinuationFrame` and no reflection.

### Hexwright

**Pattern registration** [V]

- Each feature has a `*Actions.register()` that registers `ActionRegistryEntry`s through
  `IXplatAbstractions.INSTANCE.getActionRegistry()`, for example `server/hexpatterns/HexwrightActions.java:68`
  and `server/region/RegionActions.java:77`.
- These are called from `inits/HexwrightServer.kt:39-65`.
- Actions use `HexwrightSpellAction` / `HexwrightConstMediaAction`, Java shims over the Kotlin default
  `operate`.
- A weave needs no new action for Phase 2 (see [Egress](#payload-semantics)).

**Hexwright already starts hexes programmatically in eleven places, all the same way** [V]

| Site | Env | Seed |
|---|---|---|
| `server/talisman/TalismanCasting.java:239-248` | `TalismanCastEnv` (extends `PocketCasterCastEnv` extends `StaffCastEnv`) | context iota |
| `server/wardingbox/WardingBoxCasting.java:69-80` (the Hex Ward) | `WardingBoxCastEnv` (casterless, own reservoir) | visitor entity |
| `server/harmonic/HarmonicDelivery.java:173-184` (Resonant Ring) | `ResonantRingCastEnv` | payload |
| `server/harmonic/HarmonicEmitterCasting.java:64` | `HarmonicEmitterCastEnv` (casterless) | payload |
| `server/staff_assembly/StaffPowers.java:142-157` | `StaffPowerCastEnv` + `AreaCastRangeComponent` | scan result |
| `server/weapon/WeaponHexCasting.java:140` | `WeaponHexCastEnv` + range component | impact |
| `server/pocketcaster/PocketCasterCasting.java:152` | `PocketCasterCastEnv` | |
| `server/reliquary/SatchelCasting.java:76` | `ChestCastEnv` (casterless, borrows opener) | |
| `server/dust/DustManifestations.java:226-232` | `PocketCasterCastEnv`, stored image, end of tick | |
| `server/vehicle/FlightRunner.java:78-79` | `FlightCastingEnvironment` | reads the result stack |
| `server/region/RegionThothBenchmark.java:292` | benchmark | |

The shared idiom is: `StoredHex.decode(iota)` to a `List<Iota>`; a fresh
`new CastingImage().copy(List.of(seed), 0, List.of(), false, 0L, new CompoundTag())`; a new env; then
`new CastingVM(image, env).queueExecuteAndWrapIotas(hex, level)` inside `try/catch RuntimeException`.
The memory note "triggered casts need a fresh image" records why: reusing a staffcast image leaked
stack state between firings. **A weave Thread invocation is this idiom with a different seed** [I].

**Reusable pieces** [V]

- `server/hexpatterns/StoredHex.java`: the one reading of "a stored hex" (a list, or a bare pattern).
- `server/harmonic/HarmonicDelivery.java`: the closest existing design to what an Egress needs:
  - an envelope queue drained at END_SERVER_TICK;
  - `MAX_DEPTH` 4, `MAX_ACTIVATIONS_PER_TICK` 64, `MAX_QUEUED` 4096;
  - the payload serialised once and deserialised **per recipient** ("no cross-talk");
  - targets re-validated when opened.

  It is the Resonant network's delivery path and must not be reused or altered for weaves (invariant 9),
  but its guard rails are the model.
- `TalismanCasting.castDepth`: a static re-entrancy guard, so a talisman cast never fires talisman
  triggers.
- `server/region/FrameRegionForEach.java`: Hexwright's one custom `ContinuationFrame`, registered in
  `RegionActions.java:110-111`. Memory `region_thoth_not_a_frameforeach` records that several addon
  meta-eval patterns (Overevaluate, Ephemera, Lapisworks) mishap or misbehave on frames that aren't
  `FrameForEach`. That is concrete evidence that custom frames carry ecosystem risk.
- Env extensions (`CastingEnvironmentComponent`, Hex's official hook): `ResonantFieldRangeComponent`,
  `AreaCastRangeComponent`, `RegionBatch.Hook`, and `MediaGrants` (installed on every env from
  `CastingEnvironmentMediaGrantMixin`). Grant pools live on the entity or block, **not** the env, so
  several envs share one pool [V].
- `FlightCastingEnvironment.hasMishap()`: detects mishaps from `postExecution` side effects, which is
  more thorough than the returned resolution type.
- Serialisation precedents: `FrameRegionForEach` NBT (PascalCase keys); `PowerOrbData` (typed iota tags,
  see memory `iota_holder_tag_must_be_typed`); `server/hexicon/HexiconSavedData.java` (off-stack
  storage keyed by a UUID on the stack, with a revision bump so container sync notices changes).
- Future host: Power Orbs (`server/powerorb/`, `PowerOrbItem`, `PowerOrbSlot`, a 1-deep `power_orb`
  Accessories slot) are the "Artifact Power Cores" that Woven Orbs imitate.
- Client: `client/spellcasting/PatternDrawBatch.java` batches a whole grid into one draw call (a port of
  `RenderLib.drawPatternFromPoints`); `PatternGeometryCache`; `mixin/GuiSpellcastingGridBatchMixin`,
  `GuiSpellcastingAccessor` and `AugurStrokeGhostOverlay` already hook the staff grid;
  `client/cloak/CloakFieldWard.java` draws Hex patterns in world space with `HexUtils.coordToPx`.
- Logging goes through `HexwrightDebug`. A weave needs a new `WEAVE` area, per `AGENTS.md`.

### Reference mods

- **hexcassettes** (`data/QueuedHex.kt`, `actions/OpEnqueue.kt`) stores a queued hex as NBT with a delay
  and a lineage `depth` (capped at 10). Each run is `CastingVM.empty(new CassetteCastEnv(...))`: a fresh
  image every time.
- **Hexal wisps** (`api/casting/wisp/WispCastingManager.kt`) keep a per-player priority queue of
  `WispCast(hex, initialStack, initialRavenmind)`, all serialised. The queue is persisted, runs 10 casts
  per tick, and gives each cast a fresh `CastingImage` and a new `WispCastEnv`. Links (`ILinkable`) pass
  single iotas into a bounded (64) receive queue.
- **Hexal `MixinCastingVM`** intercepts `queueExecuteAndWrapIota` (singular) for `StaffCastEnv`
  (Everbook macros, transmission) and modifies a variable in the plural method. A weave calls the plural
  entry point, and its effect there is limited to sounds when a macro flag is in `userData`, which a
  fresh image never has [V].
- **hexical** lamps and charms: `CastingVM(CastingImage(), env)` per use.

Every addon that runs hexes on the player's behalf chose separate VMs with fresh images. None of them
nests one program inside another's continuation.

---

## Recommended execution architecture

**Recommendation: approach B with a local worklist.** Each Thread invocation is an ordinary triggered
cast: a new `CastingEnvironment` from the host, a fresh `CastingImage` seeded with the Ingress payload,
and its own `CastingVM`. Egress events are collected, not executed inline, and run
**synchronously, breadth-first, after the upstream VM has returned**, inside the one `activate` call.
There is no custom frame, no mixin and no global queue.

### Approaches compared

| | A. Same VM (splice frames) | B. New VM per Thread (recommended) | C. Custom Thread frame | D. Deferred queue (end of tick / next tick) |
|---|---|---|---|---|
| Stack | shared; needs manual save/restore | fresh per invocation | frame saves/restores (Thoth-like) | fresh per invocation |
| Ravenmind / `userData` | shared across Threads | per Thread | shared | per Thread |
| Media | one env | one host purse, one env per Thread | one env | one host purse, host must still exist |
| Mishap | ends the **whole** VM (`earlyExit`) | ends that Thread only | ends the whole VM; frames cannot catch | ends that Thread only |
| Recursion | op budget only | worklist; no Java recursion | op budget only | queue depth |
| Tick | all at once | all at once, bounded | all at once | spread across ticks |
| Independence | none | full, apart from world state and purse | none | full |
| Suspension later | continuation is serialisable | between Threads: trivially (NBT worklist); mid-Thread: via `ContinuationIota` resume | serialisable, but ours alone | natural |
| API stability | public API but reaches into control flow | only calls Hexwright already makes | custom frame breaks addon meta-eval patterns | public API |

**Why not A or C.**

- The VM stops at the first unsuccessful step (`CastingVM.kt:81`). No frame can catch a mishap, so one
  failing branch would kill its siblings.
- Iris' Gambit captures the *whole* continuation, so a Thread could jump into its siblings' pending
  frames, which breaks invariant 7.
- Charon's Gambit would need a Thread boundary frame to stop it.
- The stack, ravenmind and Impulse marks would leak between Threads.
- Addon meta-eval patterns already misbehave on Hexwright's one custom frame.

**Why not D now.** Some hosts need the weave's effects applied before they continue. Talisman of
Reprieve heals, runs the hex and then checks health (`TalismanCasting.onBrink`). A deferred Egress
would run after the host has already decided. Deferral also needs a static queue, re-validation of the
host (HarmonicDelivery re-resolves rings and emitters) and a policy for server shutdown. The worklist
entry below is already pure data, so moving to D later is a scheduling change, not a semantic one [I].

**E. Spell-circle style (image carried along each link).** This is the upstream precedent, but it passes
the whole image and permits only a single exit. That conflicts with invariant 4 and with the payload
recommendation below.

### Mechanics

> **Phase 2 note.** This is the Phase 1 sketch, kept for the reasoning. The implementation is
> `server/hexweave/HexweaveCasting.java`, and it differs in three ways: there is **no** `activating`
> guard (the user ruled out any global "one weave at a time" rule, see
> [Phase 2](#phase-2-implementation-status)); a small `PostExecution` component detects mishaps, because
> reading the image alone can't tell a budget overrun apart from an ordinary mishap; and the op count
> carries forward as `max(before, after)`.

```java
/** Runs a weave for one activation by its host. */
public static void activate(Hexweave weave, ServerLevel level,
                            Supplier<CastingEnvironment> environment, @Nullable Iota seed) {
    if (activating) {
        return;                                   // re-entrancy, like TalismanCasting.castDepth
    }
    activating = true;
    try {
        Deque<Invocation> pending = new ArrayDeque<>();
        CompoundTag seedTag = seed == null ? null : IotaType.serialize(seed);
        for (WeaveThread root : weave.roots()) {  // id order
            pending.add(new Invocation(root.id(), seedTag));
        }
        long opsUsed = 0;
        int runs = 0;
        while (!pending.isEmpty() && runs++ < MAX_RUNS) {
            Invocation next = pending.poll();
            WeaveThread thread = weave.thread(next.threadId());
            CastingEnvironment env = environment.get();
            if (opsUsed >= env.maxOpCount()) {
                break;
            }
            List<Iota> stack = next.payload() == null
                ? List.of()
                : List.of(IotaType.deserialize(next.payload().copy(), level));
            CastingImage image = new CastingImage()
                .copy(stack, 0, List.of(), false, opsUsed, new CompoundTag());
            CastingVM vm = new CastingVM(image, env);
            ExecutionClientView view = vm.queueExecuteAndWrapIotas(thread.program(), level);
            opsUsed = vm.getImage().getOpsConsumed();
            Iota out = egressPayload(thread, view, vm.getImage());
            if (out != null) {
                CompoundTag payload = IotaType.serialize(out);
                for (int target : weave.targetsOf(thread.id())) {  // id order
                    pending.add(new Invocation(target, payload));
                }
            }
        }
    } finally {
        activating = false;
    }
}
```

The real code would also wrap each VM run in `try/catch RuntimeException`, as every existing site does,
and log through `HexwrightDebug` under a new `WEAVE` area.

- **Egress detection needs no pattern and no env hook.** The executor reads the finished image
  (`vm.getImage()`), exactly as `FlightRunner` reads its output.
- **`Invocation` is `(int threadId, @Nullable CompoundTag payload)`**: no runtime references (D3), so it
  can later be persisted for deferral or suspension.
- **A new env per invocation.** The host supplies a factory, not an instance, because envs carry
  per-cast state and hosts decorate them (for example `WeaponHexCasting` adds an
  `AreaCastRangeComponent`).

### What the host must provide (root execution semantics)

The minimum is `ServerLevel` + `Supplier<CastingEnvironment>` + an optional seed `Iota`. That is all
[I]. The caster, origin, ambit, media source, pigment, mishap target, sounds and visuals all already
live in the host's `CastingEnvironment` subclass. Hexwright has one per host (see the table above), and
a future orb would get one too, almost certainly a `PocketCasterCastEnv` subclass like
`TalismanCastEnv`. The seed plays the role of today's context iota (the entity that entered the ward,
the Resonant payload, the talisman context). No `Hexweave`-specific host interface is needed.

---

## Proposed data model

In memory (Phase 2):

```java
public record Hexweave(List<WeaveThread> threads, List<WeaveLink> links) { /* roots(), thread(id), targetsOf(id) */ }
public record WeaveThread(int id, boolean ingress, boolean egress, List<HexPattern> patterns) { /* program() */ }
public record WeaveLink(int from, int to) { }
```

`WeaveThread`, not `Thread`, to avoid colliding with `java.lang.Thread`.

**Ingress and Egress are properties of a Thread, not iotas in its body.** They are the armatures drawn
at the edge of the Thread's area. This makes "at most one of each" true by construction. If they were
patterns inside the body, the rule would need validating against escaped copies, embedded lists and
Hermes'd data, and there would still be no sound answer for an Egress reached inside a Thoth.

Stored (Phase 3, sketch; the implemented Version 1 is under
[Phase 3](#version-1-stored-structure). Phase 6 added the layout, but as a separate top-level
`Layout` compound rather than per Thread; see [Layout](#layout-in-the-stored-weave)):

```
Hexweave                      CompoundTag, one key on the ItemStack or block entity
  Version: int                1
  Threads: [
    { Id: int, Ingress: byte, Egress: byte,
      Patterns: [ { Pattern: {start_dir, angles}, OriginQ: int, OriginR: int } ],   // ResolvedPattern's keys, minus Valid
      Layout: { X: float, Y: float, Depth: int } }                                 // optional, Loom only
  ]
  Links: [ { From: int, To: int } ]
```

- **Program order is list order** (draw or insert order). Geometry is where a pattern sits, not when it
  runs. `program()` maps patterns to `PatternIota`.
- **Stable ids** are small ints unique within the weave. Links reference ids, not list indices. A new
  Thread takes `max + 1`; deleting a Thread deletes its links.
- **Validation on load and save**: unique ids; a link's source has an Egress and its target has an
  Ingress; no duplicate links; pattern positions inside the Thread's bound and not overlapping (the same
  union-of-`positions(origin)` check `StaffCastEnv.handleNewPatternOnServer` uses against cheating);
  hard caps on Thread count and patterns per Thread.
- **Versioning**: an unknown `Version` refuses to run and logs a `warn` (a broken save record, per
  `AGENTS.md`). Migrations are version-to-version upgrade functions.
- **Why not an iota.** A `HexweaveIota`, or bodies stored as typed `ListIota`s, would lose the origins
  the Loom needs, would need a `ServerLevel` to decode on the client (`IotaType.deserialize` takes one,
  `HexPattern.fromNBT` does not), and would invite in-hex weave manipulation: a new data language. Raw
  pattern NBT is compact and level-free.
- **Size and sync**: ItemStack NBT syncs whole to clients. Start on-stack. If weaves grow large, the
  Hexicon's off-stack `SavedData` pattern already exists (with its revision-bump caveat, memory
  `offstack_iota_storage_breaks_container_sync`).
- Thread bodies are **patterns only**. The Loom draws patterns. Importing hexes with embedded non-pattern
  iotas is an open question, not a Phase 2 concern.

---

## Payload semantics

**Egress carries exactly one iota: the top of the Thread's stack when it finishes. The downstream
Thread's stack starts as that iota alone.** The rest of the upstream stack is discarded.

- **Hexwright convention.** Every triggered hex in the mod already starts with a single seeded iota:
  the ward visitor, the talisman context, the Resonant payload. Ingress becomes "the thing you were
  handed", which players already know.
- **Composition.** Several values travel as a list. Hex already has the list patterns to build and
  unpack one. Downstream Threads never depend on the upstream stack layout.
- **Fan-out.** Serialise once at the Egress and deserialise per recipient, as `HarmonicDelivery` does.
  Every downstream gets an independent copy, and mutable payloads such as `StackIota` (whose payload is
  a live `ItemStack`) can't alias. The size rules are enforced for free: an oversized payload becomes
  Garbage.
- **Entity iotas** re-resolve in the host level on each deserialise. An entity that has gone becomes
  Null [V].
- **Empty stack at the end means no Egress fires.** This makes Charon's Gambit at top level ("clear the
  stack and stop", `OpHalt`) the natural "stop this branch" idiom, with no new pattern.
- **A mishap means no Egress fires.** The same applies if the Thread ends with an unclosed Introspection
  (`parenCount > 0`), because its stack is not its intended output.
- **Ravenmind is not carried.** It is Thread-local scratch space. Shared state travels in the payload.

Rejected alternatives: passing the whole stack couples Threads to each other's stack layout and
multiplies copying under fan-out; wrapping the whole stack in a list makes the common case (hand over
one entity) awkward; an explicit Egress pattern with an argument is covered under Open questions.

---

## Execution semantics

**Root.** On activation every Thread without an Ingress runs, in id order, each with its own copy of the
host seed (or an empty stack if the host gives none). A Thread with neither Ingress nor Egress is a root
that emits nothing.

**One-to-one (A → B).** A finishes; if its Egress fires, B runs next in a new VM with `[payload]`.

**One-to-many (A → B, C, D).** [I, with each mechanism V]

- Each of B, C and D gets its own copy of the payload, its own VM, image and env.
- They run in **deterministic FIFO order**: B, C, D by id, then their own downstreams. The whole tier
  runs before its children, and a later deferred queue would keep the same order.
- A mishap in B ends B only (its env is B's). C and D still run. B's Egress does not fire.
- What the branches *do* share is the world (C sees what B changed), the host's media purse (B spending
  media leaves less for C) and, if the budget below is adopted, the op budget. All of these are
  real-world coupling, not hidden VM coupling.
- Mutable iotas are safe because payloads are copied. Shared-state mutation (an inventory, a block) is
  ordinary Hex behaviour, with deterministic ordering.

**Many-to-one (A, B, C → D).** Every arrival is its own invocation. If A and C both reach D in one
activation, D runs twice, each time with its own stack. There is no dedup or JOIN. Re-entrancy is safe
because all execution state lives in the VM for the duration of one call, and the weave is an immutable
snapshot taken at activation. A future suspended D is just another pending invocation record.

### Loops and runaway execution

What Hex already provides [V]:

- per-VM `maxOpCount` (100 000, `MishapEvalTooMuch`, which drowns the caster);
- stack size limits;
- media, for spells only; stack-only patterns are free;
- **nothing** that limits the number of VMs.

Threads with no spells in them are therefore free, and a cycle of them is unbounded unless we bound it.

Fan-out makes this worse even **without** cycles: a chain of k diamonds (A → B, C → D → E, F → G ...)
runs the last Thread 2^k times. So cycle detection alone is not enough, and a budget is mandatory
whatever loop policy is chosen [I].

Protections for Phase 2:

1. **An invocation cap** per activation (for example 64, the same as `HarmonicDelivery`'s per-tick cap).
   Past it, pending invocations are dropped and logged.
2. **A shared op budget** per activation. Each Thread's image starts at the activation's running
   `opsConsumed`, so a whole activation evaluates no more than one ordinary hex could. Runaway ends in
   Hex's own `MishapEvalTooMuch`, and Thanatos' Reflection reports the budget left for the whole weave.
   Once the budget is spent, the activation stops, so later Threads don't each drown the caster again.
3. **No Java recursion.** The worklist never nests VMs.
4. ~~**A re-entrancy guard.**~~ **(dropped in Phase 2)** Hosts must eventually be able to trigger one
   another (Hex Ward → Woven Orb → Resonant message → Resonant Ring → another orb), so there is no global
   "one activation at a time" rule. No Phase 2 path can nest an activation. When hosts arrive, a nested
   activation should inherit its parent's lineage (remaining budget and depth) rather than be refused;
   see [Phase 3 recommendation](#phase-3-recommendation).

Loop policy (illegal / budgeted / useful) can then be decided later. The Loom can warn about cycles at
authoring time either way.

---

## Known risks

| Risk | Detail | Mitigation |
|---|---|---|
| Source drift between the traced checkout and 0.11.4 | The checkout is between 0.11.3 and 0.11.4 (see Source note). | **Resolved in Phase 2**: checked against the real 0.11.4, see [0.11.4 verification](#0114-verification). |
| Custom frames and continuation internals | Addon meta-eval patterns assume `FrameForEach` (memory `region_thoth_not_a_frameforeach`); `SpellContinuation` is a sealed Kotlin interface. | The recommended design uses none. |
| Tick cost | Synchronous execution puts the whole activation in the triggering tick. | Shared op budget plus invocation cap. Move to deferral only if profiling says so. |
| Per-invocation op budgets (if the shared budget is rejected) | 64 × 100 000 ops in one tick. | Keep the shared budget, or cap much lower. |
| Overcast | Player-based envs in the `StaffCastEnv` lineage can overcast into health. Fan-out multiplies that. | Host's choice of env, as today. Document it for players. |
| Mishap and chat spam | `PlayerBasedCastEnv` messages each mishap, so fan-out can repeat one. | Acceptable. Could be coalesced later. |
| Impulse surcharge reset | The `hexcasting:impulsed` marks live in `userData`, so they reset per Thread. Impulsing the same entity from sibling Threads skips the +1 surcharge. | Minor. It is the same leak as two separate casts. Could carry the marks if it matters. |
| Mishap detection | `ExecutionClientView.getResolutionType()` misses a `MishapInternalException` raised by a side effect (the VM loop continues). | **Phase 2**: a `PostExecution` component is attached; see [what counts as success](#what-counts-as-a-thread-completing-successfully). The side-effect case is still invisible to it, deliberately. |
| Host disappears mid-activation | The ward is broken by Thread B's explosion; the player dies in Thread B. | The env factory can return null to stop; hosts already re-validate (`CircleExecutionState` checks `getImpetus() == null` after each slate). |
| `ContinuationIota` as a payload | Executing it downstream runs the rest of the upstream Thread inside the downstream VM. | Contained: same budget, no access to weave scheduling. Refuse it at Egress if that feels wrong. |
| Name clash | `Thread` vs `java.lang.Thread`. | `WeaveThread`. |
| Staff-env visuals and sound | A host env that isn't muted (a plain `StaffCastEnv`) broadcasts spiral glyphs per Thread. | Hosts use `PocketCasterCastEnv`-family envs, as every triggered host already does (memory `pocketcastercastenv_postcast_visual_leak`). |

---

## GUI integration findings

How the staff grid works [V] (`client/gui/GuiSpellcasting.kt`, `api/utils/HexUtils.kt:86-100`,
`client/render/RenderLib.kt`):

- **Storage.**
  - Client: `patterns: MutableList<ResolvedPattern>` plus `usedSpots: Set<HexCoord>`.
  - Server: each pattern is executed **live** as it is drawn (`drawEnd` sends `MsgNewSpellPatternC2S` to
    `StaffCastEnv.handleNewPatternOnServer`), and the grid is persisted per player through
    `IXplatAbstractions.setPatterns` and restored by `MsgOpenSpellGuiS2C`.
- **Coordinates.**
  - Axial `HexCoord(q, r)`. A pattern is `HexPattern(startDir, angles)` placed at an origin;
    `positions(origin)` gives every dot it visits.
  - `coordToPx(coord, size, offset)` and `pxToCoord(px, size, offset)` are public top-level functions
    that take any size and offset.
- **Camera.** None.
  - `coordsOffset()` is fixed to the screen centre.
  - `hexSize()` is `sqrt(w·h/512) / GRID_ZOOM attribute`.
  - The scroll wheel goes to `ShiftScrollListener` (staff item switching), not zoom.
- **Rendering.**
  - `RenderLib.drawPatternFromPoints`, `drawSpot`, `findDupIndices` and `HexPattern.toLines` are all
    public.
  - Line widths are fixed pixels (5 and 2), so zoom by scaling the `PoseStack` rather than shrinking
    `hexSize`, or zoomed-out lines crowd [I].
  - Hexwright's `PatternDrawBatch` already batches a whole grid into one draw call, which is exactly
    what a zoomed-out weave with many Threads needs.
- **Validation.**
  - Client: no drawing over used dots.
  - Drawing: `HexPattern.tryAppendDir` refuses to retrace an edge.
  - Server: rejects any new pattern that overlaps earlier ones.
  - What a pattern *means* is only resolved at execution (`PatternRegistryManifest`). Per-world great
    spells can't be named reliably on the client, and a Loom doesn't need to name them to author them.
- **Loading arbitrary coordinates.** Yes for Hex's own screen: its constructor takes `ResolvedPattern`s
  at any origin, although with no pan anything far from the centre is off-screen. Trivially yes for a
  screen we own.
- **Can it be embedded or subclassed? No.**
  - `GuiSpellcasting` is a final Kotlin class (no `open`).
  - `drawStart/drawMove/drawEnd` are private, and `drawEnd` always triggers live execution.
  - `tick()` closes the screen unless a staff is held.
  - Its geometry is tied to the screen size.

**Safest route [I]:** a Loom canvas of our own that reuses Hex's building blocks rather than its screen:

- `HexCoord`, `HexDir`, `HexAngle`, `HexPattern` (with `tryAppendDir` and `positions`);
- `coordToPx` and `pxToCoord` with per-Thread offset plus pan, and zoom via the pose matrix;
- a port of `drawMove`'s angle snapping (about 40 lines);
- `RenderLib` or `PatternDrawBatch` for strokes and dots;
- `PatternGeometryCache`.

Each Thread keeps its own local axial space centred on `(0,0)`. Its bound is a hex radius
(`HexCoord.distanceTo(Origin) <= R`, which is cheap and native to the grid). The Ingress and Egress
armatures sit on that boundary, and per-Thread layout plus depth are just a translate, scale or tint
per plane. Saving goes through a C2S packet that the server re-validates (bounds, overlap, retraced
edges, the Ingress/Egress/link rules). A plain `Screen` is probably easier than LDLib for a freeform
drag canvas: LDLib's `ModularUIGuiContainer` routes vanilla mouse handling only through `SlotWidget`
(memory `ldlib_vanilla_mouse_only_via_slotwidget`). Decide that at the GUI phase.

---

## Open questions

> **All six were decided for Phase 2, as recommended** (Egress on completion carrying the top iota; an
> empty stack emits nothing; roots get their own copy of the host seed; one shared op budget; cycles
> allowed but bounded; list order). They are kept below for the reasoning. Questions that are still
> open are under [Phase 2: implementation status](#open-questions-after-phase-2).

Only questions that change behaviour players will see. The recommendation comes first in each.

1. **When does Egress fire?** Recommended: when the Thread *finishes*, carrying the top of the stack
   (structural armature, and Charon's Gambit means "don't emit"). The alternative is an executable
   Egress pattern that can emit mid-Thread, conditionally or repeatedly, with a counting problem for
   "at most one".
2. **An empty stack at the end:** no Egress (recommended), or Egress with Null?
3. **Root seed:** do roots receive the host's activation iota (recommended; matches every current host),
   or always start empty?
4. **Op budget:** one budget for the whole activation (recommended: a weave is "one hex's worth" of
   evaluation, so structure costs no extra compute), or a full budget per Thread?
5. **Loops:** allowed but bounded by the budget and cap (recommended for now), or refused by the Loom?
6. **Program order in the Loom:** list or draw order (recommended), or derived from geometry (the spiral
   from Ingress outwards), where moving a pattern would reorder the program?

---

## Phase 2 recommendation

> Done; see [Phase 2: implementation status](#phase-2-implementation-status). One change from this plan:
> no re-entrancy guard.

**Smallest step: an in-memory weave plus its executor, and a dev-only command to run one. No NBT, no
item, no GUI, no triggers, no host changes.** Estimated effort is small: three small classes and a
command, with no mixins and nothing touched that already exists except `HexwrightDebug` and command
registration.

1. Diff the 0.11.4 sources jar against the traced checkout for `CastingVM`, `PatternIota`,
   `CastingImage` and `OpHalt`.
2. `server/hexweave/Hexweave.java`: records `Hexweave`, `WeaveThread` (patterns only) and `WeaveLink`,
   plus `roots()`, `thread(id)`, `targetsOf(id)` and construction-time validation.
3. `server/hexweave/HexweaveCasting.java`: `activate(Hexweave, ServerLevel, Supplier<CastingEnvironment>,
   @Nullable Iota seed)` as sketched above, with the FIFO worklist, per-invocation VM, image and env,
   serialise-per-recipient payloads, a shared op budget, an invocation cap, a re-entrancy guard, and
   `try/catch` per run.
4. `HexwrightDebug`: add a `WEAVE` area. Log every run (thread id, payload display, success,
   `opsConsumed`) behind `HexwrightDebug.on(WEAVE)`.
5. A dev-only, op-level command, for example `/hexwright weave run 1>2,1>3,2>4,3>4`.
   - Thread *n* is the hex in hotbar slot *n* (read through `ADIotaHolder` and `StoredHex.decode`;
     non-pattern iotas are refused).
   - Ingress and Egress are inferred from the links.
   - It runs as the player with `PocketCasterCastEnv`, seeded with the player's `EntityIota`.

**Done when** each of these is observed in game with debug logging:

- a single Thread with no links behaves exactly like a Pocket Caster fire (D2);
- in A → B, B starts with only A's top iota;
- in A → B, C, B and C run in order with independent payloads, and a mishap in B leaves C running and
  B's downstream silent;
- in A, C → D, D runs twice;
- an empty stack or Charon's Gambit at the end of A stops propagation;
- a cycle `1>1` stops at the budget or cap with no crash and no noticeable lag.

The Phase 2 results then settle Open questions 1–4 before the NBT format (Phase 3), which is the step
that fixes the stored shape for Woven Orbs and the Loom.

---

## Phase 2: implementation status

Implemented 2026-09-28. This is a proof of the execution model, not something players can use: there is
no item, no persistence, no Loom, no trigger, and no existing host (Hex Ward, Resonant Ring, Harmonic
network) was touched.

### 0.11.4 verification

The Kotlin classes were checked by `javap -c` against the Mojmap-remapped 0.11.4 binary, because the
published sources jar has no Kotlin. The Java classes were checked against the remapped 0.11.4 sources
(see the Source note at the top).

| Area | 0.11.4 vs the Phase 1 trace | Effect on the design |
|---|---|---|
| `CastingVM` loop | Same order: evaluate frame, stack-size check, set image, `postExecution`, then side effects (exceptions become `MishapInternalException`); early exit on the first unsuccessful step; `postCast` once; start state untouched (no op reset). **New:** the op-budget check lives here, after every step, for every frame type. An overrun becomes `DoMishap(MishapEvalTooMuch)`, `ERRORED`, `newData = null`. | The shared budget works as designed: an image seeded with `opsConsumed = N` is checked against `N + work`. Because the overrunning step's image is discarded, the VM can end a little *below* the maximum after an overrun, so the executor detects `MishapEvalTooMuch` directly instead of comparing op counts. |
| `CastingVM.executeInner` | No `handleParentheses`. A pending Consideration is handled here; open lists go through `Iota.executeInParens`; the parenthesis patterns are actions with `operateInParens`. | None. Thread bodies are plain pattern lists. |
| `PatternIota` | `execute` and `executeInParens` both go to `lookupAndOperate`. The op check is no longer here. A mishap inside a metacast now closes open parens. Mishaps are still caught and turned into `DoMishap` with the mishap's resolution type. | None. |
| Mishap resolution types | `ERRORED` by default; `INVALID` for `MishapInvalidPattern`, `MishapDisallowedSpell` and `MishapUnenlightened`. None successful. | An unsuccessful final resolution always means a mishap stopped the Thread. |
| `CastingImage` | The same six fields; the constructor is public; `copy`, `withUsedOp`, `withOverriddenUsedOps` unchanged. | None. |
| `OpHalt` (Charon's Gambit) | Unchanged: breaks frames downward, and clears the stack if nothing stops it. | Top-level Charon's Gambit ends a Thread with an empty stack, so its Egress stays silent (Test F). |
| `SpellAction` / `ConstMediaAction` | Unchanged: simulate media before returning, queue `ConsumeMedia`, and add `opCount` (normally 1) to `opsConsumed`. | Every ordinary pattern costs at least one op; Thanatos' Reflection reads `maxOpCount - opsConsumed`. |
| `CastingEnvironment` | `maxOpCount()` still public and overridable; `postExecution` feeds `PostExecution` components; `costModifier` is now computed from a `ResourceLocation` plus config scaling. `StaffCastEnv`, `PlayerBasedSpiralPatternCastEnv` and `PlayerBasedCastEnv` all chain to `super.postExecution`. | The mishap watch sees every step on every Hexwright env. |
| `IotaType` / `EntityIota` / `ContinuationIota` | Serialise and deserialise unchanged (oversize becomes Garbage; an entity missing from the level becomes Null); `ContinuationIota.execute` unchanged. | Payload semantics as designed. |

**Verdict: no Phase 1 assumption the implementation relies on was wrong.** Three Phase 1 statements
were inaccurate for 0.11.4 and are corrected in place above: where the op check lives, where
parenthesis handling lives, and that the `CastingImage` constructor is public.

### What was built

| File | What it is |
|---|---|
| `server/hexweave/WeaveThread.java` | Record: `id`, `ingress`, `egress`, ordered `List<HexPattern>`; `program()` returns the body as `PatternIota`s in list order. |
| `server/hexweave/WeaveLink.java` | Record: `from`, `to`. No ports, since a Thread has at most one of each armature. |
| `server/hexweave/Hexweave.java` | Immutable weave, validated on construction. Refuses a duplicate Thread id, a link naming a missing Thread, a link out of a Thread without an Egress, a link into a Thread without an Ingress, and a duplicate link. Provides `thread(id)`, `roots()` (id order) and `targetsOf(id)` (id order, precomputed). |
| `server/hexweave/HexweaveCasting.java` | The executor: `activate(Hexweave, ServerLevel, Supplier<? extends CastingEnvironment>, @Nullable Iota seed)` returns an `Activation` (list of `Run`s, why it stopped, ops used, invocations dropped). |
| `server/hexweave/HexweaveCommand.java` | `/hexwright weave run <graph>` and `/hexwright weave selftest`, creative and op level 2 like every `/hexwright` command. |
| `server/hexweave/HexweaveSelfTest.java` | Tests A–H plus validation against the real VM, using a Fabric `FakePlayer`. Runs from a dedicated server console. |
| `HexwrightDebug.java` | New `WEAVE` area. |
| `inits/HexwrightServer.kt` | Registers the command. |

No mixin, no custom `ContinuationFrame`, no new pattern, no reflection.

### How Thread execution works

1. The host seed is serialised once. Every root (no Ingress), in id order, is queued with that tag.
2. Invocations run off a local FIFO worklist. For each one:
   - the executor takes a **new environment** from the host's supplier and attaches a small
     `PostExecution` watch;
   - it deserialises a **fresh copy** of the payload tag (the tag is copied first, so an iota type that
     keeps its tag can't share it);
   - it builds a **fresh image** `new CastingImage().copy([payload] or [], 0, [], false, opsSoFar, {})`;
   - it runs a **new `CastingVM`** with `queueExecuteAndWrapIotas(thread.program(), level)`.
3. Once that VM has returned, the outcome is classified. If the Egress fires, the top iota is
   serialised once and one invocation per target (id order) goes on the **back** of the queue. So
   execution is breadth-first and deterministic, a self-link is a loop over the queue rather than a
   recursion, and the weave object is never mutated.
4. `RuntimeException`s are caught per invocation (outcome `CRASHED`, logged under WEAVE), so one broken
   Thread can't take down the rest of the activation or the host's event.

The dev command's environment is `new PocketCasterCastEnv(player, MAIN_HAND)`, the same class a Pocket
Caster fire uses. Its seed is `new EntityIota(player)`.

#### What counts as a Thread completing successfully

The first matching rule decides the outcome:

| Outcome | Condition | Egress |
|---|---|---|
| `CRASHED` | Java threw out of the invocation. | no |
| `OUT_OF_OPS` | The watch saw `DoMishap(MishapEvalTooMuch)`. **Ends the whole activation.** | no |
| `MISHAPPED` | The watch saw any `DoMishap` side effect, or the VM's final resolution was unsuccessful. | no |
| `UNFINISHED` | It ended with `parenCount > 0` (an open Introspection) or `escapeNext` (a dangling Consideration). | no |
| `COMPLETED` | Anything else. | fires if the Thread has an Egress **and** the final stack isn't empty; carries the top iota |

A spell whose side effect *throws* is not counted as a mishap. Hex performs that
`MishapInternalException` directly, without passing it through `postExecution`, and carries on
evaluating the rest of the hex. Hex itself treats the hex as continuing, and the weave follows Hex.

#### How the shared budget works

- `long ops` is local to one activation and starts at 0.
- Each invocation's image starts with `opsConsumed = ops`. Hex's own loop check
  (`opsConsumed > env.maxOpCount()`) then applies to the activation's total, and an overrun is Hex's
  ordinary `MishapEvalTooMuch`.
- After each invocation, `ops = max(ops, image.opsConsumed)`. The `max` keeps a pattern that resets its
  own image's op count from handing budget back to the Threads after it.
- Before each invocation, `ops >= env.maxOpCount()` stops the activation (`OP_BUDGET`). After an
  invocation that reports `OUT_OF_OPS`, it stops at once, because the VM's image can sit just below the
  maximum after an overrun and the next Thread would otherwise drown the caster again.
- The hard cap is `MAX_INVOCATIONS = 64` Thread runs. Nothing in the source argued for a different
  number. It is the only bound on Threads that spend no ops.

#### Re-entrancy

There is no global guard. All state is local to `activate`, so one weave's Thread may activate another
weave through a host. Nothing in Phase 2 can do that: the only entry point is a command. When hosts that
can chain arrive, the budget and a depth should travel with the activation (see Phase 3).

### Dev commands

- `/hexwright weave run <graph>`: Thread *n* is the hex in hotbar slot *n*.
  - `1>2,1>3,2>4,3>4` is a diamond, `1>2>3` a chain, and a bare `1` a single Thread with no links.
  - Ingress and Egress are inferred from the links.
  - A slot must hold a hex made only of patterns (an empty list is allowed).
  - Chat shows the first 12 runs; `/hexwright debug weave on` logs every run.
- `/hexwright weave selftest`: runs every check below and prints pass or fail for each.
  - It needs no player, so a dedicated server's console works.
  - It spawns a no-AI pig at world spawn and removes it afterwards.
  - It uses a `FakePlayer` as the caster.
  - The pig stands in for the player seed because a `FakePlayer` isn't in the level, so an entity iota
    naming it would deserialise to Null.

### Test results

Both runs used the dev dedicated server (`runServer`) against a throwaway world, with the real Hex
Casting 0.11.4 VM and a `FakePlayer` caster in a `PocketCasterCastEnv`. The first run had WEAVE logging
on; the second ran the self-test once with it on and once with it off. **All 39 checks passed in both
runs.**

| Test | Graph and program | Observed |
|---|---|---|
| **A** single Thread | `1`, body `[Gemini, Alidade's, Impulse]`, seed the pig | 1 root run, input a *new* entity iota for the same pig, `COMPLETED`, empty final stack (so it started with exactly the seed), 3 ops. Cost **1 dust**, identical to the same hex run the old way (`new CastingVM(seeded, new PocketCasterCastEnv(...))`). The Impulse moved the pig. |
| **B** A → B | `1>2`, A `[7, 3]`, B `[Flock's Reflection]` | A ended with 3 iotas and emitted only `3` to `[2]`. B's input was a fresh instance of `3`, and Flock's Reflection counted a stack of **1**. Each Thread got its own environment instance. |
| **C** fan-out | `1>2,1>3`, A `[3]`, B `[1, +]`, C `[Flock's]` | Order `[1, 2, 3]`. B and C each got a distinct instance of `3`; B computed 4 and C still saw a one-iota stack. **Mutable payload:** seed a Stack iota of 5 diamonds, relay it through `1>2,1>3`, shrink B's `ItemStack` to 2: C's, A's and the host's stay at 5. |
| **D** branch mishap | `1>2,1>3,2>4`, B `[+]` on a one-iota stack | Order `[1, 2, 3]`. B `MISHAPPED` (not enough arguments) and emitted nothing, so Thread 4 never ran. C `COMPLETED`. The activation `FINISHED`; nothing threw. |
| **E** many-to-one | `1>4,3>4`, A `[1]`, C `[2]`, D `[Flock's]` | Order `[1, 3, 4, 4]`. D ran from A with `1` and from C with `2`, each with its own one-iota stack. No dedup. |
| **F** empty stack | `1>2`, A `[Bookkeeper's v]`; then A `[5, Charon's Gambit]` | Both times A `COMPLETED` with an empty stack, logged `Egress suppressed (empty stack)`, and Thread 2 never ran. |
| **G** cycle | `1>1`; `1>2,2>2` with B `[1]`; the same with empty bodies | `1>1` alone has **no root**, so nothing starts it (0 runs). `1>2,2>2` stopped at the **cap: 64 runs, 1 dropped, 63 ops, 2.4 ms** with logging off (16 ms with it on). The Java stack depth at every VM step of all 64 runs was **constant (25 frames)**, so there is no recursion. The empty-body loop spent 0 ops and still stopped at the cap. Every stop was logged. |
| **H** shared budget | `1>2>3`, each `[Thanatos, 1, 1, 1]` (4 ops) with `maxOpCount = 10`; then `1>2,1>3` with B an endless Hermes loop at the default 100 000 | Thanatos read **10, 6, 2** in Threads 1, 2 and 3. Thread 3 went `OUT_OF_OPS` at op 11, the activation stopped with `OP_BUDGET`, and 10 ops were used, where separate budgets would have let all three finish. With the Hermes loop, B spent the whole 100 000 and C was dropped rather than getting a budget of its own. **176 ms for the weave against 165 ms for the same loop cast directly as one hex** (logging off). |
| **V** validation | `Hexweave.of` and the command parser | Duplicate id, missing Thread, link from a Thread without an Egress, link into a Thread without an Ingress, duplicate link (`1>2,1>2`), a non-numeric slot, an empty slot and a non-pattern iota were all refused with a readable message. |
| logging | the self-test with WEAVE off | 0 `[weave]` lines, against 248 with it on. |

**Still to check by hand in a client**, because a `FakePlayer` can't show them: the command with a real
player and real foci (the player's own entity iota as the seed), chat mishap messages, and sounds and
visuals (the Pocket Caster env is muted and draws no spiral). Suggested order: `1`, `1>2`, `1>2,1>3`,
`1>2,2>2`.

### Deviations from Phase 1

- **No re-entrancy guard**, by decision. See Re-entrancy above.
- **Egress detection uses an env component**, not only the finished image. Phase 1 said no env hook
  was needed; one is, to tell `MishapEvalTooMuch` apart from other mishaps (see the 0.11.4 table) and to
  catch `DoMishap` side effects that don't fail the step.
- **A cycle needs a root.** `A → A` gives A an Ingress, so A is not a root and nothing starts it. A loop
  is entered from a root, as in `1>2,2>2`. That is a safety property rather than a problem: a pure cycle
  can never run itself.
- Everything else (worklist, fresh image, env and VM per invocation, serialise-once and deserialise-per-
  recipient payloads, id ordering, root seeding, shared budget, cap) is as designed.

### New risks

| Risk | Detail |
|---|---|
| A single activation can take a noticeable slice of a tick | 100 000 ops took about 165–176 ms here, for a weave and for a plain hex alike. The shared budget keeps a weave to one hex's worth; per-Thread budgets would have allowed 64 times that in one activation. A weave on a frequent trigger (Hurt, Attack) would be exactly as heavy as a talisman carrying the same hex. |
| Entity payloads only survive if the entity is in the host's level | A payload is re-resolved by UUID in `level`. A `FakePlayer`, or an entity in another dimension, becomes Null. Hosts that cast as a fake or remote entity need to know that. |
| Mishaps can cast other things mid-Thread | On `StaffCastEnv`-family envs (Pocket Caster, Talisman), `StaffCastEnvMixin` fires Mishap talismans from inside the failing Thread's `postExecution`, the same as a Pocket Caster fire does today. A future host should choose its env class with that in mind. |
| The mishap watch depends on `super.postExecution` | Every Hexwright and Hex env chains correctly. A future env that doesn't would blind the watch; the `ops >= max` pre-check would still catch most overruns, but not the case where the image sits just below the maximum. |
| Per-Thread chat spam | Player envs message every mishapping Thread. |

### Open questions after Phase 2

1. **Should a nested activation share its parent's budget?** When a Thread's spell triggers another
   host's weave, should the child spend from the parent's remaining ops (the recommended lineage), or
   start fresh with a depth limit only?
2. **What happens when the cap is hit?** Right now the dropped invocations are only logged. Should the
   caster or host hear about it (chat, a Scrying Lens message on a block host), as with a mishap?

### Phase 3 recommendation

**Persist a weave, still with no player-facing content.** Woven Orbs and the Loom both depend on this
format, so it should be settled before either.

1. A version-1 NBT codec for `Hexweave`, in the shape sketched under Proposed data model:
   - `Version`;
   - `Threads [{Id, Ingress, Egress, Patterns [{Pattern, OriginQ, OriginR}]}]`;
   - `Links [{From, To}]`.

   The pattern origins go in the format now, even though nothing places them yet, so that the Loom
   needs no migration. Loading validates through `Hexweave.of`. An unknown version or a malformed record
   refuses to load and logs a `warn`.
2. Round-trip, corrupt-record and unknown-version checks added to `/hexwright weave selftest`.
3. Dev-only `/hexwright weave save <graph>` (write the weave built from the hotbar onto the held item's
   NBT) and `/hexwright weave run held`, so a stored weave can be exercised end to end, including after
   a server restart.
4. Decide Open question 1 of this section. If the answer is "share", replace the local `ops` in
   `HexweaveCasting` with a small budget object the host can pass in. That is the only change the
   executor needs before the first real host.

Still out of scope for Phase 3: the Woven Orb item, the Loom, triggers, integration with existing hosts,
suspension and JOIN.

---

## Phase 3: implementation status

Implemented 2026-09-28. Phase 3 adds persistence and the shared execution budget. All Phase 2 execution
semantics are unchanged, and every Phase 2 check still passes. There is still no item, no Loom, no
trigger, and no existing host was touched.

### What was built

| File | Change |
|---|---|
| `server/hexweave/PlacedPattern.java` | **New.** Record `(HexPattern pattern, HexCoord origin)`: Hex's `ResolvedPattern` without the resolution state. |
| `server/hexweave/WeaveThread.java` | Patterns are now `List<PlacedPattern>`. `program()` still uses list order only. |
| `server/hexweave/Hexweave.java` | `of()` now also enforces the limits and checks each Thread's geometry (see below). |
| `server/hexweave/HexweaveNbt.java` | **New.** `write(Hexweave)` and `read(CompoundTag)` for Version 1. |
| `server/hexweave/WeaveExecutionBudget.java` | **New.** The budget one causal chain shares. |
| `server/hexweave/HexweaveCasting.java` | Spends from a `WeaveExecutionBudget`. The old four-argument `activate` makes a fresh one; a five-argument overload takes one. |
| `server/hexweave/HexweaveCommand.java` | Hotbar import lays patterns out legally. New `save <graph>` and `runstored`. |
| `server/hexweave/HexweaveSelfTest.java` | Tests I–R added; new `selftest persist` / `selftest restored` pair for the restart check. |

Validation lives in one place: reading only parses (types, ranges and counts), then hands the result
to `Hexweave.of`, the same gate a weave built in memory goes through. Writing never validates, because
it only ever receives a `Hexweave`, which is valid by construction.

### Version 1 stored structure

```
{
  Version: 1                                   int
  Threads: [                                   list of compounds, written in Thread-id order
    { Id:      int
      Ingress: byte (boolean)
      Egress:  byte (boolean)
      Patterns: [                              list of compounds, in program order
        { Pattern: { start_dir: byte, angles: byte[] }   Hex's pattern shape, written by hand
          OriginQ: int                         axial origin in the Thread's own grid
          OriginR: int }
      ] }
  ]
  Links: [ { From: int, To: int } ]            list of compounds, in stored order
}
```

- A pattern entry uses Hex's `ResolvedPattern` keys (`Pattern`, `OriginQ`, `OriginR`) without `Valid`,
  the display state, which is recomputed wherever it is shown.
- Nothing names a level, entity, block entity, environment, VM or live iota. Payloads are runtime data
  and are not part of the definition.
- **Room to grow:** keys a version doesn't know are ignored. Optional additions (Loom plane layout,
  armature placement) can therefore be added without a new version. A change in what an existing key
  means needs Version 2.
- **Migration path:** `HexweaveNbt.read` dispatches on `Version` (`switch`). v1 → v2 is one more
  `case 1 -> readV2(upgradeV1(tag))`. An unknown version is never guessed at.
- **Dev storage:** `HexweaveCommand.DEV_KEY = "hexwright:dev_hexweave"` in the item's root tag. It is a
  test key on purpose; the Woven Orb should choose its own key and can reuse `HexweaveNbt` unchanged.

### Validation limits

| Limit | Value | Why |
|---|---|---|
| Threads per weave | 64 | One activation can run at most 64 Threads anyway; more could never all be reached. |
| Links per weave | 256 | Four per Thread on average; covers any fan-out an activation could use. |
| Patterns per Thread | 1024 | Hex's own ceiling on the iotas one list can serialise (`MAX_SERIALIZATION_TOTAL`), so a Thread holds no more than a focus could. |
| Patterns per weave | 4096 | Bounds the item's NBT, which syncs whole to clients. At about 60 bytes per typical pattern that is roughly 250 KB; the absolute worst case (every pattern 256 angles) is about 1.25 MB, still under vanilla's 2 MB limit for NBT read from a packet. |
| Angles per pattern | 256 | Great spells are under 40 angles; this leaves room for long number literals. With the per-Thread cap it bounds the overlap check to about 263k points per Thread and about 1M per weave. |
| Origin, each axis | ±65 536 | Keeps every visited point far from int overflow and is far beyond any grid a screen shows. It is **not** a Loom radius; none is enforced yet. |

The counts are checked on the raw list sizes before any entry is parsed, and the running pattern total
is checked before each Thread's patterns are parsed.

**Geometry** uses Hex's own rules and adds none:

- **Drawability:** a pattern must pass `HexPattern.fromAngles(signature, startDir)`, which is Hex's own
  check that rejects a line drawn twice or doubling back.
- **Occupancy:** no two patterns in a Thread may share a grid point. This is the rule
  `StaffCastEnv.handleNewPatternOnServer` holds a staff grid to. A pattern may cross its own points,
  just as a drawn one can.

The reader also checks the raw bytes: Hex's `HexPattern.fromNBT` silently maps an out-of-range
direction or angle to a valid one, which would run a different pattern from the one stored.

The writer builds the pattern tag itself too, rather than calling `HexPattern.serializeToNBT`. Addons
mix into that method: Iotaworks wraps it as `{parent: {start_dir, angles}, level: int}`, which made
every Loom save in a pack with Iotaworks fail with "is not a pattern tag". The stored format is
Hexwright's, so it must not change shape with the installed addons.

**Hotbar import:** a focus has no geometry. `HexweaveCommand` therefore lays the imported patterns side
by side, each starting one column clear of the last, so the result is legal. This is placement for dev
tooling only; the Loom will place patterns for real.

### Malformed-data behaviour

`HexweaveNbt.read` never throws anything but `IllegalArgumentException`. Any other `RuntimeException`
is caught and wrapped. Every refusal logs one `warn` line, `Refusing a stored Hexweave: <reason>`,
because per `AGENTS.md` a broken save record is something an operator needs to see. The dev commands
turn the refusal into a chat failure; nothing is executed. Each refusal in the tests took 0.04–2 ms;
the worst case, 5 120 patterns, took 11 ms, having parsed 4 096 of them first.

### Execution budget design

`WeaveExecutionBudget` holds exactly:

- **ops consumed**;
- **Thread runs** (invocations) against `MAX_INVOCATIONS = 64`;
- an **ops-exhausted** flag.

It has no global registry, no ThreadLocal and no lifetime beyond the Java references that hold it.

- **No op ceiling is stored.** Hex enforces each environment's `maxOpCount()` against the image's op
  count, and the executor seeds that count from the budget. So the environment's ceiling is the one
  that actually holds, exactly as in Phase 2; a second ceiling stored here could only disagree with it.
- **Ops are added, not overwritten:** `opsConsumed += max(0, opsAfter - opsBefore)`. For sequential
  activations this is identical to Phase 2's `max`, and it still refuses to hand back ops that a pattern
  resets. For a nested activation, which spends from the same budget while its parent Thread is still
  running, both spends are counted; overwriting would lose one under the other.
- **The exhausted flag** exists because after a `MishapEvalTooMuch` Hex keeps the image from before the
  overrunning step. The count can then sit just under the ceiling, and a later weave on the same budget
  would start a Thread that drowns its caster again.
- **One invocation cap per chain, not per weave.** Nesting can only happen from inside a running
  Thread, and every nested activation runs at least one Thread before it can nest again. So the chain's
  invocation count also bounds nesting depth, and a separate depth counter would add nothing yet.

**Using it:**

- `HexweaveCasting.activate(weave, level, envs, seed)` starts a fresh budget. That is what an external
  event does, and it is all the dev command uses.
- `activate(weave, level, envs, seed, budget)` spends from the budget it is given.

**How a future nested host will propagate it.** When a Thread's spell reaches a host that activates
another weave (Ward → Weave A → Resonant message → Ring → Weave B), that host needs the budget of the
chain it was triggered from. The natural carrier is the casting environment:

1. The executor attaches the budget to each Thread's environment as a `CastingEnvironmentComponent`
   extension, the same mechanism the mishap watch already uses.
2. A host activated synchronously from inside a Thread reads it with `env.getExtension(KEY)`, or from
   whatever it was handed, and passes it to `activate`.
3. A host that defers work across a tick (the Harmonic delivery queue does) would carry the budget in
   its envelope. Cross-tick lifetime is deliberately not designed yet.

None of this is built. No host nests, and the extension should be added with the first one that does.

### Test results

These ran on the dev dedicated server against a throwaway world, with the real Hex Casting 0.11.4 VM and
a `FakePlayer` caster. The final run of `/hexwright weave selftest` had **77 checks, all passed**:
Phase 2's 39 unchanged plus 38 new. The restart check ran across a real server stop and start.

| Test | Observed |
|---|---|
| **A–H, V** (Phase 2) | All 39 still pass. They go through the new geometry validation, since hotbar import now lays patterns out, and through the budget object. Timings are in line with Phase 2: the 64-run cycle took 2–7 ms and the 100k-op weave 176–258 ms, against 165–218 ms for the same loop as a plain hex. The spread is run-to-run JVM variance. |
| **I** round trip | A 4-Thread weave (`1>2,1>3,3>4`, 5 patterns) came back structurally equal: Thread ids, armatures, pattern order, patterns, origins, links. Writing the copy gave a byte-equal tag. Original and copy ran identically (same Thread order, outcomes, stack sizes and emitted iotas). |
| **J** geometry | Five patterns at `(0,0)`, `(12,-5)`, `(-20,9)`, `(300,-300)` and `(-65536,65536)` came back exact. Refused: the same pattern twice on the same points; a pattern starting on another's last point `(2,-1)`; an origin at 65 537; a pattern that doubles back. Accepted: the same two patterns side by side sharing no point. |
| **K** missing Thread | A link rewritten to `1>99` was refused: "names Thread 99, which is not in the weave". |
| **L** duplicate ids | Refused: "Two Threads share the id 1". |
| **M** illegal links | Stored without an Egress, stored without an Ingress, and a link stored twice were all refused. |
| **N** bad version | `Version 2` was refused ("this build reads only Version 1"), as were a missing Version and a Version stored as text, each with its `warn` line. |
| **O** malformed pattern | Refused: angle byte 9, start direction 7, start direction -1, a doubling-back pattern, angles stored as an int array, a missing `Pattern`, an origin stored as text, and `Threads` that isn't a list. None crashed. |
| **P** excessive counts | Refused: 65 Threads, 257 links, 1 025 patterns in one Thread, 5 120 patterns across the weave, and a 257-angle pattern. |
| **Q** shared budget | Weaves A (`1>2>3`) and B (`4>5>6`), each Thread `[Thanatos, 1, 1, 1]`, with a 20-op ceiling. A on a fresh budget read 20, 16, 12, leaving 12 ops and 3 runs. **B on the same budget** read 8, 4 and stopped with the budget spent before its third Thread (20 ops, 5 runs). **B on a fresh budget** read 20, 16, 12 and finished. For invocations: a loop that used all 64 runs left **none** for a second weave on the same budget, and on a fresh budget that weave ran. No lock, no global. |
| **R** persistence | **In one session:** a weave stored on a stick survived the item being saved, written to NBT bytes, read back and rebuilt as a new `ItemStack`; structure and behaviour were identical. An item holding a Version 99 weave, or none, was refused. **Across a real restart:** `selftest persist` put the stick in a chest at spawn; the server was stopped and started; `selftest restored` loaded it (3 Threads, 2 links), ran it correctly (`[1, 2, 3]`, Thread 2 computed 4), then removed the chest. |

A stored weave also survives moving between inventories, because it is ordinary item NBT. The restart
half checks exactly that path: chest to disk and back.

### Real-player check

**Not done.** This environment can build and run a dedicated server headlessly, but it cannot drive a
game client: there are no input or screen tools. Everything above used a `FakePlayer`. Still to check
by hand in `runClient`, as a creative op with foci holding pattern-only hexes in hotbar slots 1–4 and
something in the off hand:

1. `/hexwright weave run 1`. Check that your own entity is the seed (e.g. slot 1 is
   `[Alidade's Purification, Impulse]` with a vector built on the stack); the effect happens; amethyst
   leaves your inventory; there is no spiral glyph and no per-pattern ding (Pocket Caster behaviour).
2. `/hexwright weave run 1>2` with slot 2 holding a pattern that needs two arguments. There should be
   one mishap message in chat, from Thread 2 only.
3. `/hexwright weave run 1>2,1>3`. Check the order and that both branches have their effect.
4. `/hexwright weave run 1>2,2>2`. It should stop quietly at 64 runs: no chat warning, one WEAVE log
   line with `/hexwright debug weave on`, and no visible hitch.
5. `/hexwright weave save 1>2,1>3`, move the off-hand item around, quit to the title screen, reload,
   then `/hexwright weave runstored`.

### Changes to earlier assumptions

- **The budget's op ceiling comes from the environment, not the budget.** The brief suggested storing
  a maximum; it isn't stored, for the reason above.
- **Ops are summed rather than taken as a maximum**, so nested activations are counted correctly. The
  sequential behaviour is unchanged.
- **Hotbar-built weaves now have real geometry** (side-by-side layout). Weaves Phase 2 would have
  accepted with overlapping or undrawable patterns are now refused. None of the tests had any.
- The Phase 1 sketch's optional `Layout` is not in Version 1; it can be added without a version bump.

### Remaining design questions

1. **What does Loom geometry validation need beyond overlap?** Presumably a Thread radius or canvas
   bound, and where the Ingress and Egress armatures sit on the boundary (which may reserve points).
   Both are Loom decisions and can be added as optional keys or tighter checks without changing
   Version 1's meaning.
2. **Should a Thread that crosses its own points be allowed?** Hex's staff grid allows it and so does
   this. The Loom may want to show it differently.
3. **Where does the Woven Orb keep its weave?** On-stack NBT under its own key, reusing `HexweaveNbt`,
   is the default. Off-stack storage, as the Hexicon does it, is only worth it if orbs routinely
   approach the 4 096-pattern limit.

### Phase 4 recommendation

Choose between the two prerequisites for players ever seeing a weave. Recommended: **the first real
host, as a Woven Orb with a single `Use` trigger and no Loom.**

- An orb item holding a `HexweaveNbt` Version 1 tag under its own key.
- For now it is filled by the existing dev `save` path or a creative-only command, since there is no
  Loom yet.
- Using it calls `activate` with a fresh budget, a `PocketCasterCastEnv`-family environment of its
  own (a new `WovenOrbCastEnv`, identifiable in mixins like `TalismanCastEnv`), and the player's
  `EntityIota` as the seed.
- Add the budget extension on Thread environments at the same time, so the orb is ready to pass a
  budget to anything it triggers. Nothing needs to consume it yet.
- The one concise cap-reached warning per activation, if wanted, belongs here; it is the first
  player-facing surface.

Out of scope for Phase 4: the Loom GUI (it deserves its own phase), the other triggers (Hurt, Attack,
Kill), Hex Ward and Resonant Ring integration, suspension and JOIN.

---

## Phase 4 — Woven Orb

Implemented 2026-09-28. For the first time a player can hold a real item and right-click to run a
persisted Hexweave. Phase 1–3 semantics are unchanged. No other trigger, no Loom, and no existing host,
Power Orb or artifact was touched.

> **Shared budget behaviour across sequential weave executions is verified. True in-flight nested
> activation remains unverified until a host-to-host integration test exists.**

### What was built

| File | Change |
|---|---|
| `server/hexweave/WovenOrbItem.java` | **New.** The item. Right-click activates the bound weave; tooltip; `bind`, `clear`, `weave`. |
| `server/hexweave/WovenOrbCasting.java` | **New.** The host: `activate(holder, hand, orb)` returns a `Result`. `use` calls it, and so do the tests. |
| `server/hexweave/WovenOrbCastEnv.java` | **New.** An empty `PocketCasterCastEnv` subclass, so orb casts are identifiable, as `TalismanCastEnv` is. |
| `server/hexweave/WeaveExecutionBudget.java` | Now a `CastingEnvironmentComponent`, with `of(env)`. |
| `server/hexweave/HexweaveCasting.java` | Attaches the budget to every Thread's environment (one line). |
| `server/hexweave/HexweaveNbt.java` | `summarize(tag)`: counts for a tooltip without running the full read. |
| `server/hexweave/HexweaveCommand.java` | `/hexwright weave bind <graph>` and `/hexwright weave unbind` for a held Woven Orb. |
| `server/hexweave/HexweaveSelfTest.java` | Tests S–Y. The old validation checks are relabelled `Val`, since `V` is now the use-activation test. |
| `server/item/HexwrightItems.java` | Registers `WOVEN_ORB` and adds it to the Tools tab after the Power Orbs. |
| `assets/hexwright/lang/en_us.json` | Six keys: `item.hexwright.woven_orb`, `tooltip.hexwright.woven_orb.{blank,weave,unreadable}`, `message.hexwright.woven_orb.{unravelled,broken}`. |
| `assets/hexwright/models/item/woven_orb.json`, `textures/item/woven_orb/` | Generated by `tools/gen_woven_orb_assets.py`: a thin voxel glass sphere filled with a liquid - `fluid_strange_mixture`'s animation recast as bright greys. `client/hexweave/WovenOrbModel` tints it with the viewer's Soulglimmer at full brightness, lights it full-bright, and draws it as cutout so the translucent glass can't sort behind it and punch holes. A woven orb switches to `woven_orb_woven.json`, the empty shell, on the `hexwright:woven` predicate, and `WovenOrbPatterns` (hooked by `ItemRendererWovenOrbMixin`) draws the weave's patterns inside it. It has its own art on purpose, so it can't be mistaken for a Power Orb. |

### Item and data

- **Id:** `hexwright:woven_orb`, stacks to 1, rarity uncommon.
- **Stored weave:** the item's root tag under **`Hexweave`**, in exactly the form `HexweaveNbt.write`
  produces (Phase 3's Version 1). There is no other copy and no trigger field: being a `WovenOrbItem`
  is what makes it fire on use. A blank orb has no `Hexweave` tag.
- **Tooltip:** "Blank. No weave is bound to it." when blank, or "Woven: N threads, M links" when bound.
  It reads the counts through `HexweaveNbt.summarize`, not a full read. A tooltip redraws every frame,
  and a full read would re-run the geometry checks and log each refusal every frame. "Its weave is
  unreadable." shows for an unknown version. Whether the weave actually loads is decided on use.
- **No foil:** the enchantment glint drew over the glass shell badly. A blank orb is full of Soulglimmer liquid instead, and a woven one is empty glass with its own patterns fading in and out inside (`client/hexweave/WovenOrbPatterns`).

### Use activation flow

`WovenOrbItem.use(level, player, hand)`:

1. **Blank orb:** returns `PASS` on both sides. No swing, no execution, no cooldown, and the other hand
   gets its turn.
2. **Client:** returns `success` and does nothing else. Execution is server-only.
3. **Server:** calls `WovenOrbCasting.activate(holder, hand, orb)`:
   1. It reads the weave through `HexweaveNbt.read`. A refusal logs its `warn`, shows the holder
      "The weave within is broken and will not hold." on the action bar, and returns `BROKEN`. Nothing
      runs.
   2. It makes a **fresh `WeaveExecutionBudget`**.
   3. It calls `HexweaveCasting.activate(weave, level, () -> new WovenOrbCastEnv(holder, hand),
      new EntityIota(holder), budget)`. Every Phase 2/3 guarantee is inherited unchanged: independent
      VMs, payload copies, the FIFO worklist, the shared budget, the cap, mishap isolation, and the
      top-iota Egress.
   4. If the activation stopped at the invocation cap, it sends the one notice (see below).
   5. It plays one amethyst chime at the holder, as the Pocket Caster and Resonant Ring do after a
      silent cast, and logs one WEAVE line.
4. **Cooldown:** the server then sets the item's cooldown to Hex Casting's own
   `HexConfig.common().artifactCooldown()` (3 ticks by default). This is the convention Hex's reusable
   stored-hex item, the Artifact, uses on every use. It is enough that one click can't fire twice, and it
   is not a gameplay cooldown. Talismans have a longer cooldown, but that is their own gameplay rule.

### Environment and media

`WovenOrbCastEnv extends PocketCasterCastEnv extends StaffCastEnv`. It adds nothing to them:

- **Caster, ambit, true name, pigment:** the holder's, exactly as a staffcast.
- **Media:** drawn from the holder's inventory by `StaffCastEnv`. There is no separate media system and
  nothing is free.
  - Test W: each branch of a fan-out paid for its own Impulse, 2 dust in total.
  - With no media, both branches mishapped with not enough media.
  - A real player who has earned Hex's overcast advancement will instead overcast into health, exactly
    as with a staff.
- **Sound and visuals:** per-pattern sounds are muted and there is no spiral-glyph broadcast, as for
  every triggered player hex in Hexwright. There is one chime per activation.
- **Mishaps:** Hex's usual effects, and `PlayerBasedCastEnv` messages the holder once per mishapping
  Thread. Because this is a `StaffCastEnv`, `StaffCastEnvMixin` fires the holder's Mishap-trigger
  talismans, as a Pocket Caster mishap does. `PocketCasterCastEnv` is already excluded from the staff
  cast animation and flare.

### Cap notification

- When `activation.stop() == INVOCATION_CAP`, the holder gets **one** action-bar line: "The weave
  unravels under its own complexity." (`message.hexwright.woven_orb.unravelled`, dark purple).
- It is sent from one branch per activation, never per dropped invocation. It is not a mishap and is
  not styled as one.
- The executor still logs the cap under WEAVE, and the host adds its own summary line.
- Nothing is shown for a normal finish. Running out of ops shows nothing extra either, since that is
  Hex's own `MishapEvalTooMuch`, which already told the holder.

### Budget attachment

- `WeaveExecutionBudget` implements `CastingEnvironmentComponent` with a private key.
- `HexweaveCasting` calls `env.addExtension(budget)` on every Thread's environment before running it.
  That covers every host, not only the orb.
- Anything running inside a Thread can call `WeaveExecutionBudget.of(env)` to find the budget that
  owns that execution. It returns `null` outside a weave.

Test Y proves the budget is attached and is the same instance. It does **not** prove nested accounting
is correct. The budget adds each Thread's op increase when that Thread *finishes*, and a child
activation started mid-Thread seeds its Threads from the budget's value at that moment, which does not
yet include the parent Thread's in-progress ops. The Phase 3 reasoning says the totals come out right,
but the child's ceiling check can be slightly generous. It needs a real host-to-host test.

### Test results

These ran on the dev dedicated server against a throwaway world, with the real VM and a `FakePlayer`
holder. `/hexwright weave selftest` passed **97 of 97 checks**: the 77 from Phases 2–3 unchanged, plus
20 new. The restart pair (`persist`, restart, `restored`) passed again.

| Test | Observed |
|---|---|
| **S** orb serialisation | A new orb is blank. Bound: the weave is under `Hexweave`, tag-equal to `HexweaveNbt.write`, not under the dev key, reads back structurally equal, and summarises as 3 threads and 2 links. Clearing blanks it. |
| **T** blank orb | `BLANK`, no activation (no VM was ever made), no dust spent. The item's `use` returned `PASS` and set no cooldown. |
| **U** invalid data | A Version 5 weave and one whose `Threads` isn't a list both returned `BROKEN`, ran nothing, spent nothing, and each logged its `warn`. The tooltip summary calls the Version 5 weave unreadable. |
| **V** use path | `1>2,1>3`: order `[1, 2, 3]`. The root started with exactly one seeded iota; it is Null only because a `FakePlayer`'s entity can't be resolved from the level (as in Phase 2). A real player's is. The root's `1` reached both branches (Thread 2 made 2). The budget was fresh: 3 runs, 4 ops, equal to the activation's. A second use got a different, fresh budget. `WovenOrbItem.use` itself consumed the click and set the artifact cooldown. |
| **W** media | Two branches each Impulsing the holder cost **2 dust**, one each. With no media, both branches `MISHAPPED` and spent nothing. |
| **X** cap notice | A weave of two self-feeding Threads hit the cap with **12 invocations dropped** and raised **one** notice. A normal finish raised none. The actual action-bar delivery can't be seen headlessly; the test asserts the state that triggers it. |
| **Y** budget attachment | At every VM step of both Threads, the `WovenOrbCastEnv` returned the activation's own budget instance. An environment outside a weave returned `null`. |

### Real-client checklist (not yet run)

None of this has run in a real client, because this environment can't drive one. In `runClient`, as a
creative op, with amethyst in your inventory:

1. Take a **Woven Orb** from the Tools creative tab. It should say "Blank"; right-clicking it does
   nothing, not even the swing.
2. Put pattern-only hexes on foci in hotbar slots 1–3. Hold the orb in your **off hand** and run
   `/hexwright weave bind 1`. The orb reads "Woven: 1 threads, 0 links".
3. **Seed:** bind slot 1 as `[Gemini Decomposition, Alidade's Purification, Impulse]`, which uses only
   the seed. Right-clicking should push you along your own look, which proves the root started with
   your entity.
4. **Media:** the same hex. Your amethyst goes down by 1 dust per use; with no amethyst (and no overcast
   advancement) you get Hex's not-enough-media mishap instead of the push.
5. **Mishap:** bind `1>2` with slot 2 `[Additive Distillation]`. You get one mishap message in chat,
   from Thread 2 only.
6. **Fan-out:** bind `1>2,1>3` with two visible effects. Both happen, in order.
7. **Sound and visuals:** there should be one chime per use, no per-pattern dings and no spiral glyphs,
   and the orb's glass and Soulglimmer liquid in hand and in the inventory.
8. **Cap:** bind `1>2,2>2,2>3,3>3` with empty or trivial bodies. You should get exactly **one** "The
   weave unravels under its own complexity." on the action bar, and no hitch.
9. **Relog:** quit to the title screen, reload, and use the orb again. It should behave the same. Move
   it through a chest on the way for good measure.

### Remaining questions

1. **Is the per-mishap chat line per Thread right for players?** A wide fan-out that fails everywhere
   repeats it once per Thread.
2. **Should a Woven Orb be worn to fire, like a talisman, or only held?** Held-and-used is this phase's
   only trigger. Worn triggers are a future host decision, as the brief says.
3. **Should the orb show which Threads ran?** Deliberately not built; that belongs to the Loom.

### Phase 5 recommendation

The Loom's first step: **a bounded single-Thread editor, with no weave-level layout yet.**

- A screen that edits one Thread's patterns on its own axial grid. It reuses Hex's coordinate and
  pattern maths and Hexwright's `PatternDrawBatch`, and it does not subclass `GuiSpellcasting` (see the
  GUI findings).
- It saves through a C2S packet that the server validates with `Hexweave.of`, which already enforces
  Hex's own drawing and occupancy rules.
- It writes to a held Woven Orb under `Hexweave`, as `bind` does now.
- Ingress and Egress stay flags set by buttons until armature placement is designed.

The alternative Phase 5 is the first host-to-host integration: an orb whose Thread can reach one other
host. That is the phase that must add the nested-budget test described above. The editor comes first
because it removes the need for the hotbar-and-command authoring path.

---

## Phase 5 — Loom Thread Editor

Implemented 2026-09-28. The first authoring interface: one Thread of a Woven Orb, drawn with Hex
Casting's own stroke rules on a bounded grid. **No runtime change:** a Loom-woven orb is an ordinary
Version 1 weave and fires through the Phase 4 path untouched. The server side is covered by automated
checks. **The GUI itself has not been run in a real client yet**; see the checklist below.

### What was built

| File | What it is |
|---|---|
| `server/hexweave/ThreadDraft.java` | The editing model for one Thread, in common code so the screen and the server tests share it. It holds armatures and patterns in drawing order, knows which pattern holds each grid point, and enforces the bound, drawability and occupancy for every stroke. It also defines `RADIUS`. |
| `server/hexweave/HexLoomBlock.java` | The Loom block. A `has_orb` block state shows whether an orb sits on it. |
| `server/hexweave/HexLoomBlockEntity.java` | One orb slot and a revision counter (see Orb identity). |
| `server/hexweave/HexLoom.java` | Registers the block, item and block entity (id `hexwright:hex_loom`), adds it to the Functional Blocks tab, and registers the save packet. |
| `server/hexweave/HexLoomEditing.java` | The server's authority: what an orb may be opened as, and whether an edit may be saved. |
| `server/hexweave/LoomNetworking.java` | Packets: open (S2C), save (C2S) and save result (S2C). |
| `client/hexweave/ThreadCanvas.java` | The reusable drawing surface for one Thread: grid, strokes, zoom, pan and selection. A component, not a screen. |
| `client/hexweave/LoomScreen.java` | The editor screen: a button column, the canvas and a status line. |
| `client/hexweave/LoomClient.java`, `LoomPatternNames.java` | Client packet handlers, and naming for patterns the client can recognise. |
| `HexweaveSelfTest.java` | Z1–Z8, run against a real Loom placed beside spawn. |
| Assets | Blockstate, two block models (empty and with an orb), an item model, three **placeholder** 16×16 textures, a loot table, a pickaxe-tag entry, and lang keys (`block.hexwright.hex_loom`, `gui.hexwright.hex_loom.*`, `message.hexwright.hex_loom.*`). |
| `HexwrightServer.kt`, `HexwrightClient.java` | One registration line each. |

### Block and item interaction

The orb sits **on** the Loom; the Loom never becomes the host. **(Superseded in Phase 6 by the docked
model and its top/side rule; see [The Loom block](#the-loom-block).)**

| Action | Result |
|---|---|
| Use the Loom holding a Woven Orb (either hand) | Sets the orb on the Loom; the top texture shows it. |
| Use the Loom with an orb on it | Opens the editor on that orb's Thread. |
| Sneak-use with **empty hands** | Takes the orb back into the inventory, or drops it. Vanilla only calls a block's use while sneaking when both hands are empty. |
| Use an empty Loom without an orb | "Set a Woven Orb on the Loom to weave it." on the action bar. |
| Break the Loom | Drops the orb. |

There is no container menu: the canvas owns the whole screen, and the one slot lives in the block
entity. That leaves room for Phase 6's workspace.

- **Blank orb:** opens as a draft (Thread 1, no Ingress, no Egress, nothing drawn). The orb isn't changed
  until a save succeeds.
- **One-Thread, unlinked orb:** opens with its id, armatures and every origin exactly as stored.
- **Multi-Thread or linked orb:** refused with "This weave is too complex for this Loom interface." It
  is never opened and never flattened. **(Superseded in Phase 6: every readable weave now opens.)**
- **Unreadable orb:** refused with "This orb's weave is broken; the Loom won't touch it."

### Thread boundary and coordinates

- **Bound:** `ThreadDraft.RADIUS = 10` hex steps from the Thread's centre, measured as axial distance
  (`HexCoord.distanceTo(origin) <= 10`). That is 331 points, 21 across. It is generous for
  experimenting and is not balance.
- **Authoring rule, not a format rule.** Version 1 still stores any origin within ±65 536. The Loom
  refuses to save a pattern past its bound, and opens such a weave (for example a long dev-bound hex)
  with those patterns drawn in red and a notice, so the player can remove them.
- **Stored coordinates:** every pattern keeps its axial origin in the Thread's own grid, exactly as
  Phase 3 stores it. Pixels exist only for drawing and input: `HexUtils.coordToPx` / `pxToCoord` with
  the current spacing and centre. No float ever reaches a saved coordinate.
- **View:** by default the whole bound fits the canvas. Scroll zooms in 5 steps (1×, 1.5×, 2×, 3×, 4×),
  keeping the point under the cursor fixed. Right- or middle-drag pans, clamped so the Thread can't be
  lost; zooming back to 1× re-centres.
- **Drawing scale:** everything is drawn at a fixed 24 px reference spacing and scaled by the pose
  matrix, so strokes keep Hex's proportions at every zoom.

### Drawing

`ThreadCanvas.extend` is a port of `GuiSpellcasting.drawMove`. Hex's version is private, so it can't be
called.

- A stroke starts on a **free point inside the bound**.
- Once the cursor is past Hex's snap distance (its client `gridSnapThreshold` setting), the direction is
  rounded to one of six with Hex's own formula and the stroke takes one step.
- Stepping straight back along the last segment undoes it.
- Otherwise `HexPattern.tryAppendDir` adds the step, which refuses to retrace a line.
- A step onto a point another pattern holds, or outside the bound, is simply not taken. The stroke can
  never leave the Thread or cross another pattern.
- Releasing appends the pattern, with its start point as origin, to the **end of the program**.
  `ThreadDraft.add` re-checks it against Hex's drawability, the bound and occupancy.
- **Nothing is cast.** No VM or environment is created, no media is spent, and there are no mishaps or
  world changes. The Loom stores geometry, and what a pattern means is resolved when the orb fires.

**Editing:**

- Undo removes the last pattern.
- Clicking a drawn pattern selects it, and Remove (or Delete) takes out just that one. The patterns
  after it keep their order.
- Clear empties the Thread.
- Esc cancels a stroke in progress; Ctrl+Z undoes.
- Moving a finished pattern is deliberately not supported.

**Feedback:**

- Each pattern's place in the program is shown beside its first point once the zoom leaves room.
- Hovering a pattern names it when the client can: any fixed-shape action (by signature, as
  `AugurStrokeGhostOverlay` does) and Numerical Reflection.
- Great spells, whose shapes are chosen per world, and Bookkeeper's Gambit show "unnamed here (resolved
  when cast)".

### Ingress and Egress

- Two toggle buttons, "Ingress: On/Off" and "Egress: On/Off". They are structural flags, as before, not
  patterns.
- Placeholder marks sit just outside the rim: Ingress in teal on the west, Egress in violet on the east,
  bright when on and faint when off. They only say "execution enters and leaves at the edge"; their
  positions aren't stored.
- **Root sanity:** with one Thread and no links, Ingress on means there is no root. The editor disables
  Save and shows a warning, and the server refuses it too.
- Egress may be on; it has no effect until links exist.

### Save flow and validation

1. The client builds the draft's one-Thread weave with `HexweaveNbt.write` and sends it with the Loom's
   position and the **revision** it was opened at.
2. The server (`HexLoomEditing.save`) refuses unless:
   - the player can still reach a Loom at that position (`Container.stillValidBlockEntity`);
   - its revision matches;
   - the orb on it is still one this Loom can edit.
3. It reads the submission through `HexweaveNbt.read`, the same hardened untrusted-input path as a
   stored weave. That gives Version, types, counts, pattern bytes, drawability, occupancy and links, each
   refusal logged as a `warn`.
4. It requires exactly one Thread and no links. It **keeps the Thread id the orb already has**, whatever
   the client sent. It refuses an Ingress, and checks every point against `RADIUS`.
5. It revalidates with `Hexweave.of`, writes a **fresh** tag into the orb (`WovenOrbItem.bind`; the
   client's tag is never stored), and advances the revision.
6. It replies with the result. The client adopts the new revision **only on success**.

Client-side checks exist only so the editor never offers something the server would refuse; they are
not the security boundary.

### Orb identity

The orb stays in the Loom's block entity while it is being edited, so it can't be moved, dropped or
swapped in an inventory. Its revision changes whenever the slot's contents change (set down, taken up,
or saved into), and every save must quote the revision it was opened at.

- A stale editor can never write into a different orb, or into this one after someone else has woven it.
- Two players editing the same Loom: the second save is refused, and that player has to reopen.
- The screen also closes itself if the Loom disappears, the orb is taken off (the synced `has_orb`
  state), or the player walks out of reach.
- The revision is not saved; no editor survives a restart.

### Automated test results

These ran on the dev dedicated server against a throwaway world. `/hexwright weave selftest` passed
**114 of 114**: Phases 2–4's 97 unchanged, plus 17 new. The new checks drive `HexLoomEditing` (the
method the save packet calls) against a real Loom, with a `FakePlayer`.

| Test | Observed |
|---|---|
| **Z1** blank draft | A blank orb opened as Thread 1, root, no Egress, empty, and was not changed by opening. Three drawn patterns saved as exactly that Thread, and the revision advanced. |
| **Z2** existing geometry | A stored Thread (id 5, Egress on, four origins) opened exactly as stored. Saving it unchanged wrote a **byte-identical** weave tag. |
| **Z3** drawing order | Patterns drawn east, west, north, then south were saved in that order. Removing the second kept the rest in order. |
| **Z4** boundary | The editor refused a stroke leaving the bound. A Thread sent anyway with a pattern past it was refused ("Pattern 1 reaches past the Loom's edge"), leaving the orb and revision untouched. |
| **Z5** overlap | The editor refused a second pattern on the first one's points. A crafted submission with two patterns on the same points was refused by the server's own checks. A lone Thread sent with an Ingress was refused as having no root. The orb was untouched each time. |
| **Z6** stale / wrong item | After the orb was taken off and a different one set down, the old editor's save was refused ("The orb on the Loom has changed since you opened it") and **neither** orb was written. A save from 20 blocks away and a save to an empty Loom were both refused. |
| **Z7** unsupported | A two-Thread, one-link orb was refused for editing, and a valid one-Thread save **could not flatten it**; the tag was unchanged. An unreadable orb was likewise refused and left alone. |
| **Z8** runtime | A Loom-woven Thread (`[Flock's Reflection, 2, +]`) and the same program bound by the dev command, with patterns in different places, ran identically through `WovenOrbCasting` (result 3). |

Also checked: no class under `server/hexweave/` references a client class (compiled bytecode scan), and
the dedicated server starts with the Loom registered.

### Real-client checklist (not yet run)

**Nothing about the screen has been seen.** Rendering, input feel and layout are unverified. In
`runClient`, creative, with some amethyst:

1. Place a **Hex Loom** (Functional Blocks tab). Use it empty-handed and see the hint. Use it holding a
   blank **Woven Orb**; the top should change.
2. Use the Loom again. The editor opens with a faint hex field and a firmer rim, with Ingress (west) and
   Egress (east) marks.
3. Draw the Phase 4 test hex as three patterns: Gemini Decomposition, Alidade's Purification, Impulse.
   Check that snapping feels like the staff grid: the same threshold, doubling back undoes, and there are
   no retraces.
4. Try starting on or crossing an existing pattern (it won't step there), and drawing past the rim (it
   stops at the edge).
5. Undo; click a pattern, hover it (tooltip name), Remove it; Clear.
6. Toggle Ingress on (Save disables, with a warning) and off; toggle Egress.
7. Save. You should see "Woven into the orb." Take the orb back (sneak-use, empty hands); its
   tooltip reads "Woven: 1 threads, 0 links".
8. Set it back and reopen. The geometry should be identical and the numbers in the same order.
9. Right-click the orb in your hand. The Thread runs, with your entity as the seed, as in Phase 4.
10. Edit, then Close without saving. Reopen: the change is gone.
11. With the editor open, have a second player (or a second client) take the orb off, or break the
    Loom. The screen closes; a save from a stale screen is refused.
12. Zoom (scroll) and pan (right-drag), then draw at 2–3× zoom. Check the input still lands on the right
    points.

### UX concerns found while building it

- **Fit-to-view is dense.** With radius 10, the whole bound fits at roughly 9–13 px per step on common
  GUI scales, against about 20–30 px on the staff grid. Drawing at 1× is likely fiddly; 2× or 3× zoom
  should feel like the staff. A smaller radius, or opening at 2× centred on the last pattern, are the
  obvious knobs.
- **Hex's "clicking toggles drawing" client option is not honoured**; only press-drag-release is.
- **One step per mouse event**, exactly as Hex does, so a very fast drag lags until the mouse moves
  again.
- Names cover fixed-shape actions and numbers only; great spells and Bookkeeper's Gambit read as
  unnamed.
- A dev-bound orb whose row layout reaches past radius 10 opens with red patterns and a disabled Save.
  That is correct, but the player must remove patterns to proceed.

### Deliberately deferred to Phase 6

Multiple Threads and creating them, links and stitching, the zoomed-out weave overview and semantic zoom
between Threads, layered Thread planes, real armature placement and physical ingress/egress overlap,
moving finished patterns, and the bound becoming part of the stored format. The runtime and triggers are
unchanged.

### Phase 6 recommendation

**A multi-Thread workspace around the existing canvas, with links but no geometry-based stitching.**

- The Loom screen holds several `ThreadCanvas`es, one per Thread, each with its own draft and grid, laid
  out in a workspace view. Only the Thread being edited takes input.
- Threads are added and removed, and ids are allocated as `max + 1`.
- Links are drawn from an Egress mark to an Ingress mark. They are validated as they are drawn by the
  same rules as `Hexweave.of` (source has an Egress, target has an Ingress, no duplicates).
- Save sends the whole weave through the same `HexweaveNbt.read` path. The server's one-Thread
  restriction is lifted, while root sanity (at least one Thread without an Ingress) stays.
- The "too complex" refusal goes away, since the Loom can now show every weave `Hexweave.of` accepts.
- Keep the positions of Threads in the workspace as optional layout keys, which Version 1 already
  tolerates.

---

## Phase 6 — Multi-Thread Loom

Implemented 2026-09-28. The Loom now authors whole weaves: several Threads, the links between them and
where each sits, in an overview that zooms into the Phase 5 Thread editor and back out. **No runtime
change:** a Loom-woven weave is an ordinary Version 1 weave, and its layout is never read by anything
that runs it. The server side is covered by automated checks. **The GUI itself has not been run in a
real client**; see the checklist below.

### What was built

| File | What changed |
|---|---|
| `server/hexweave/WeaveDraft.java` | **New.** The whole-weave editing model, in common code so the screen and the tests share it. Threads (by id), links (in the order made) and positions. Keeps links legal by construction: see Workspace model. |
| `server/hexweave/WeaveLayout.java` | **New.** Positions only: the record, the workspace bound, the automatic arrangement for weaves with no layout, and the free-spot search for new Threads. |
| `server/hexweave/ThreadDraft.java` | Armatures can now be set only through its `WeaveDraft`, because links hang from them. An edit counter lets views tell when a Thread has changed. The one-Thread `blank()` and `toWeave()` are gone. Drawing rules unchanged. |
| `server/hexweave/HexweaveNbt.java` | `write(weave, layout)` and `readLayout(tag)`. `read` is unchanged and never looks at the layout. |
| `server/hexweave/WovenOrbItem.java` | `bind(stack, weave, layout)` and `layout(stack)`. |
| `server/hexweave/HexLoomEditing.java` | Opens any readable weave; saves a whole weave (see Save flow). |
| `server/hexweave/LoomNetworking.java` | Weaves now travel as compressed NBT, with a bounded unpack (see Network payload). |
| `client/hexweave/WeaveOverview.java` | **New.** The overview component: fragments, armatures, links, picking, dragging, pan and zoom. |
| `client/hexweave/LoomScreen.java` | Rebuilt around two views, the transition between them, and confirmations. |
| `client/hexweave/ThreadCanvas.java` | Two accessors only, `atWidestZoom()` and `armatureAt(side)`. Drawing and input are untouched. |
| `client/hexweave/LoomClient.java` | Packed payloads. |
| `client/spellcasting/PatternDrawBatch.java` | `drawLineSeq`: a batched stand-in for `RenderLib.drawLineSeq`, one plain stroke, for the miniatures. |
| `HexweaveSelfTest.java` | Z1-Z8 moved to the whole-weave API; AA-AM added. |
| `en_us.json` | The Loom's block of keys, rewritten. `too_complex` and `one_thread` are gone. |

No mixin, no reflection, no new packet id.

### Workspace model

`WeaveDraft` holds three things, and keeps execution and presentation apart:

- **Threads:** a `TreeMap<Integer, ThreadDraft>`. Each `ThreadDraft` is exactly the Phase 5 model: local
  axial coordinates centred on the Thread, its bound, program order, and Hex's drawing rules. Workspace
  coordinates never touch it.
- **Links:** a list of `WeaveLink(from, to)`, in the order they were made.
- **Positions:** a map from Thread id to `WeaveLayout.Position(x, y)`. It becomes the `WeaveLayout`,
  never part of the `Hexweave`.

The draft never holds a link `Hexweave.of` would refuse:

- `link(from, to)` goes through `checkLink`, which gives `OK`, `MISSING`, `NO_EGRESS`, `NO_INGRESS`,
  `DUPLICATE` or `FULL` (256 links). The rules are `Hexweave.of`'s, so self-links and cycles are allowed.
- `removeThread(id)` removes every link to or from the Thread.
- `setIngress(id, false)` removes every link into it; `setEgress(id, false)` every link out of it.
  The screen asks before either happens; the model never leaves a link dangling.

There are no VMs, environments or iotas anywhere in it. `toWeave()` builds the `Hexweave` through
`Hexweave.of`, so the only thing it can still refuse is a weave over 4 096 patterns.

### Overview, Thread view and semantic zoom

The screen has two views over one `WeaveDraft`.

| | Overview | Thread view |
|---|---|---|
| Shows | Every Thread as a fragment, with its links | One Thread's `ThreadCanvas`, the Phase 5 editor |
| Left column | Add Thread, Open Thread, Delete Thread, Ingress, Egress, Unlink, Fit View | `< Weave`, Ingress, Egress, Undo, Remove, Clear |
| Both | Save, Close, the header (counts and "unsaved"), the status line | |

**In:** double-click a fragment; select it and press Open Thread or Enter; or keep scrolling in with the
cursor on a Thread once the overview is at its closest zoom. **Out:** `< Weave`, Escape (when no stroke
is being drawn), or scrolling out once the Thread is already at its widest zoom. So "zoom in to write,
zoom out to weave" works with the wheel alone.

**Transition (220 ms).** The canvas is drawn scaled so that its bound lies exactly over the fragment,
and grows into place while the overview fades out; closing runs the same in reverse. It is a pose
transform plus shader alpha, with no change to the canvas. Input is ignored while it runs.

**State kept across the switch:**

- The overview's camera (pan and zoom) is untouched while a Thread is open, so coming back restores it
  exactly.
- The screen keeps one `ThreadCanvas` per Thread it has opened, so each Thread keeps its own zoom and pan.
- Edits in the canvas go straight into the draft, so the overview's miniature is current the moment it
  reappears.

**The rest of the weave in Thread view.** Under the canvas's armature marks, a note says what links into
this Thread ("from Thread 1, 3") and where its Egress leads ("to Thread 4"), or that it is a root. The
other Threads are hidden, as the brief allows.

### Threads: creation and deletion

- **Add Thread** places an empty Thread just right of the selected one (where a downstream Thread
  belongs), or else in the middle of the view. Either way it goes to the first spot clear of every other
  fragment (`WeaveLayout.freeSpot`: straight there, then alternately below and above, then a column
  further right). The new Thread is selected.
- **Ids** are one past the highest the draft has held, and never go back down while editing, so a
  Thread deleted in this session never lends its id to a new one (tested: delete 3, add, get 5). After a
  save and reopen, a deleted top id can come back, since only existing Threads are stored. That is
  harmless: ids are only referenced by links within the weave.
  *Superseded 2026-10-12: deleting now closes the numbering up - see "Numbers close up on delete".*
- **Defaults:** the first Thread of an empty weave has neither armature (a root). Every later Thread
  starts with **Ingress on, Egress off**. The status line says so ("Thread 3 added, with an Ingress: link
  an Egress into it."), and the buttons show it.
- **Deletion:** Delete Thread (or the Delete key) removes an empty, unlinked Thread at once. One with
  patterns or links asks first: "Delete Thread 3, with 4 patterns and 2 links? Press Delete again to
  confirm." Its links go with it.
- **Limit:** 64 Threads; Add Thread greys out at the limit.

### Armatures

- A Thread still has at most one Ingress and one Egress. Both are flags, toggled by the Ingress and
  Egress buttons. The buttons act on the selected Thread in the overview, or the open one in Thread view.
- **Positions:** the Ingress sits on the braid at the frame's west corner, the Egress at its east
  corner (see [Thread frame](#thread-frame)). The bound is a hexagon with corners pointing east and west,
  so these are natural points on its rim.
  `WeaveOverview.armature(id, egress)` is the one place that says where they are. A Phase 7 per-Thread
  placement changes that method and the layout, nothing else.
- **Marks:** a filled teal dot with a white core is an Ingress. A **root** has no Ingress; instead it
  shows an open amber ring, where the host's activation comes in. A filled violet dot is an Egress; a
  faint small one is an Egress that is off.
- **Turning an armature off with links attached** asks first: "Turning Egress off removes 2 link(s) out
  of Thread 1. Press again to confirm." There is no hidden cleanup. Turning Ingress off says the Thread
  is now a root. Either change shows in the overview straight away.

### Links

**Creating:**

1. Press on a lit Egress mark and drag. The stroke follows the cursor in Hex's in-progress stroke
   colours.
2. Every Ingress it could land on pulses teal.
3. Release over an Ingress mark, or anywhere on that Thread's fragment, to make the link.

Over a Thread it can't link to, the stroke turns red and a red halo marks that Thread's west corner.
Releasing there says why: "Thread 4 has no Ingress. Select it and turn its Ingress on first.", or
"Thread 1 already leads to Thread 2." A press and release without dragging never makes a link.

**Deleting:** click a link to select it (it turns gold), then press Unlink or Delete. Hovering a link
names it ("Thread 1 to Thread 3") and says how to remove it.

**Many links on one armature:** one Egress to many Ingresses, and many Egresses into one Ingress, are
stored and drawn as exactly that. There is one mark per armature, never one socket per link.

### Link visual design

- A link is a Hex stroke, drawn with `PatternDrawBatch.drawPattern`: the same lightning, widths and
  vertex dots as a pattern on the staff grid. It shades from Egress violet to Ingress teal, so its
  direction reads at a glance.
- **Its course** leaves the Egress along a short straight lead, crosses, and enters the Ingress along
  another. Every link out of one Egress shares that first lead, and every link into one Ingress shares
  the last, with a vertex dot at each junction.
  - Fan-out visibly splits from a single strand, and fan-in visibly merges into one.
  - Because each link's lightning is seeded separately, the shared strands braid: several threads
    twisted into one. This is the "woven" look, from the same stroke renderer the patterns use.
- A link that would run backwards (to a Thread further left, or into its own Thread) goes over the top
  of both fragments instead of through them. So self-links and cycles stay readable.
- Selected links are gold; hovered ones brighten; links out of Threads that can never run are dimmed.
- Links sit above the fragment frames but under the patterns, so they never hide a Thread's writing.

### Miniatures

- **Frame:** the braided Thread frame (see [Thread frame](#thread-frame)), whose opaque violet plane is
  the fragment's backing. It brightens when hovered, darkens for a Thread nothing can reach, and gets a
  gold outline when selected.
- **Patterns:** the Thread's actual patterns, at their stored origins. Each is a plain two-pass Hex
  stroke (coloured body and a lighter core, as Hex draws them) with its start-to-end gradient.
  - Stroke width is fixed in screen pixels (1.3-3.2 px), so the shapes stay legible from far out.
  - They are drawn without the lightning: that is saved for the links. Drawing it across thousands of
    patterns every frame would also be costly.
  - Patterns past the Loom's bound are drawn in Hex's error colours.
- **No grid dots** in the overview; they are only noise at this scale.
- **Threads nothing can reach** (an Ingress with no link from anything that runs) are dimmed, and their
  tooltip says so. They still save: they are legal, just inert.
- **Label:** "Thread 3" above the fragment's top-left, or just "3" when zoomed far out.
- **Hover** lists the Thread's pattern count, root status or where its links come from, where its Egress
  leads, and "Double-click to write in it."
- **Cost:** stroke points are built once per Thread edit (keyed on the Thread's edit counter), never per
  frame. Fragments off screen are skipped, and everything goes through the one `PatternDrawBatch` flush.
  Reachability and the save check are likewise recomputed only after an edit.

### Thread frame

Added after the Phase 6 build, from the user's art `references/weave/thread.png`, copied to
`assets/hexwright/textures/gui/hex_loom/thread.png`. `references/` is git-ignored, so a change to the
art has to be copied across again. `client/hexweave/ThreadFrame.java` draws it for both views, so the
overview's fragment and the Thread view's border are the same object at different sizes, and the
zoom between them lines up.

- **The art:** a 164 × 177 pixel-art hexagon standing on a corner, with braids round an opaque violet
  plane. Its outline is 156 texels flat to flat and 177 tip to tip, within 2 % of a regular hexagon.
- **Rotation:** a quarter turn at draw time, since a Thread's bound has its corners east and west. The
  art is kept in the orientation it was drawn.
- **Scale:** uniform, so the plane's inner edge (63 texels from the centre across the flats) sits one
  grid unit outside the bound's outermost points. That is 3.94 texels per grid unit. The frame is then
  about 45 grid units tip to tip and 40 flat to flat.
  - An offline mock at the default fit checked all 331 grid points: every one lands on the plane. The
    closest, near a corner where the art's inner edge is a texel irregular, is 0.45 of a step from the
    braid. Pattern strokes are about 0.1 of a step wide, so nothing drawable touches it.
- **Armatures** sit on the braid, halfway through its east and west corners. In the Thread view,
  "Ingress" / "Egress" and the linked-Thread notes moved beside the frame's tips, since under the mark
  they would sit on the braid.
- **Fit:** the Thread view now fits the whole frame rather than the bare bound. At 1080p, GUI scale 2,
  the default spacing drops from about 15.2 to 12.0 GUI px per grid step (21 % denser), the price of
  showing the border in full. Zoom is unchanged.
- **Layering:** the frame is drawn before the batch, without depth, so the grid, patterns, links and
  marks always draw over it. In the overview, overlapping frames stack in id order.
- **Filtering:** nearest when a texel covers 3 or more real pixels, which keeps the Thread view's pixel
  art crisp; bilinear below that. Mocks showed nearest sampling makes strands uneven at the overview's
  1-2 px per texel and drops them below 1.
- **Resolution:** the Thread view enlarges the art to about 6 real px per texel at fit (1080p, GUI
  scale 2) and up to about 24 at the closest zoom, where it is very blocky beside Hex's smooth strokes.
  The overview shows it near its native size (about 0.8-2.3 px per texel).
- **Tint** multiplies into the current shader colour, so the transition's cross-fade still fades
  frames.

### Workspace navigation

- Right- or middle-drag pans. The wheel zooms about the cursor, 1.25× per notch, from 0.8 to 7 screen
  pixels per grid unit (a framed fragment is about 45 grid units across, so roughly 36 to 315 px wide).
- **Fit View** frames every Thread, between 1.6 and 4.5 px per unit. It deliberately won't go further
  out, so patterns stay readable; a weave wider than that needs panning. The screen opens fitted.
- The camera and every position are held within ±1 024 grid units of the centre (about thirty Threads'
  widths each way), so nothing can be lost at an absurd coordinate.
- Dragging a fragment moves it. Fragments may overlap; the higher id draws on top.

### Layout in the stored weave

```
{ Version: 1, Threads: [...], Links: [...],
  Layout: { Threads: [ { Id: int, X: float, Y: float } ] } }      optional
```

- **Units:** a Thread's own grid units (Hex's `coordToPx` at size 1). A bound is about 35 across and 30
  down, so a layout means the same on every screen and at every zoom. Positions are fragment centres.
- **Kept apart** as a top-level compound, not keys inside each Thread. `HexweaveNbt.read` never looks at
  it, so layout cannot affect execution. A Phase 3-5 reader ignores it too (unknown keys are ignored),
  so no version bump was needed. Threads and Links are written exactly as before.
- **Read forgivingly** (`readLayout`), because layout is presentation. An unusable entry is skipped and
  its Thread falls back to the automatic arrangement: wrong types, a coordinate that isn't a number, or
  a second entry for one Thread. The skip is logged under `WEAVE` debug, not `warn`, since nothing is
  lost. The weave itself is never refused over its layout.
- **Sanitised on save:** coordinates are clamped to ±1 024, and entries for Threads the weave doesn't
  have are dropped (`WeaveLayout.forWeave`). Only then is it written. Test AI proved a NaN, a 10⁹, an
  unknown id and a duplicate all cleaned.
- **Written on every Loom save,** so the composition survives reopening.

### Fallback layout for old weaves

A weave with no layout, such as anything bound by the dev command in Phases 3-5, gets
`WeaveLayout.arrange`:

- Roots go in column 0.
- Every other Thread goes one column right of the nearest root that reaches it (breadth-first over the
  links).
- Threads nothing reaches go in one column after the rest.
- Each column is centred on the axis in id order, spaced 64 × 48 grid units (56 × 44 before the
  Thread frame widened each fragment).

It is deterministic, and it is worked out when the Loom opens, **not written back**: opening never
changes the orb (test AJ). The first save writes whatever the player saw.

### Root rule and save gating

A savable weave needs at least one Thread with no Ingress. Several roots are fine. In the editor, Save
is disabled and the status line explains, in this order:

1. No Threads: "The weave has no Threads. Add one to save."
2. No root: "No Thread is a root, so nothing would ever start this weave. Turn one Thread's Ingress
   off."
3. Patterns past the bound: "Thread 2: 3 pattern(s) reach past the Loom's edge."

The server refuses all three independently.

### Save flow and server validation

1. The client writes the draft with `HexweaveNbt.write(weave, layout)`, packs it, and sends it with the
   Loom's position and the revision it opened at. A packed weave over 32 000 bytes is refused on the
   client with a message instead of being sent (see Network payload).
2. `HexLoomEditing.save` then checks, in order:
   1. The player can still reach the Loom (`Container.stillValidBlockEntity`).
   2. The revision matches.
   3. The orb on it is still one the Loom can open.
   4. The weave reads through `HexweaveNbt.read`, the hardened untrusted-input path: Version, types,
      counts and limits, pattern bytes, drawability, occupancy, duplicate ids, and every link rule.
   5. It has at least one Thread and one root.
   6. Every pattern of every Thread is inside `ThreadDraft.RADIUS`.
3. The layout is read separately and sanitised.
4. A **fresh** tag is written into the orb. The client's tag is never stored, and the revision advances.
5. The client adopts the new revision only on success, and the "unsaved" marker clears only for the
   edits that were sent.

Unlike Phase 5, the server keeps the client's Thread ids. They are only labels inside the weave, and
`Hexweave.of` already guarantees they are unique.

Nothing is written until Save. Close with unsaved changes asks first ("Your changes are not saved.
Close again to discard them."), and Escape does the same.

### Network payload

- Phase 5's one Thread could never outgrow a packet. A whole weave can: vanilla refuses a client's custom
  payload over 32 767 bytes, and the player is disconnected. In AM, a 16-Thread weave of 512 varied
  patterns was **37 028 bytes** of plain NBT, which would already have tripped it.
- So both directions now carry **gzip-compressed NBT** (`LoomNetworking.pack`/`unpack`). The same
  weave packed to 4 278 bytes. The fullest random weave the test could fit (64 Threads, 2 631 varied
  patterns, 187 949 bytes) packed to 19 160.
- At that rate (about 7 bytes per pattern) even the 4 096-pattern cap should fit under the 32 000-byte
  guard. The guard stays, with a clear message, because the rate depends on the patterns.
- Unpacking is bounded by vanilla's 2 MiB NBT allowance. A 2 961-byte payload that would inflate to
  3 MB was refused (AM).

### The Loom block

Revised after the Phase 6 build, on the user's new model (`models/block/hex-loom.json`, textures under
`textures/block/loom/`): a framed loom with a dock on its top.

| Action | Result |
|---|---|
| Use the Loom holding a Woven Orb (either hand), dock empty | Docks it. The orb floats over the dock, bobbing and turning slowly. |
| Use a **side** with an orb docked | Opens the editor. |
| Use the **top** or the **orb** with an orb docked | Takes the orb back: into the hand if that hand is empty, else the inventory, else dropped. |
| Use an empty Loom without an orb | "Set a Woven Orb on the Loom to weave it." |
| Break the Loom | Drops the orb. |

- **Why the top stands in for the orb:** the orb floats in the cell above the Loom. A click there is a
  click on air, and it would only reach the Loom's shape when the player's look ray happened to continue
  down into the block. So the whole top of the frame's shape counts.
- **Clicking the orb itself (2026-10-09):** `LoomOrbStartUseItemMixin` (client, `Minecraft.startUseItem`
  HEAD) asks `LoomClient.pickDockedOrb` whether the crosshair ray meets a docked orb's box
  (`HexLoomRenderer.ORB_BOX`, its sphere over the bob) before vanilla's target. If so the use is sent as
  a click on that Loom's top, with the height clamped to 1.5 because the server rejects a use more than a
  block from the block's centre. Only the use is retargeted: the outline and left-click still see vanilla's
  target, so aiming at the orb doesn't mine the Loom.
- **Facing:** the Loom turns to face the player when placed (`facing`, as the vanilla loom does), and its
  shape turns with it.
- **Model (2026-10-09 redesign):** `hex_loom_frame.json`, generated by `tools/gen_hex_loom_assets.py`: an
  upright tapestry loom on a slate plinth with gold rails, ebony posts with gold collars and amethyst
  finials, an open warp in two layers of lavender threads a pixel apart (cut out, so the block is on the
  cutout layer) with a shuttle in the shed between them and a woven band below, and a gold cradle with an
  amethyst gem on the top beam. Every part is whole pixels with no rotations, so there are no mixels. The orb floats centred over the
  cradle. The shape is the frame's box, 14 pixels deep and short of the block at the front and back. The
  original box model (`hex-loom.json`, its `.bbmodel`, `textures/block/loom/loom*.png`) is kept as a
  backup; restoring it means pointing the blockstate and item model back at it and putting back the full
  block shape and the old dock height.
- **The docked orb** is drawn by `HexLoomRenderer`, after `VaultPlinthRenderer`'s floating trophy. It uses
  the orb's own `fixed` display at 0.6 scale, about the cradle's width, and is lit as the air it floats in.
- **Sync:** the block entity now sends its orb to watching clients so they can draw it, so the old
  `has_orb` block state is gone and the editor watches the orb itself.
  - The orb goes out with only its first 8 patterns, for the dock to show. The client never reads
    the weave from here, and a large one would otherwise ride along with the chunk and with every save.
  - The update is written even when the dock is empty, because an empty update tag never reaches the
    client and the orb would stay drawn.
- **Occlusion:** the block doesn't occlude its neighbours; the frame is open.
- **Tests (AN):**
  - A woven orb used on the Loom docked.
  - A side click left it docked.
  - The update sent to clients carried the orb and a few of its patterns but not its weave, and still named the dock empty after a
    top click handed the orb back with its weave intact.
  - The shape turns with the Loom: wider than deep facing north, the other way round facing east.

### Orb identity

Unchanged from Phase 5: the orb stays in the Loom's block entity, and every save quotes the revision
it was opened at. Test AL: a workspace saves once (the revision moves on), then saves again quoting its
original revision, and is refused with the orb keeping the first save. Z6 still covers a swapped orb,
an out-of-reach player and an empty Loom.

### Automated test results

`/hexwright weave selftest` on the dev dedicated server, throwaway world, real Hex Casting 0.11.4 VM,
`FakePlayer` holder: **144 of 144 passed** (Phases 2-5's 114 plus 30 new); 149 of 149 after the Loom
block revision added AN's five. The restart pair
(`persist`, stop, start, `restored`) passed again.

Two Phase 5 checks were changed on purpose, because Phase 6 changes what they test:

- **Z2** now requires the Threads and Links to be byte-identical after an unchanged save, and the only
  addition to be `Layout`. It used to require the whole tag to be identical.
- **Z7** now requires a two-Thread linked orb to *open*, with both Threads and the link, without being
  changed. It used to require a refusal. The unreadable-orb half is unchanged.

| Test | Observed |
|---|---|
| **AA** round trip | A four-Thread diamond (`1>2,1>3,2>4,3>4`, six patterns) woven on the Loom saved and reopened with the same ids, armatures, links, program order and origins, and ran `1, 2, 3, 4, 4` (2 made 4, 3 made 5). |
| **AB** add/remove | Removing Thread 3 took `1>3` and `3>4` and left `1>2`, `2>4`. The next Thread added was **5**, not 3. Saved and reopened as 1, 2, 4, 5 with Threads 2 and 4 drawn as before. |
| **AC** fan-out | Both links reopened as leaving Thread 1's one Egress. Run: `1, 2, 3`; the Egress fired once and 2 and 3 each got their own copy of 3 (2 made 4). |
| **AD** fan-in | Thread 3's one Ingress received 1 and 2. Run: `1, 2, 3, 3`; 3 ran once with 1's 1 and once with 2's 3. |
| **AE** two roots | 1 and 2 (no Ingress) both into 3: saved; `roots()` was `[1, 2]`. Run: `1, 2, 3, 3`, both roots seeded by the host, 3 receiving 4 then 5. |
| **AF** no root | A cycle `1>2,2>1` with no way in: the editor reported no root and nothing reachable. The server refused it ("Every Thread has an Ingress..."), and refused an empty weave ("The weave has no Threads."). Orb untouched each time. |
| **AG** link validation | Editor: `MISSING, MISSING, NO_EGRESS, NO_INGRESS, DUPLICATE`, links unchanged. Server, on crafted submissions: missing source, missing target, source without Egress, target without Ingress, duplicate - all refused by `Hexweave.of`'s own messages, orb untouched. |
| **AH** delete connected | Deleting Thread 2 (links in from 1 and 3, out to 3 and 4, and to itself) left only `1>3`; it saved and reopened with Threads 1, 3, 4 and no dangling link. |
| **AI** layout | Positions (-37.25, 12.5) and (140.75, -88.125) came back exactly; one dragged to (5000, -5000) was held at (1024, -1024). Moving every Thread and saving again changed only `Layout` (Threads and Links byte-identical) and the orb ran identically. A crafted layout with NaN, 10⁹, an unknown id and a duplicate was cleaned and the weave still saved. |
| **AJ** legacy | A Phase 4 dev-bound diamond with no layout opened arranged: 1 at (0, 0); 2 and 3 at x 64, y ∓24; 4 at (128, 0). The orb was byte-identical after opening and ran exactly as before. |
| **AK** runtime compatibility | The same diamond woven on the Loom and bound from the hotbar ran identically: `1, 2, 3, 4, 4` with the same emitted values. |
| **AL** stale revision | First save accepted; a second quoting the opening revision refused ("The orb on the Loom has changed..."); the orb kept the first save. |
| **AM** payload | See Network payload: sizes above, both weaves unpacked unchanged, the 3 MB bomb refused at 2 MiB. |

Also checked: no class under `server/hexweave/` references a client class (compiled bytecode scan, with
a positive control on `LoomScreen`), and the dedicated server starts with everything registered.

### Real-client checklist (not yet run)

**Nothing about the new screen has been seen.** Rendering, layering, hit targets, the transition and
input feel are unverified. In `runClient`, creative, with some amethyst:

1. Set a **Phase 5 one-Thread orb** on the Loom and open it. The overview shows one fragment with its
   real patterns, a root ring on the west and the Egress mark on the east.
2. **Add Thread.** A second fragment appears to the right, selected, with a teal Ingress.
3. **Zoom into it** (double-click, Open Thread, or scroll in past the closest zoom). The canvas should
   grow out of the fragment.
4. Draw its hex, exactly as in Phase 5.
5. **Back out** (`< Weave`, Escape, or scroll out at the widest zoom). The overview should be exactly
   where you left it, with the new miniature.
6. Select Thread 1, turn its **Egress** on, and **drag from its Egress to Thread 2's Ingress**. Thread
   2's Ingress should pulse while you drag.
7. **Save.** The header's "unsaved" marker should clear.
8. Take the orb off (use the Loom's top) and **use it**. Both Threads should run, in order.
9. Set it back and **reopen**. Same Threads, links and positions.
10. Add a third Thread and link Thread 1's Egress to it too (**fan-out**). Both links should visibly
    leave along one lead.
11. Give Thread 2 an Egress and link it into Thread 3 (**fan-in**). The two links should merge into
    one lead into Thread 3's Ingress.
12. Click one link and **Unlink** it.
13. **Delete a connected Thread.** It should ask first, then take its links with it.
14. **Pan and zoom** the overview; press **Fit View**.
15. Check the **miniatures** are still recognisable at the default zoom and far out.
16. Make a change and **Close**. It should ask first; close anyway, reopen, and the change is gone.
17. Link a Thread back into an earlier one, or into itself (a **cycle**). It should be allowed, drawn
    over the top of the fragments.
18. Turn every Thread's **Ingress on**. Save should disable, with the no-root warning.

And the judgement calls this phase exists for:

- Does the overview read as woven fragments of patternwork rather than nodes?
- Is moving between overview and Thread natural?
- Is connecting armatures obvious?
- Are converging links readable?
- Is it still understandable with 5-8 Threads?

### UX concerns found while building it

- **Scale is a guess.** The default fit (1.6-4.5 px per grid unit, so fragments of about 55-155 px),
  the stroke widths, the 8 px armature hit radius and the 220 ms transition all need tuning by feel.
- **Link routing is simple.** A forward link is a straight diagonal between its leads, so with free
  placement it can cross an unrelated fragment. It passes under that fragment's patterns but over its
  plane. A backward link turns back through the gap between its two nodes when one sits clear above the
  other, and goes over the top only when they overlap (a self-link always does), which can stack up with
  many cycles.
- **Fragments can overlap** freely; nothing pushes them apart when dragged.
- **Weave-level edits have no undo.** Deleting a Thread and turning off a linked armature ask first, but
  Unlink and moves take effect at once.
- **The status line is one line** and is cut off on narrow screens. Confirmation questions and refusals
  are long enough for that to bite at large GUI scales.
- **Root vs Ingress relies on colour and shape** (amber ring vs teal dot), which may not be enough for
  every player.
- **The double-click interval is a fixed 350 ms**, not the system setting.
- **Scroll-out-to-leave** could surprise a player who scrolls out of a zoomed Thread quickly. It only
  fires once the canvas is already at its widest, but one notch too many still leaves.
- **The miniatures don't move**, only links shimmer. This is intentional, but Threads may read as
  inert.
- **Ids are the only names** Threads have.

### Deliberately not built

Triggers, Ward and Resonant Ring integration, nested host execution, JOIN, suspension, cross-tick
scheduling, Artifact Power Core changes, 3D layered planes, rotating planes, fused-armature glyphs,
particles, final artwork and final transitions. The runtime is untouched.

### Phase 7 recommendation

**First, run the checklist above**; Phase 7's shape depends on how the overview feels. Assuming the
interaction model holds:

1. **Tune from feel:** scales, stroke widths, hit radii, the transition, and the status line (two lines,
   or a tooltip for long messages).
2. **Armature placement.** Store a rim angle per Thread per armature in `Layout.Threads[]` (for example
   `IngressAt`, `EgressAt`), and let the player drag the marks around the rim. The overview already has a
   single `armature()` point of change, and the stored format needs only optional keys.
3. **Links that respect fragments:** route around planes instead of straight through, and replace the
   braided leads with the intended overlapping-glyph fusion at shared armatures.
4. **Weave-level undo** in `WeaveDraft` (a small command stack), so unlinks, moves and deletions are
   recoverable.
5. **Layered planes and depth** (`Layout` can take a `Depth`), if the 2D overview proves too flat for
   larger weaves.

Separately, the **host-to-host nested-budget test** noted in Phase 4 is still owed. It belongs with the
first integration that lets a Thread trigger another host, not with the Loom.

## Loom window in LDLib (2026-10-05)

The Loom's screen is now an LDLib window, laid out in `loom.ui`, so its look can be changed in LDLib's
UI editor without code. The vanilla `LoomScreen` is gone.

### Shape

| File | Role |
|---|---|
| `client/hexweave/LoomEditor.java` | Everything `LoomScreen` did apart from drawing buttons: the draft, the two views, the transition, confirmations, status, save. Knows nothing about widgets. |
| `client/hexweave/LoomAction.java` | Every button action (Add, Open, Delete, Unlink, Fit, Zoom In/Out, Back, Undo, Remove, Clear, Ingress, Egress, Save, Close). Saved in `.ui` files by lowercase name. |
| `client/hexweave/LoomWindow.java` | Builds the window from `loom.ui` (or the built-in layout), binds the Loom widgets by type, opens it client-side. Also the `IUIHolder`. |
| `client/hexweave/LoomFrame.java` | Full-screen host: keeps the layout 5% in from each screen edge and stretches it, giving the extra room to the canvas. |
| `client/hexweave/LoomLayout.java` | The built-in 400x240 layout: the old screen's layout plus zoom buttons. |
| `client/hexweave/LoomCanvasWidget.java` | `loom_canvas`: the views, drawn in its rectangle. Palette colours and an editor `preview` setting. |
| `client/hexweave/LoomButtonWidget.java` | `loom_button`: one action, with disabled / armed / on textures besides LDLib's normal, hover and pressed. |
| `client/hexweave/LoomLabelWidget.java` | `loom_label`: the live summary or status line. |
| `client/hexweave/LoomModeGroupWidget.java` | `loom_mode_group`: a group shown only in the weave view, only in a Thread, or both. |
| `client/hexweave/LoomPalette.java` | The views' colours, previously constants. |
| `client/hexweave/LoomDevTools.java` | `/hexwright loom preview` and `/hexwright loom export`. |
| `tools/sync_ui.py` | Copies an editor-saved project (and any run-only textures it uses) into `src/main/resources`, uncompressed. |

All four widgets are under the editor tab **Hex Loom** (`widget.hex_loom`), registered with the rest of the
custom LDLib widgets in `HexwrightClient` when `HEXWEAVE` is on.

### Decisions

- **Still client-only.** The window is a `ModularUI` built and opened on the client, like LDLib's own
  compass screen, but `player.containerMenu` is left alone: the server keeps container 0 (the inventory)
  open, and its slot packets must keep reaching the inventory menu, not an empty one. Every widget is
  marked client-side, so no LDLib click packet is sent. Networking is untouched.
- **Near full screen (2026-10-08, replacing "fixed window").** A full-screen `ModularUI` whose one widget,
  `LoomFrame`, resizes on `onScreenSizeUpdate` as LDLib's `CompassView` does. The layout is always 90% of the
  screen, 5% in from each edge - smaller than authored too, rather than cut off (user, 2026-10-08). LDLib
  has no anchors, so they are read off the layout per axis: the canvas and every group holding it grow (or
  shrink); anything else covering at least 2/3 of its group does too; everything else keeps its distance
  from the nearer edge (or stays centred). That matches where LDLib's `align` puts a widget, so aligned
  widgets agree. On a screen too small for the layout, bottom/right-anchored widgets close in on and cover
  top/left ones. The built-in layout is drawn at 400x240.
- **Bound by type.** Buttons carry their action, so a layout has no ids to match and can repeat a button.
  A layout without a `loom_canvas` logs a warning and falls back to the built-in layout.
- **Buttons ask, every frame.** Enabled state and label come from `LoomEditor` when drawn or clicked; a
  disabled button is the widget's own state, not LDLib's `isActive`, which would stop it ticking.
- **Depth.** LDLib draws with depth writes off; the views rely on depth to layer links under miniatures and
  the zooming canvas over the overview. The canvas turns depth writes on for itself and clears depth after,
  so widgets placed over it are not hidden.
- **Keys** go to a code-added `Controls` widget at the root (last child, so first to hear): Escape, Delete,
  Ctrl+Z and Enter as before. The inventory key is swallowed, since a container screen would close on it
  and skip the unsaved-changes question.
- **Enums are saved by constant name** (`SAVE`, `THREAD`), not `StringRepresentable`. LDLib looks saved
  names up in a `WeakHashMap` keyed by the name string; a computed name is collected at the next GC and
  every value then loads as the first constant. Seen in the probe: after a GC every button became Add.
- **Vanilla button faces are built fresh** in `LoomButtonWidget`. LDLib's `VANILLA_BUTTON_*` can't be
  copied: `ResourceBorderTexture.copy()` drops the slice mode and sheet offset and draws all of
  `widgets.png`.

### Verified

Scripted dev client (1920x1080, isolated run dir), screenshots checked:

- `/hexwright loom preview`: overview, tooltip, Fit View, selecting a Thread and Open Thread (zoom
  transition), the Thread view's group replacing the weave's, zoom buttons over the canvas, drawing a
  stroke through LDLib's mouse dispatch, Escape back out, Delete arming to a gold "Confirm?", F5 reload.
- `/hexwright loom export`, forced GC, preview again: the file-loaded window is identical to the
  built-in one.
- Real Loom: dock an orb, side-click opens the window, Add Thread, Save ("Woven into the orb."),
  Escape closes without asking, reopening shows the saved second Thread.
- Dedicated dev server starts and stops cleanly; LDLib's annotation scan loads the four widget classes
  there without complaint.

Not checked: the widgets inside LDLib's UI editor itself (toolbox, configurators, preview switching).

### Editing the layout

1. `/hexwright loom export` writes the built-in layout to `<run>/ldlib/assets/ldlib/projects/ui/loom.ui`
   (never over an existing file).
2. Open it in LDLib's UI editor. Set the canvas's `preview` to `thread` to see and edit the Thread view's
   group.
3. `/hexwright loom preview` opens the window on a sample weave with no Loom; in any open Loom window F5
   rebuilds it from the file on disk, keeping the weave, open Thread and zoom.
4. `python tools/sync_ui.py loom` to ship it.
5. Draw it at any size; it is stretched as described under Decisions. Put things that should hug the
   bottom or right edge nearer that edge, and make a bar or column cover most of its group to have it
   stretch.

The preview command and F5 are the `loom_tools` dev feature (on in dev runs), kept apart from `hexweave`
so they stay out of a release after the Loom ships.

## Triggers replace the Ingress/Egress toggles (2026-10-08)

Ingress and Egress were then renamed **Eye** and **Tail** (user, 2026-10-08) - in the Loom, the code and the lang keys. Stored Version 1 tags keep their `Ingress`/`Egress` keys; the sections above this one use the old names.

At the user's request the armatures stopped being settings. This supersedes "Eye/Tail are Thread
flags" and the root rule above.

### Model

- **Every Thread has an Eye and a Tail, always.** `WeaveThread` is just `(id, patterns)`. An
  Tail fires when the Thread completes cleanly with something on its stack *and* links somewhere; an
  Eye is used when anything links into it. Unlinked ends are drawn faint but can still be dragged
  from and dropped onto.
- **Triggers are nodes.** `WeaveTrigger(id, kind, targets)` - an event, and the Threads it starts with the
  host's seed. Ids are their own numbering. The only kind is `ON_USE` (the Woven Orb's use), stored by
  lowercase key. One trigger can start many Threads; a Thread can be started by triggers and Threads
  alike. A Thread two triggers start runs once per trigger.
- **Execution order**: `Hexweave.starts(kind)` - trigger by trigger in id order, each trigger's Threads in
  id order - then the existing breadth-first worklist. `HexweaveCasting.activate` now takes the event
  kind; the orb passes `ON_USE`.
- **Limits**: `MAX_TRIGGERS` 16; trigger links count toward `MAX_LINKS` (256).
- **Save gating**: the Loom (editor and server) requires that `ON_USE` starts at least one Thread
  ("Nothing starts this weave. Add a trigger and link it to a Thread."). A blank orb opens as Thread 1
  already linked from On Use trigger 1.

### Format Version 2

`Threads` lose `Eye`/`Tail`; `Triggers: [{Id, Kind: "on_use", Threads: int[]}]` is added; `Layout`
gains `Triggers: [{Id, X, Y}]`. **Version 1 is still read** and upgraded to exactly what it meant: its
links are still held to v1's armature rule, and every root (Thread without Eye) is started by one On
Use trigger, id 1. Nothing is rewritten until the orb is next saved. The tooltip counts links + trigger
links (v1: + roots) so it doesn't change on upgrade.

### Loom

- **Add Trigger** (`LoomAction.ADD_TRIGGER`). With a Thread selected, the trigger is placed just left of
  it and already linked; otherwise it starts unlinked.
- The trigger node is an amber seal with a spark, its kind written under it. **Double-click it** (or
  Enter / the Open button, which reads "Trigger Type" while a trigger is selected) for the list of kinds.
- Delete is contextual: "Delete Thread", "Delete Trigger" (asks first if it starts anything) or "Unlink".
- `LoomAction.INGRESS`/`EGRESS` and the button's `onTexture` are gone; the canvas colour `rootColour` is
  now `triggerColour`. `loom.ui` was patched to match (its two Tail buttons became Add Trigger under
  Add Thread; its status line widened to the canvas so it stretches).
- New nodes are placed clear of every node at that node's own size (`WeaveLayout.Footprint`).

### Verified

- `/hexwright weave selftest`: 165 checks pass on the dev server, including new `Val`/`M` trigger
  refusals, `AO` (Version 1 upgrade runs identically; v1 armature rule still enforced; rootless v1 has
  no trigger) and `AP` (two triggers, one starting two Threads: saved, reopened, run as 1, 2, 2, 3;
  removing triggers and Threads takes their links). `selftest persist` / restart / `restored` passes.
- Scripted client at 1280x720: overview with the trigger and lit/faint armatures, Add Trigger linking to
  the selected Thread, the type picker, and the open Thread's "from Trigger 1 (On Use)" note.

## Held-orb triggers (2026-10-08)

The Woven Orb fires from a hand - main hand (no weapon/tool) or off hand (no shield/totem) - never from
the inventory. That cost is what lets it carry triggers too strong for a worn talisman. All of it lives in
`WovenOrbTriggers`; `WovenOrbItem.use` only hands it the click.

| Kind | Fires when | Starts with |
|---|---|---|
| On Use | right-click | the holder |
| Sneak + Use | right-click while sneaking | the holder |
| Key | the trigger's own key goes down while the orb is in hand | the holder |
| Use + Key | right-click and the trigger's own key together, either pressed first | the holder |
| Proximity | a living entity comes within 1 block of the holder's outline | that entity |
| Incoming Projectile | a projectile within 5 blocks, not the holder's, whose path passes within 1.5 blocks in the next second | the projectile |
| Targeted | a mob within 24 blocks sets the holder as its target | that mob |
| Gaze | the holder looks at one creature for 20 consecutive ticks (32 blocks, blocks occlude) | that creature |
| Parry | an accepted blow from an entity lands within 8 ticks after a use (same `hurt` RETURN as talisman Struck) | the attacker |
| Falling | fall distance passes 4 blocks while airborne (not elytra, flying or in water) | the holder |

- **Keys are per trigger, not a keybind** (user, 2026-10-08): each Key / Use + Key trigger stores its own
  key as the client's input name (`key.keyboard.g`, `key.mouse.4`) under `Key` in the trigger entry. On
  the Loom, picking either kind from the type list asks for the key next ("Press a key...") - any keyboard
  key or an extra mouse button; Escape or a left/right click cancels. The node then reads "Press G" or
  "Use + G".
- **A use fires the most specific chord the weave listens for:** Use + Key for every held key a trigger
  names, then Sneak + Use, then On Use. A matched chord still waiting does not fall through. An orb with
  only On Use fires it however the click is made. A key going down while the use button is held fires
  that key's Use + Key (else its Key), so the chord works either way round.
- **No item cooldown.** Each kind waits before firing again, per hand: 5 ticks after an input (not
  noticeable, but a held or spammed click fires at most every other vanilla repeat), 10 after a sensor.
  Nothing is queued during a wait.
- **Sensors are edge-triggered:** they fire on the change (an entity entering, a projectile turning
  incoming, a mob acquiring the holder, a stare reaching a second, a fall passing the threshold) and never
  again while it stays that way. A hand that stops holding an orb forgets what it saw.
- **Cheap per tick:** a held orb is asked which kinds it listens for with `HexweaveNbt.triggerKinds`
  (reads only the `Triggers` list); the weave is fully read only when one fires. Scans run only for the
  kinds present.
- The client reports only keys a held orb's triggers name: `WovenOrbKeyPressMixin` /
  `WovenOrbMouseMixin` (HEAD of `KeyboardHandler.keyPress` / `MouseHandler.onPress`, never cancelling)
  feed `WovenOrbClient`, which sends (key, down, use button held) on `WovenOrbTriggers.KEY_C2S`. Presses
  count only with no screen open (typing in chat fires nothing); releases always go through, and a held
  key whose orb leaves the hand is released on the next tick. Events rather than polling, so a tap
  shorter than a tick still counts. A key bound to something else still does that too.
- Waits are per hand per kind **and key**, so G and H don't hold each other off.
- The Loom's save rule is now "some trigger starts something", whatever its kind.

Verified: `/hexwright weave selftest` 177 checks pass (`AQ` chords, per-key Key triggers, either-order
Use + Key, fallback; `AR` every sensor's fire-once-with-its-seed behaviour; keyed-trigger validation in
`Val`/`M`). In a real client through the real packets: Key G, Use + G and On Use each fired the right
trigger, a side mouse button (`key.mouse.4`) fired its Key trigger, and on the Loom picking Use + Key then
pressing K made the trigger "Use + K".

## Thread timeline (2026-10-12)

The user's complaint: putting a pattern in the middle of a Thread meant deleting everything after it,
because a stroke could only ever go on the end. So the Thread view has a timeline along the bottom of the
canvas (`ThreadTimeline`).

- **What it shows:** one cell per pattern in program order, each the pattern itself centred with Hex's
  own `RenderLib.getCenteredPattern` and drawn through `PatternDrawBatch` in the canvas's colours (gold
  when selected, red when outside the bound). A pulsing I-beam in the stroke-in-progress cyan marks where
  the next stroke will go in.
- **Where it lives:** inside the `loom_canvas` widget, not as its own `loom.ui` widget - the bottom 32 GUI
  px of the canvas rectangle while a Thread is open. The `ThreadCanvas` is given the rectangle above it,
  so fit-to-view never puts the frame under the bar. It fades in and out with the zoom (shader alpha =
  transition progress) but doesn't scale with the Thread, since it isn't part of the Thread's picture.
- **Insertion point:** view state on each `ThreadCanvas`, like zoom - each Thread keeps its own. `null`
  means "follow the end", so the default behaves exactly as before. A finished stroke is inserted there
  (`ThreadDraft.insert`) and the point moves past it, ready for the next.
- **Input:** a click moves the line to the nearest gap (left half of a cell = before it, right half =
  after), and a click on a cell also selects that pattern, as on the canvas. Scroll over the bar scrolls
  it; Left/Right step the line, Home/End jump it. The bar scrolls itself to keep the line in view, but
  only when the line or the Thread changes, so scrolling away to look isn't undone.
- ~~**Undo** takes out the pattern just before the line~~ - replaced the same day by a real undo/redo,
  see "Undo and redo". Remove moves the line back if it took something before it; Clear resets it to the
  end. While the line is not at the end, the help line says where strokes go and
  how to get back to the end.
- Program order is still the only order the format knows: insertion only changes which index a pattern
  is stored at. `HexLoomEditing` and the server are untouched.

- **Look (same day, user request):** the bar didn't match the window, so it is drawn with LDLib
  textures, and those are settings on the `loom_canvas` widget alongside its colours: timeline height
  (16-96, cells are that less 8), background, cell, hovered cell, selected cell (unset = plain cell with a
  gold outline), and the insertion line colour (added to `LoomPalette` as `insert`). Defaults are the same
  180 px / 4 px-border panels the user's `loom.ui` palette is built from - `internal-heavy` bar,
  `section-meium` cells, `input-default` hovered - built in code, so the shipped `loom.ui` needed no
  patching (absent keys keep the field defaults). Verified in the probe: defaults render in the window's
  style, a restyle (height 44, other textures, selected texture, gold line) applies live and the canvas
  shrinks to make room, and the settings round-trip through `serializeInnerNBT`/`deserializeInnerNBT`
  with a GC between.

Not built: drag-to-reorder in the timeline (would be a `ThreadDraft.move` plus a drag state; a natural
next step if insert alone isn't enough).

Verified: `/hexwright weave selftest` 178 checks pass (`Z3` gained an insertion check: inserted at 1,
saved second, the rest in order). Scripted client at GUI scale 2 and 3: clicking a gap, drawing a stroke
(inserted as 2, later patterns renumbered on the canvas), Left/End, clicking a cell (selected gold, line
before it), Delete, scroll over the bar (taken by the bar, no zoom), and the bar fading out mid-zoom.

## Numbers close up on delete (2026-10-12)

The user's report: deleting Thread 2 left Threads 1, 3 and 4. Now `WeaveDraft.removeThread` and
`removeTrigger` number what is left from 1 again, in order, moving each node's links, position (and a
trigger's kind and key) to its new number; the next one added is one past the last. Threads and triggers
keep separate numberings.

- Safe because numbers are only names inside the weave: links are all that refer to them, and execution
  order is their order, which closing up never changes. Casting only logs them.
- `ThreadDraft` keeps its identity and edit count across a renumber (`renumber` is package-private, called
  only by the draft), so an open canvas still points at the right Thread.
- Client caches that were keyed by id - and relied on ids never being reused - are now keyed by the
  `ThreadDraft` itself: `LoomEditor.canvases` and `WeaveOverview.miniatures`. A deletion also cancels the
  overview's transient state (link drag, move, trigger picker, key capture) and selection, since those hold
  numbers that now name a different node.
- A weave stored with gaps opens with them (opening still never changes a weave - `Z2`) and closes up at
  its first deletion.

Verified: selftest 179 checks pass - `AB`, `AH` and `AP` now assert the closing up (Thread 4 becomes 3 with
its patterns, place and links; trigger 3 becomes 2 with its kind, place and link; the next added is one past
the last). In the dev client: after opening Threads 2 and 3, deleting 2 relabelled 3 as Thread 2 with its own
miniature, Thread 1's tooltip read "Tail, to Thread 2", opening Thread 2 showed the old Thread 3's canvas,
and Add Thread made Thread 3.

## Undo and redo (2026-10-12)

The user's complaint: Undo only removed the last pattern drawn, not the last action. Now Undo and Redo walk
through every finished action, in both views: strokes, Remove, Clear, adding/deleting/moving Threads and
triggers, links, trigger kinds and keys. Ctrl+Z undoes; Ctrl+Y or Ctrl+Shift+Z redoes.

- **Steps are snapshots, not commands.** `WeaveDraft.snapshot()` / `restore()` capture the whole draft;
  `LoomHistory` (client) keeps up to 100. A step is taken on the editor's tick when `weave.edits()` has moved
  and no gesture is under way (`overview.busy()` or a stroke in progress) - so a drag, a stroke or a deletion
  with its links is one step, and any future edit is undoable without being taught to be.
- **Same objects back.** A snapshot holds each `ThreadDraft` itself plus the number and patterns it had, so
  undoing a deletion (and its renumbering) restores the same Thread - canvases and miniature caches are keyed
  by it. `ThreadDraft.patterns()` is now cached per change, so snapshots of untouched Threads share one list.
  `edits()` still only goes up across a restore.
- **Timeline line.** Each step keeps every open canvas's insertion point; moving the line is not a step, so
  the latest step keeps up with it until the next change. Undoing a mid-Thread stroke puts the line back
  where it went in; redo puts it after.
- **After a restore** selections, strokes and anything the overview held by number are dropped; the open
  Thread stays open under its number then, or the editor leaves for the overview if the step is from before
  it existed.
- **Unsaved** is now "is the current step the saved one" (snapshot identity), so undoing back to what was
  saved clears the marker and Close stops asking.
- **Buttons:** `LoomAction.REDO` added. `loom.ui` (both copies) was patched: the Thread view's Undo is split
  into Undo | Redo (42 px each, same row) and the overview group gained an Undo | Redo row at y 141. The
  built-in `LoomLayout` got the same.

Verified: selftest 181 checks (`AS`: a snapshot restores the same Thread objects, numbers, patterns, places
and links after a delete + renumber + clear + new trigger, edits keep rising, and the result saves). Dev
client: delete Thread 2 -> Undo brought back Threads 2 and 3 -> Redo deleted again; a stroke inserted at 2
-> Undo removed it with the line back at 1 -> Redo restored it with the line at 2 -> a new stroke after Undo
disabled Redo; adding Thread 4, drawing in it and undoing twice left the Thread view for the overview.

## Embedded iotas and copying from a holder (2026-10-13)

A Thread can now hold **embedded iotas** - vectors, numbers, entities, lists - beside its patterns, and
the Loom can **copy a hex in from an iota holder** the player carries.

### Model and format

- `ThreadEntry` (sealed): `PlacedPattern` or `EmbeddedIota(CompoundTag iota, HexCoord origin)`. A Thread is
  a list of entries in program order; `WeaveThread.program(level)` makes each a live iota when it runs.
- An embedded iota is kept exactly as Hex keeps one on a focus (its serialised tag) and only deserialised at
  run time - an entity is looked up then; an iota of a type no longer installed comes out as garbage, as on a
  focus. `Hexweave.of` only checks that the tag names a type.
- **Escaping is Hex's own rule (user ruling: no auto-Consideration).** After a Consideration it is pushed as
  is; without one it mishaps, exactly as casting the focus would (checked in the 0.11.4 `CastingVM`
  bytecode: escaped iotas are pushed whatever their type).
- On the grid an embedded iota is a **knot** holding one point: no pattern or other knot may share it.
- Format **Version 3**: a `Patterns` entry is either `{Pattern, OriginQ, OriginR}` or `{Iota, OriginQ,
  OriginR}`. Version 2 reads the same way. `displaySample` (what watching clients get for the orb on a Loom)
  carries patterns only, so an embedded entity never rides along with the chunk.

### Trust (the one thing a client couldn't simply have drawn)

On Save the server accepts an embedded iota only if it equals (NBT) one already in the orb's stored weave or
one that an iota holder in the player's inventory would copy in (read with the same
`ThreadEntry.copiedFrom` the client uses). Otherwise "Entry N of Thread M embeds an iota you aren't
carrying". This stops a forged save naming an unseen entity or an addon's reference to stored things.

### Showing them

- Knot = a small trefoil drawn like a Hex stroke (`EmbedKnot`), in `IotaType.getColor` - the colour a focus
  holding that iota is tinted. Selected = gold, outside the bound = red.
- Tooltip (canvas and timeline): "N: Embedded <type name>" and the value from `IotaType.getDisplay`
  (cut short past 320 px).
- Timeline cells show the knot; overview miniatures show a coloured dot; the overview hover says
  "x patterns, y embedded"; the header says "Thread N - k entries" when there are embeds (the split is the
  timeline's to show - the full wording didn't fit beside "unsaved").

### Copying

- **Copy from...** (`LoomAction.COPY`, Thread view; patched into `loom.ui` under Clear at y 134) opens a
  list of every iota holder the player carries that would copy something: hotbar, off hand, then the rest,
  each with its icon, name and "x patterns, y embedded". Nothing moves - holders are only read.
- A holder's iota copies as: a list's elements in order (patterns drawn, the rest embedded); any other iota
  as one entry; an empty holder as nothing.
- Click a row: `ThreadDraft.fit` places them all at once - reading order (top row first), each pattern in
  any of its six turns (Hex knows a pattern by its angles, so turning changes nothing; mirroring would, so it
  is never tried), preferring places with a clear point around and off the rim, then allowing touching -
  and they go in as one run at the timeline line: one undo step. All or nothing.
- If they don't all fit, or on Shift-click, they are **placed by hand**: the next entry follows the cursor as
  a ghost (stroke colours where it fits, error colours where not), click places it at the timeline line,
  R turns a pattern, Esc stops ("Stopped, with N not placed"). The status line says "Placing 3 of 40: ...".

### Verified

- Selftest 193 checks: `AT` format v3 round trip, a knot refusing patterns and knots, a tag naming no type
  refused; `AU` Consideration + vector pushes it and the Tail sends it on, a bare vector mishaps; `AV` Save
  refuses an embed nobody carries, accepts one on a carried focus, keeps accepting it once stored, refuses a
  new uncarried one; `AW` a focus list copies as pattern/embed in order, fits a Thread after an existing
  pattern with the same program, 400 knots refused whole. `Val` now expects the dev command to embed a
  number rather than refuse it.
- Dev client with a real Loom: the picker listed both foci; copying a Consideration/vector/number/entity hex
  placed 4 patterns and 3 coloured knots in reading order; hovering a knot showed "2: Embedded Vector
  (1.00, 2.00, 3.00)"; a 40-pattern focus fell back to hand placement (ghost, place, turn, Esc); Save
  passed the server's carried-iota check ("Woven into the orb").

## Every arrow, every mob; Proximity reach (2026-10-13)

The user's report: two arrows close together let one through, and one mob of a swarm could slip past an
orb's defences. Two causes in `WovenOrbTriggers`: each sensor fired only for the *nearest* newcomer in a tick
(the rest were marked seen and never fired), and any fire started a 10-tick wait for the whole kind.

- **Proximity, Incoming Projectile and Targeted now fire once for each creature or projectile.** The wait is
  per entity (`SENSOR_DELAY`, 10 ticks, per hand and sensor), so one mob bobbing at the edge of reach still
  can't fire every tick, but a second arrow or mob is never held back by the first. An entity re-arriving
  during its own wait is missed, as anything during a wait is.
- **Cap, not drop:** at most `MAX_ARRIVALS_PER_TICK` (8) fire per sensor per hand per tick, nearest first;
  the rest stay unseen and arrive on the following ticks. Each fire is a whole weave run paid in media, so the
  cap only spreads a mob farm's worth over a few ticks.
- This relaxes the earlier "nothing can fire a hex every tick" rule for these three sensors: a stream of
  *different* arrows or mobs can now fire every tick (user request). Inputs, Gaze, Parry and Falling keep
  their per-kind waits.
- **Proximity reach** is now the trigger's own, 1 to 5 blocks from the holder's outline (`WeaveTrigger.reach`,
  `Kind.ranged()`, stored as `Reach`; a Proximity trigger stored without one reads as 1). Each distinct reach
  on an orb is watched separately, so a creature stepping within 5 and later within 1 sets off both
  triggers; `Hexweave.starts(kind, reach)` starts only the triggers of that reach.
- **Loom:** picking Proximity in the type picker opens a row of reaches 1-5 (current outlined; number keys
  1-5 work too). The node reads "Proximity, 3 blocks"; descriptions say "Casts once for each one".

Verified: selftest 201 checks. New `AX`: two arrows in one tick fire twice (once with each) and a third the
next moment fires too; 11 arrows fire 8 then 3 then none; a pig and a zombie stepping close together fire
twice; with reach-1 and reach-4 triggers a pig 3 blocks off starts only the reach-4 Thread, then stepping in
starts the reach-1 one; reach 5 round-trips, missing reach reads 1, reach 6 and a reach on On Use are
refused; the draft defaults, clamps and drops reach correctly. (It caught `Hexweave.of` rebuilding triggers
without their reach.) `AR` unchanged and passing. Dev client: the reach row opened after picking Proximity
and pressing 3 set "Trigger 1 now waits for: Proximity (3)" (label since reworded to "Proximity, 3 blocks").
