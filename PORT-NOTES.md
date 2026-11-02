# Dungeons2 3.0.0 → NeoForge 1.21.1: port notes

Branch `1.21.1-feature/3.0.0`, cut from the 3.0.0 release commit `b12b081` (tip of `1.20.1-main`).
This was a budgeted first pass in a cloud session. **Nothing has been compiled yet.** The session's
network policy blocked `maven.neoforged.net` (403 at the egress proxy) and Maven Central returned 429s,
so Gradle could not download NeoForge/MDG or publish the libraries. Every change below was made by
script and by reading the source. None of it has been checked by `javac`.

## Milestones

| # | Milestone | State |
|---|---|---|
| 1 | Build skeleton (MDG) | **Done, but `./gradlew dependencies` not run** (blocked) |
| 2 | Mechanical pass | **Done** |
| 3 | Compile | **Not started** (no compiler). Some categories were fixed ahead of time by script (see below) |
| 4 | Tests | Not started |
| 5 | PORT-NOTES.md | This file |

### 1. Build skeleton
- `build.gradle` rewritten for `net.neoforged.moddev` 2.0.78, NeoForge 21.1.117, Parchment 2024.11.17 / 1.21.1, Java 21. It follows the shape of DungeonBlocks's neo build.
- Gradle wrapper set to 8.10 (properties + jar copied from DungeonBlocks).
- `settings.gradle` copied from DungeonBlocks.
- `neoForge { unitTest { enable(); testedMod = mods.dungeons2 } }`.
- `-Xmaxerrs 5000` added to every `JavaCompile`.
- The custom tasks `probes`, `releaseCheck`, `spacingSweep` and `floorplan` were kept verbatim; only the `mainClass` packages changed. The `testRuntimeOnly slf4j-simple` line was dropped because the NeoForge unit-test runtime brings log4j. If `spacingSweep`/`floorplan` log noisily, add it back.
- `mods.toml` → `src/main/templates/META-INF/neoforge.mods.toml`, expanded by the `generateModMetadata` task (the MDG template pattern). It has:
  - `type="required"` dependencies on neoforge, minecraft, gottschcore `[2.10.0,3.0.0)`, dungeonblocks `[3.1.0,4.0.0)`, gmm `[1.1.0,2.0.0)` and gottschbooks `[1.3,2.0)`
  - the treasure2/eechelonsapi blocks replaced by `# TODO port` lines
  - `updateJSONURL` pointing at `1.21.1-main/update.json`
- `update.json` was rewritten for 1.21.1 → 3.0.0. **Note:** this replaced the 1.20.1 promos in this branch's copy.
- `pack.mcmeta`: the file is still expanded by nothing. MDG's `generateModMetadata` only reads `src/main/templates`, and the file has no `${}` tokens, so this is fine.
- Dropped: ForgeGradle, `fg.deobf`, `reobf*`, Terralith, Stronger Mobs Below, `flatDir` (`libs`, `../StrongerMobsBelow-1.20.1`), `../maven`, the Modrinth and illusivec4 repos, Treasure2 and eechelonsapi.
- jarJar of Gottsch Books uses the MDG `jarJar` configuration with `strictly [1.3,2.0)` and `prefer 1.3.0`.

### 2. Mechanical pass
- `git mv` from `mod/gottsch/forge/dungeons2` to `mod/gottsch/neo/dungeons2` (main and test), plus sed over all `.java` files.
- Library packages, read from each library's source tree on its port branch:
  - GottschCore: `mod.gottsch.neo.gottschcore` (imports rewritten)
  - gmm: `mod.gottsch.neo.gmm` (imports rewritten)
  - Gottsch Books: `mod.gottsch.neo.gottschbooks` (imports rewritten)
  - **DungeonBlocks kept `mod.gottsch.forge.dungeonblocks`**, so its 9 imports were left alone
- Namespaces (script in the commit `mechanical pass`):
  - `net.minecraftforge.{fml,api.distmarker}` → `net.neoforged.*`
  - `eventbus.api` → `net.neoforged.bus.api`
  - `event`/`common`/`client` → `net.neoforged.neoforge.*`
  - `ForgeRegistries.X` → `BuiltInRegistries.X` (singular names)
  - `RegistryObject` → `DeferredHolder`
  - `ForgeConfigSpec` → `ModConfigSpec`
  - `ForgeEventFactory` → `EventHooks`
  - `ForgeSpawnEggItem` → `DeferredSpawnEggItem`
  - `@Mod.EventBusSubscriber` → `@EventBusSubscriber` (import `net.neoforged.fml.common.EventBusSubscriber`; 20 files)
- `TickEvent.PlayerTickEvent` → `PlayerTickEvent.Post` (ReachedFloorEvent, DungeonMapEvent; `event.player` → `event.getEntity()`). `TickEvent.ClientTickEvent` → `ClientTickEvent.Post` (HazeParticles).
- `new ResourceLocation(a, b)` → `ResourceLocation.fromNamespaceAndPath`, and the one-arg form → `ResourceLocation.parse`. The regex treats a first argument containing `(`/`)` as the one-arg form. **Check any `parse(` call that has two arguments**: those were 2-arg calls whose first argument was a method call.
- Resource folders made singular with `git mv`:
  - `loot_tables`, `recipes`, `advancements`, `structures`
  - `tags/blocks`, `tags/items`, `tags/entity_types` (→ `entity_type`)
  - There was no `data/forge/`, so there were no `c` tags to move.
  - `pack_format` set to 34.
  - Path strings in tests were updated too (e.g. `"/data/dungeons2/structure"`, `CounterItemPlannerTest`'s `"loot_table"`). JSON *field* names called `loot_tables` in D2's own config codecs were left alone on purpose.
- `.nbt`, `.png` and `.ogg` files were only `git mv`'d (renames at 100% similarity). No CRLF strip or sed ran over binaries.

### 3. Fixes applied ahead of compiling (by script, unverified)
- `Dungeons` constructor is now `(IEventBus eventBus, ModContainer container)` and uses `container.registerConfig`. `Registration.init(IEventBus)` lost `FMLJavaModLoadingContext`.
- `DeferredHolder<T>` → `DeferredHolder<R, T>`. R comes from the `DeferredRegister<R>` the holder registers on, falling back to `Outer<?>` for generic T.
- `MapCodec`:
  - the `codec(Supplier)` factories of the 12 `extends StructureProcessor` classes use `RecordCodecBuilder.mapCodec`
  - `Registration`'s local `Codec<XProcessor> codec` variables became `MapCodec`
  - `DrySinglePoolElement.CODEC` and `DungeonStructure.CODEC` (`simpleCodec`) became `MapCodec`
- BlockEntities (Chest/Pot/Sarcophagus/SpawnerMarker/DungeonSpawner): `loadAdditional`/`saveAdditional(CompoundTag, HolderLookup.Provider)`.
- `defineSynchedData(SynchedEntityData.Builder)` in SmashShard, Boulder and AnnihilationRay.
- `LivingEvent.LivingTickEvent` → `EntityTickEvent.Pre` (BossWebTearEvent; `event.getEntity()` is now an `Entity`, and the existing `instanceof Mob` check covers it). The unused `LivingEvent` import is left there.
- `MobEffectEvent.Applicable`: `setResult(Applicable.Result.DO_NOT_APPLY)` and `getEffectInstance().is(MobEffects.X)` (ShockAbsorptionEvent).
- `SpawnPlacementRegisterEvent` → `RegisterSpawnPlacementsEvent`, `SpawnPlacements.Type.X` → `SpawnPlacementTypes.X` (CommonSetup, 41 calls). **Check the `Operation` enum and the argument order.**

## Excluded / stubbed files
| File | Why | Replacement |
|---|---|---|
| `src/main/java/.../core/integration/TreasureIntegration.java` | Treasure2 has no NeoForge API to compile against (`compileOnly` dropped) | `src/main/stubs/java/.../integration/TreasureIntegration.java`: same public API, `isLoaded()==false`, no-ops |
| `src/main/java/.../core/integration/EchelonsIntegration.java` | The Enemy Echelons API neo port is 1.0.0; D2 needs 1.1.0 | `src/main/stubs/java/.../integration/EchelonsIntegration.java`: same, no-op |
| `src/test/java/.../core/integration/TreasureIntegrationTest.java` | Tests the excluded facade | none |

How the exclusion works in `build.gradle`:
- The two facades are excluded **by absolute file** (`portExcluded`). A pattern exclude would also drop the same-named stubs.
- The stubs come in through `sourceSets.main.java.srcDir 'src/main/stubs/java'`.

The originals are unchanged apart from the package sed. Only their private `Delegate` classes name `mod.gottsch.forge.treasure2.*` / `mod.gottsch.forge.eechelonsapi.*`, and those FQNs still need updating to whatever package the neo APIs ship.

Callers that now get the stubs:
- `ChestMarkerProcessor`
- `MobRange`
- `EchelonSpawnEvent`
- `BossTierWiringTest`, `ChestMarkerProcessorTest` and `DependencyFloorsAgreeTest`, which mention treasure2/eechelonsapi. `DependencyFloorsAgreeTest` pins the mods.toml ranges against build.gradle, so **expect it to fail until it reads `neoforge.mods.toml` and the new property names**.

Dungeon Map is **not excluded yet**, but it is a known rework (see below).

## Library coordinates
Taken from each library's `gradle.properties` / `build.gradle` on its port branch. Not yet confirmed in `~/.m2`, because the publishing was blocked.

| Library | Coordinate | Branch | Package |
|---|---|---|---|
| GottschCore | `gottsch:gottschcore-neoforge-1.21.1:2.10.0` | `gottsch-minecraft-neoforged-GottschCore` @ `1.21.1-feature/2.10.0` | `mod.gottsch.neo.gottschcore` |
| DungeonBlocks | `gottsch:dungeonblocks-neoforge-1.21.1:3.1.0` | `gottsch-minecraft-neoforge-DungeonBlocks` @ `1.21.1-feature/v3.1.0` | `mod.gottsch.forge.dungeonblocks` (not renamed) |
| Monster Manual | `gottsch:monster-manual-neoforge-1.21.1:1.1.0` | `gottsch-minecraft-Monster-Manual` @ `neoforge-1.21.1-feature/1.1.0-sync` | `mod.gottsch.neo.gmm` |
| Gottsch Books | `gottsch:gottschbooks-neoforge-1.21.1:1.3.0` (jarJar) | `gottsch-minecraft-GottschBooks` @ `1.21.1-feature/1.30` | `mod.gottsch.neo.gottschbooks` |

**Surprise:** DungeonBlocks and Monster Manual still pin `gottschcore_version=2.7.0` in their own `gradle.properties`.
- To publish them, either have 2.7.0 in mavenLocal or run with `-Pgottschcore_version=2.10.0`.
- D2 itself uses 2.10.0, and Gradle will pick the higher version on D2's classpath.

## Compile error counts
**Unknown.** No compile has run. Static inventory of what is still unported in `src/main/java` (grep hits / files):

| Category | Hits/files | Examples | Notes |
|---|---|---|---|
| Dungeon Map (MapId component, `MapItem.create`, `MapDecorationTypes`, item NBT) | ~15 / 5 | `core/map/DungeonMaps.java`, `BlankDungeonMapItem.java`, `ClientSetup.java` (ItemProperties on `stack.getTag()`), `DungeonMapCopyRecipe.java` (`CraftingContainer` → `CraftingInput`) | Real rework. If it is costly, exclude it like the integrations |
| Item NBT → `DataComponents.CUSTOM_DATA` | 5 / 2 | `DungeonMaps`, `ClientSetup` | part of Dungeon Map |
| Loot tables → `ResourceKey<LootTable>` / `reloadableRegistries().getLootTable` | ~4 / 3 | `WeaponRackFiller.java` (`getLootData`), `EntitySpawner.java`, `ChestMarkerProcessor` | |
| `appendHoverText(stack, TooltipContext, list, flag)` | 5 / 4 | `MirrorShield.java`, `BlankDungeonMapItem.java` | |
| `ItemStack.of(tag)` / `stack.save(tag)` → `parse/save(provider)` | 2 / 2 | `EntitySpawner`, `WeaponRackFiller` | |
| `finalizeSpawn` (no tag arg; `MobSpawnType` → `EventHooks.finalizeMobSpawn`) | 1 / 1 | `EntitySpawner` | |
| `LivingHurtEvent` / `LivingAttackEvent` → `LivingIncomingDamageEvent` / `LivingDamageEvent.Pre` | 4 / 3 | `ShockAbsorptionEvent`, `MirrorShieldEvent` | **semantics differ, so review by hand** |
| Explosion/ProjectileImpact event API | 5 / 2 | `BossRoomProtectionEvent`, `MirrorShieldEvent` | `ProjectileImpactEvent` is no longer cancelable that way; `ExplosionEvent.Detonate` OK |
| `SimpleCriterionTrigger` (codec-based `TriggerInstance`) | 5 / 1 | `ReachedFloorTrigger` | |
| Recipe serializer / `CraftingContainer` → `CraftingInput` | 6 / 2 | `DungeonMapCopyRecipe`, `Registration` | |
| `Attributes.*` are `Holder<Attribute>` | 3 / 1 | `DungeonsEntities` | |
| `MobEffects.*` are `Holder<MobEffect>` | 3 / 3 | `DungeonFogEvents`, `ShockAbsorptionEvent` | `hasEffect(holder)` is fine |
| `PieceBeardifierModifier` | 2 / 1 | `DungeonPiece` | Check it still exists in neo 21.1 (`net.neoforged.neoforge.common.world`) |
| `DataPackRegistryEvent` / `AddReloadListenerEvent` | 6 / 3 | `MotifConfigRegistries`, `DungeonGenerationConfigRegistries` | Probably only the event bus wiring changes |
| Client events (`EntityRenderersEvent`, `ViewportEvent`) | 6 / 2 | `ClientSetup`, `DungeonFogEvents` | Usually `bus = MOD` and `value = Dist.CLIENT` on `@EventBusSubscriber` |
| `EventHooks.onEntityDestroyBlock` | 1 / 1 | `AnnihilationRay` | Check the signature with javap |
| `DeferredSpawnEggItem` ctor (Supplier of `EntityType<? extends Mob>`) | 1 file | `DungeonsItems` (or wherever the eggs register) | |
| `StructureProcessor.processBlock` / `finalizeProcessing` signatures | 12 files | `templatesystem/*Processor` | unchanged in 1.21, I believe, so check |
| `Structure.findGenerationPoint`/`StructureType` | 2 | `DungeonStructure`, `Registration` | `StructureType` is now a `MapCodec` lambda: `() -> CODEC` |
| Test-side: headless `WorldGenLevel` stub, loader tests reading shipped JSON | ? | `src/test/...` | Rebuild against 1.21.1; leave it for later if it is large |

Expect the usual long tail beyond these:
- `@EventBusSubscriber(bus = Bus.FORGE)` → drop `bus` (GAME is the default)
- `Event.Result` removals
- `level.getServer().getLootData()`
- `BlockEntityType.Builder.build(null)`
- `ResourceLocation.parse` misfires
- `GottschCore` API renames between 2.9.0 (Forge) and 2.10.0 (neo)

## Exact next steps (local)
1. Publish the libraries to mavenLocal, GottschCore first. Use `-Pgottschcore_version=2.10.0` for DungeonBlocks and gmm if 2.7.0 isn't in mavenLocal.

   ```bash
   cd ../gottsch-minecraft-neoforged-GottschCore && ./gradlew publishToMavenLocal -x test
   ```

   Then the same in DungeonBlocks, Monster Manual and Gottsch Books. Confirm the coordinates in `~/.m2/repository/gottsch/`.
2. In D2, run `./gradlew dependencies --configuration compileClasspath | grep gottsch` and fix any coordinate in `build.gradle`.
3. Run `./gradlew compileJava 2>&1 | grep -c error:` to get the baseline count. Group the errors with `grep 'error:' | sed 's/.*error: //' | sort | uniq -c | sort -rn | head -40`.
4. Work through the table above, biggest and most mechanical first. If Dungeon Map is expensive, exclude its 5 files the same way as the integrations and note it.
5. Fix `DependencyFloorsAgreeTest`, which reads `mods.toml` and the build.gradle version strings. Then run `./gradlew compileTestJava test`. The planner/POJO tests should pass unchanged; any difference is a port bug.
6. Port the integrations once neo APIs exist:
   - delete the stubs and the `portExcluded` entries
   - update the `Delegate` FQNs
   - restore the `compileOnly` lines and the mods.toml optional dependency blocks
   - re-include `TreasureIntegrationTest`
7. In-game: load once, confirm the `.nbt` templates DataFix, and generate a dungeon with Treasure2/EE absent.
