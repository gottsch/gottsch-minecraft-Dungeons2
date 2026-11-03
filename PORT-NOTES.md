# Dungeons2 3.0.0 → NeoForge 1.21.1: port notes

Branch `1.21.1-feature/3.0.0`, cut from the 3.0.0 release commit `b12b081` (tip of `1.20.1-main`).
Milestones 1 and 2 were a budgeted first pass in a cloud session, which could not compile anything:
its network policy blocked `maven.neoforged.net` (403 at the egress proxy) and Maven Central returned
429s. Milestone 3 was then done locally (2026-11-02) against the real NeoForge 21.1.117 jar.
**Main and tests compile, and `./gradlew test` passes: 1,890 tests, 0 failures, 2 skipped** (see "Tests").

## Milestones

| # | Milestone | State |
|---|---|---|
| 1 | Build skeleton (MDG) | **Done**; dependencies resolve locally |
| 2 | Mechanical pass | **Done** |
| 3 | Compile (main) | **Done**: 169 errors → 0 (local) |
| 4 | Tests | **Done**: 137 compile errors → 0; 1,890 pass (local, 2026-11-02) |
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
| `src/test/java/.../core/integration/TreasureIntegrationTest.java` | Tests the excluded facade | none |

How the exclusion works in `build.gradle`:
- The Treasure2 facade is excluded **by absolute file** (`portExcluded`). A pattern exclude would also drop the same-named stub.
- The stub comes in through `sourceSets.main.java.srcDir 'src/main/stubs/java'`.

`TreasureIntegration` is unchanged apart from the package sed. Only its private `Delegate` class names `mod.gottsch.forge.treasure2.*`, and those FQNs still need updating to whatever package the neo API ships.

Callers that now get the stubs:
- `ChestMarkerProcessor`
- `BossTierWiringTest` and `ChestMarkerProcessorTest`, which mention treasure2. `DependencyFloorsAgreeTest` checks the `gradle.properties` pairs (see "Tests"); its id map already lists treasure2, so restoring that integration only needs its `_version`/`_version_range` properties.

### Enemy Echelons restored (2026-11-03)
The `EchelonsIntegration` stub and its `portExcluded` entry are gone, and the real facade compiles against the Enemy Echelons API **1.1.0** for NeoForge (`gottsch:eechelonsapi-neoforge-1.21.1:1.1.0`, branch `1.21.1-api-feature/1.1.0` of `gottsch-minecraft-neoforge-EnemyEchelons`).
- `gradle.properties`: `eechelonsapi_version=1.1.0` and `eechelonsapi_version_range=[1.1.0,2.0.0)`. The API dropped its `1.21.1-` version prefix at 1.1.0, and NeoForge sorts `1.1.0` below `1.21.1-1.0.0`, so the range has to be in the new form.
- `build.gradle`: `compileOnly`, plus dev-only `localRuntime` lines for EE and Jade (`curse.maven:jade-324717:7545219`, the build Treasure2 uses), so `runClient` has EE present and Jade shows the scaled hearts and armor. `localRuntime` only feeds `runtimeClasspath`, so the unit tests still run with EE absent. Comment both lines out to check the EE-absent case in game.
- `neoforge.mods.toml`: an `eechelonsapi` block, `type="optional"`, `ordering="NONE"`.
- `Delegate` FQNs: `mod.gottsch.forge.eechelonsapi.*` → `mod.gottsch.neoforge.eechelons.*`. Nothing else changed. API 1.1.0 has `hasDifficultyCapability`, and `getDifficulty` returns `Integer` (it unboxes). Before 1.1.0, the NeoForge API's `apply` was a no-op on a freshly spawned mob, so 1.1.0 really is the floor.
- `EchelonSpawnEvent` and `MobRange` now reach the real facade. `EchelonConfigTest`'s EE-absent asserts still hold in the unit tests, because EE is compileOnly.
- Checked in game (2026-11-03): an Acid Skeleton from a dungeon spawner had `"eechelonsapi:difficulty": 3` (32 HP from a base of 20 with `hp_factor` 0.2). Still to check: bosses get pinned at 0, and the exclusion file `data/dungeons2/echelons/exclusions/dungeons2.json` keeps other echelon configs off.
- `difficultyOf` now checks `EnemyEchelonsApi.hasDifficulty`, not `hasDifficultyCapability`, before reading. On NeoForge, `getDifficulty` *creates* the attachment at -1, so a plain read, such as a Jade lookup, wrote `-1` into the NBT of any mob it looked at. The results are unchanged: unset is still empty. `difficultyNameOf` is new.

### Jade "Echelon" line (2026-11-03, NeoForge only so far)
- `core/integration/jade/DungeonsJadePlugin` (`@WailaPlugin`) and `EchelonJadeProvider` (uid `dungeons2:echelon`) add "Echelon: N", or "Echelon: N (Name)", to the tooltip of a mob EE has scaled. The server reads the difficulty through `EchelonsIntegration` and sends it with Jade's server data, because the attachment is server-only. The line shows nothing without EE.
- Jade is `compileOnly` (plus the dev `localRuntime`). Only Jade loads the `@WailaPlugin` class, so no other guard is needed. The lang keys are `config.jade.plugin_dungeons2.echelon`, `jade.dungeons2.echelon` and `jade.dungeons2.echelon_named`.
- The Jade API shapes were checked with javap against Jade 15.10.5+neoforge (`7545219`). Not yet compiled.
- **TODO Forge 1.20.1:** back-port it. See `C:\Development\claude\minecraft\forge\dungeons2\1.20.1\dungeons2-forge-1.20.1-JadeEchelonBackport-Nov03.md`.

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

### Main: 0 (was 169 at the first real compile)
Fixed locally, by category. All API shapes were checked with `javap` against
`build/moddev/artifacts/neoforge-21.1.117-merged.jar`.
- **Events:**
  - `bus = Bus.FORGE` dropped (GAME is the default)
  - `LivingAttackEvent`/`LivingHurtEvent` → `LivingIncomingDamageEvent`
  - `MobSpawnEvent.FinalizeSpawn` → `FinalizeSpawnEvent`, whose `getSpawner()` is now `Either<BlockEntity, Entity>`
  - `ProjectileImpactEvent` `SKIP_ENTITY` → `setCanceled(true)`
  - `hurtAndBreak(n, entity, EquipmentSlot)`
- **Registries:**
  - Forge's `getValue` → `get`
  - singular `BuiltInRegistries` names
  - `MOB_EFFECT.getHolder(id)` for `MobEffectInstance`
- **Armour:** `DungeonsArmorMaterials` is now a `DeferredRegister<ArmorMaterial>` holder (`ArmorMaterial` is a record on 1.21). Its durability moved to the item: `Properties.durability(BOOTS_DURABILITY)`.
- **Weapons:** `SwordItem`/`AxeItem` take `Properties.attributes(X.createAttributes(tier, dmg, speed))`.
- **Loot:**
  - the three JSON `Serializer`s → `MapCodec CODEC`s (`BossTierCondition` still rejects an unknown tier, now via `Codec.validate`)
  - `LootItemFunctionType<T>`
  - `reloadableRegistries().getLootTable(ResourceKey)`, with `LootTable.EMPTY` treated as missing
  - `AdvancementHolder`
- **Blocks:**
  - `codec()` on the five `BaseEntityBlock`s
  - `MobSetSpawnerBlock.properties()` → `defaultProperties()`, because it clashed with the new `BlockBehaviour.properties()`
- **Block entities and saved data:**
  - `SavedData.Factory` and `save/load(tag, Provider)`
  - `saveWithFullMetadata(provider)` / `loadWithComponents`
  - `ItemStack.save(provider)`
- **Worldgen:**
  - `JigsawPlacement.addPieces(..., PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.APPLY_WATERLOGGING)`
  - `checkStructurePresence(..., set.placement(), false)`
  - `chunk.status.ChunkStatus`
  - `DataResult.getOrThrow(Function)`
  - `MapCodec.unit`
  - processors registered with `MapCodec`
- **`DrySinglePoolElement`:** now passes `LiquidSettings.IGNORE_WATERLOGGING` to vanilla, which 1.20.5 added for exactly this, and forces it in `getSettings`. The JSON shape is unchanged.
- **Rendering:**
  - `Model.renderToBuffer(..., int color)`
  - `ModelPart.render(..., color)`
  - `VertexConsumer.addVertex(...).setColor(...)`, with no `endVertex`
- **Entities:**
  - `getDefaultGravity()` returns a double
  - `ignoreExplosion(Explosion)`
  - `AbstractArrow(type, owner, level, ItemStack.EMPTY, null)` plus `getDefaultPickupItem()`
- **Dungeon Map and compass:** ported, not excluded.
  - `MapId` and `setMapData(id, MapItemSavedData.load(tag, provider))`
  - `DataComponents.MAP_ID` / `MAP_COLOR` / `ITEM_NAME`
  - the stamp lives in `CUSTOM_DATA`; the compass's compound too, with `NbtUtils.readBlockPos(tag, key)`
  - `PathType` replaces `BlockPathTypes`
  - `DungeonMapCopyRecipe` takes a `CraftingInput`
- `SlotOptions`: the call to its own static `validate` now has to be qualified. The anonymous `Codec` had started resolving to DFU's new `Codec#validate`.

**Check in game, because the semantics may differ from Forge:**
- **Mirror Shield reflection:** a cancelled `ProjectileImpactEvent` should leave the bolt flying for `reflect()` to turn.
- **Boots of Shock Absorption:** `LivingIncomingDamageEvent` fires before armour, as Forge's `LivingHurtEvent` did. Check that the `[D2-BOOTS]` debug lines still show the cut.
- **Echelon scaling:** spawns from an entity-owned spawner (e.g. a spawner minecart) now get no origin, where Forge read the `BaseSpawner`. Cage spawners are unchanged.
- **Dungeon Map name:** it now uses `ITEM_NAME`, so it isn't italic and an anvil rename still wins. The map's tint and its "dungeon_map" item property should still show.
- **Waterlogging:** dry pool elements must not waterlog trim. Vanilla elements still do, as on 1.20.1.
- ~~**Mining Chest:** open one and check the stacks have their full counts (e.g. 64 coal, not 1). The SNBT fix is unit-tested through `ContainerHelper`, but the chest itself is only placed in game.~~ Works (2026-11-03).

### Tests: 0 compile errors (was 137); `./gradlew test` passes 1,890, skips 2
The 137 compile errors were mechanical:
- `getOrThrow(false, ...)` → `getOrThrow()` / `getOrThrow(Function)`
- `.codec().parse` on processor `MapCodec`s
- `CraftingInput.of(3, 3, items)` replaces the test's `CraftingContainer` grid
- `NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap())`
- `RegistrationInfo.BUILT_IN` for `register` (`MappedRegistry`'s constructor still takes a `Lifecycle`)
- `DataResult.Error::message`
- the provider forms of `save`/`load`/`loadAllItems`

Then the suite failed to start at all, and once it ran, 22 tests failed. What each turned out to be:

**Real bugs in the port (main source):**
- **`StructurePieces` crashed mod loading** with "Registry is already frozen". Forge unfroze the vanilla registries inside `enqueueWork`; NeoForge does not. The piece types now go through a `DeferredRegister` that hands back the existing constants, registered from `Registration.init`. This would have crashed the game on startup.
- **The Mining Chest would have held one of each item.** `MiningHaul.itemsSnbt()` wrote the pre-1.20.5 `Count:Nb`, and the 1.21 item codec reads a lowercase int `count`, defaulting to 1. It now writes `count:N`. The pedestal item in `RoomPedestalGenerator` was changed the same way (it was 1 anyway). `MiningHaulSnbtTest` caught it and now asserts the 1.21 key and type.
- **`minecraft:grass` was renamed `minecraft:short_grass` in 1.20.3.** It appeared 16 times across the eight `classic_*weathering` processor lists, where it would have become air. All changed. One template (`mud/13x17_1.nbt`, DataVersion 3465) also names it, but the game data-fixes templates on load, so that one is fine.

**Tests that assumed Forge's test environment:**
- **Working directory.** MDG's runner starts the game with `--gameDir .` in `build/minecraft-junit`, so tests reading `src/...`, `build.gradle` or `build/...` by relative path failed. The new `diagnostic/ProjectFiles.path(...)` walks up to `settings.gradle`; seven call sites use it.
- **The mods are loaded now.** On Forge a bare `Bootstrap` registered no mod blocks, so tests checked modded ids against the blockstate files in the DungeonBlocks jar. Under FML each mod is its own module, and `Class.getResource` stopped seeing another mod's assets. `ShippedBlockIdsTest` and `ShippedTemplateBlocksTest` now ask `BuiltInRegistries.BLOCK`, which is stronger. `MiningChestPlacementTest` gets its original assertion back: the cell holds `dungeons2:mining_chest`, not just air.
- **`ShippedTemplateBlocksTest`** now data-fixes each template the way `StructureTemplateManager` does before sweeping the palette.
- **Two tests building a `BlockEntityType`** also unfreeze `BLOCK_ENTITY_TYPE`, because on 1.21 it takes an intrusive holder too.
- **`ModLoadOrderTest`** reads `META-INF/neoforge.mods.toml` and ignores `neoforge` as it did `forge`.
- **1.21 folder names:** `structure/` and `loot_table/` in `ShippedMoodBiomesTest` and `ShippedTombsTest`.
- **`JigsawStripsStructureBlocksTest`** reflects the 4-argument `getSettings` (with `LiquidSettings`). `DrySinglePoolElementTest` asserts `shouldApplyWaterlogging()`.
- **`RendererCoverageTest`'s regex** had an unescaped `?` in `EntityType<?>`, so it matched nothing. It is now `<\?>`.
- **`DependencyFloorsAgreeTest` was rewritten.** On NeoForge, `build.gradle` and the toml both read `gradle.properties`. The test now checks:
  - each sibling's `X_version` and `X_version_range` floors agree, with two-part ranges such as `[1.3,2.0)` treated as `1.3.0`
  - `build.gradle` uses `${X_version}`
  - the shipped `neoforge.mods.toml` declares `X_version_range`

### Found in the first in-game run (2026-11-02)
- **Seven loot tables failed to load**, so their chests rolled nothing:
  - the 19 Gottsch Books entries used Forge's `minecraft:set_nbt`, and now use `minecraft:set_components` with `{"gottschbooks:book": "<id>"}`
  - the two counter tables' `minecraft:loot_table` entries now say `value`, not `name`

  The new `ShippedLootTablesDecodeTest` decodes every table with `LootTable.DIRECT_CODEC`; the old loot tests only read the JSON by hand.
- **No corner (angle) cobwebs.** This was a GottschCore bug, fixed in GottschCore. On 1.20.5+, `BlockState.hasProperty` is an identity check, because the properties sit in a reference map. `DecorationProcessor` looked for `BlockStateProperties.FACING`, and DungeonBlocks' `AngleCobwebBlock` is a GottschCore `FacingBlock` with its own `IFacingBlock.FACING`, so no web ever got a facing. It now looks the property up by name. Dungeons2 needs GottschCore 2.10.0 republished to mavenLocal to pick up the fix.
- **Brazier embers showed the missing-model cube.** This was a DungeonBlocks bug, fixed in DungeonBlocks: `forge_data` → `neoforge_data` in `brazier_embers_block.json`.
- **The `has_structure/dungeon` biome tag** listed `#forge:is_*` and pre-1.21 `#c:` names, all optional and so silently matching nothing. It now lists the NeoForge `c:is_*` tags.
- **Pot markers in 22 templates named entities that do not exist** (on either loader; this predates the port). Each was skipped with "Unknown entity id in placement". Fixed by rewriting the NBT strings:
  - `clay_pot` ×21 → `pot`
  - `clay_stone_pot` ×1 → `pot`
  - `blue_stone_pot` ×6 → `blue_pot`
  - `red_stone_pot` ×6 → `red_pot`

  The new `ShippedTemplateBlocksTest.everyTemplateEntityIdExists` walks every template's NBT for `entity` ids and checks each against the entity registry.

### Second in-game run (2026-11-03): the fixes above hold
- No missing textures or models.
- Corner cobwebs generate.
- Pots appear.
- Mining Chest: works.
- Echelon scaling (cage spawner, EE present): works. An Acid Skeleton had difficulty 3. See "Enemy Echelons restored".

The other in-game checks listed above (Mirror Shield, boots, Echelon scaling from an entity-owned spawner, bosses pinned at 0, map name, waterlogging) and books in chests have not been reported yet.

## Exact next steps (local)
1. ~~Publish the libraries; compile main~~ (done locally).
2. ~~Fix the test compile errors and run `./gradlew test`~~ (done: 1,890 pass).
3. Run `./gradlew probes` and `./gradlew releaseCheck`, which `test` excludes. They have not been run on NeoForge yet.
4. Port the Treasure2 integration once its neo API exists (Enemy Echelons is done, see "Enemy Echelons restored"):
   - delete the stubs and the `portExcluded` entries
   - update the `Delegate` FQNs
   - restore the `compileOnly` lines and the mods.toml optional dependency blocks
   - re-include `TreasureIntegrationTest`
5. ~~In-game: load once, confirm the `.nbt` templates DataFix, and generate a dungeon with Treasure2/EE absent~~ (done 2026-11-03). Still to check: the list under "Check in game", and books in chests.
