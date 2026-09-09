/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks;
import mod.gottsch.forge.dungeons2.core.block.entity.SpawnerMarkerBlockEntity;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import mod.gottsch.forge.dungeons2.core.util.VanillaSpawnerNbt;
import mod.gottsch.forge.gottschcore.mobset.MobSetDataRegistry;
import mod.gottsch.forge.gottschcore.mobset.WeightedMob;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import mod.gottsch.forge.dungeons2.core.world.BossRoomRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Swaps the authored {@code dungeons2:spawner_marker} block for Dungeons2's invisible mob-set
 * spawner, with the mob set attached. Backlog #10, Dungeons2 side.
 *
 * <h2>Why a marker BLOCK, and not the {@code d2:spawner} DATA marker the README used to document</h2>
 * <p>The first version of this matched a DATA structure block, on the reasoning that a processor
 * sees every block in a template. <strong>That is true of a raw
 * {@code StructureTemplate.placeInWorld} and false of a jigsaw pool element</strong>, which is the
 * only way Dungeons2 places authored content: {@code SinglePoolElement.getSettings} installs
 * {@code BlockIgnoreProcessor.STRUCTURE_BLOCK} <em>before</em> appending the pool's own processors,
 * and that returns {@code null} for a structure block &mdash; <em>removing</em> it from the
 * placement list rather than replacing it. The pool's processors then receive a list it is already
 * absent from.</p>
 *
 * <p>The symptom was a good one to remember: the marked cell showed neither a spawner nor a visible
 * structure block, but <em>the terrain the dungeon was carved out of</em> (a coal ore, as reported),
 * because nothing ever wrote that cell. {@code JigsawStripsStructureBlocksTest} pins the mechanism.
 * Village Dungeons keys its own spawner processor on marker blocks; that looked like a stylistic
 * choice and is in fact forced.</p>
 *
 * <h2>The trade the block form was thought to make, and why it was wrong</h2>
 * <p>This section used to read: "a DATA marker carried a free-text string, so it could name its own
 * mob set per cell; a block cannot", and concluded that a motif wanting a second set had to register
 * a second marker block. <strong>The premise was false.</strong> A structure template stores
 * block-entity NBT per cell and hands it to a processor as {@code current.nbt()} &mdash;
 * {@code ChestMarkerProcessor} was already reading a per-marker loot table that way while this note
 * claimed it was impossible. What a block could not do was carry text with <em>no block entity</em>.
 *
 * <p>As of 2026-09-03 {@code dungeons2:spawner_marker} has one, so every codec field below is a pool
 * <em>default</em> that an individual marker may override: {@code mobSetName}, {@code proximity},
 * {@code minMobs}, {@code maxMobs}, {@code probability} and {@code type}. A marker that states nothing behaves exactly as
 * it did before, which is why no shipped template needed touching. {@code marker_block} stays a
 * codec field &mdash; a second marker block is still legitimate, it is just no longer the only way
 * to get a second set.</p>
 *
 * <h2>What this does NOT do</h2>
 * <p>It does not validate that the named mob set exists. {@code MobSetDataRegistry} is populated
 * from datapacks at reload time and a processor runs during worldgen, so "not yet loaded" and "does
 * not exist" are indistinguishable here; the block entity resolves the name when it fires. The
 * shipped sets are swept by {@code ShippedMobSetsTest} instead, which is where a typo becomes a
 * build failure.</p>
 *
 * <p>Implements {@link LevelIndependentProcessor} because it reads nothing but the block it was
 * handed &mdash; see {@code PieceProcessors} for why that split matters at a chunk seam. In
 * practice it never fires on a procedurally-built piece, since only an authored template contains
 * the marker.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
public class SpawnerMarkerProcessor extends StructureProcessor implements LevelIndependentProcessor {

    /** The authoring marker block, as documented in {@code structures/README.md}. */
    public static final ResourceLocation DEFAULT_MARKER_BLOCK =
            new ResourceLocation(Dungeons.MOD_ID, "spawner_marker");

    /**
     * Certainty. Every marker authored before {@code probability} existed states nothing, and a pool
     * that states nothing lands here &mdash; so the field is inert until somebody opts in, which is
     * what {@code processBlock} relies on to leave existing worlds' random streams untouched.
     */
    public static final float DEFAULT_PROBABILITY = 1.0F;

    private static final String MOB_SET_NAME = "mobSetName";
    private static final String MIN_MOBS = "minMobs";
    private static final String MAX_MOBS = "maxMobs";
    private static final String PROXIMITY = "proximity";

    private final ResourceLocation mobSet;
    private final Optional<ResourceLocation> bossMobSet;
    private final Optional<ResourceLocation> escortMobSet;
    private final Optional<ResourceLocation> rangedEscortMobSet;
    private final ResourceLocation markerBlock;
    private final double proximity;
    private final int minMobs;
    private final int maxMobs;
    private final float probability;
    private final SpawnerConfig.Kind kind;

    /** The proximity form, which is what every marker authored before vanilla spawners meant. */
    public SpawnerMarkerProcessor(ResourceLocation mobSet, ResourceLocation markerBlock, double proximity,
                                  int minMobs, int maxMobs) {
        this(mobSet, markerBlock, proximity, minMobs, maxMobs, SpawnerConfig.Kind.PROXIMITY);
    }

    public SpawnerMarkerProcessor(ResourceLocation mobSet, ResourceLocation markerBlock, double proximity,
                                  int minMobs, int maxMobs, SpawnerConfig.Kind kind) {
        this(mobSet, Optional.empty(), Optional.empty(), Optional.empty(),
                markerBlock, proximity, minMobs, maxMobs, kind);
    }

    public SpawnerMarkerProcessor(ResourceLocation mobSet, Optional<ResourceLocation> bossMobSet,
                                  ResourceLocation markerBlock, double proximity,
                                  int minMobs, int maxMobs, SpawnerConfig.Kind kind) {
        this(mobSet, bossMobSet, Optional.empty(), Optional.empty(),
                markerBlock, proximity, minMobs, maxMobs, kind);
    }

    /**
     * The form without a probability, which is every caller that predates it. Delegates at
     * {@link #DEFAULT_PROBABILITY}, so a pool that says nothing converts every marker exactly as
     * before.
     */
    public SpawnerMarkerProcessor(ResourceLocation mobSet, Optional<ResourceLocation> bossMobSet,
                                  Optional<ResourceLocation> escortMobSet,
                                  Optional<ResourceLocation> rangedEscortMobSet,
                                  ResourceLocation markerBlock, double proximity,
                                  int minMobs, int maxMobs, SpawnerConfig.Kind kind) {
        this(mobSet, bossMobSet, escortMobSet, rangedEscortMobSet, markerBlock, proximity,
                minMobs, maxMobs, DEFAULT_PROBABILITY, kind);
    }

    public SpawnerMarkerProcessor(ResourceLocation mobSet, Optional<ResourceLocation> bossMobSet,
                                  Optional<ResourceLocation> escortMobSet,
                                  Optional<ResourceLocation> rangedEscortMobSet,
                                  ResourceLocation markerBlock, double proximity,
                                  int minMobs, int maxMobs, float probability,
                                  SpawnerConfig.Kind kind) {
        this.mobSet = mobSet;
        this.bossMobSet = bossMobSet;
        this.escortMobSet = escortMobSet;
        this.rangedEscortMobSet = rangedEscortMobSet;
        this.markerBlock = markerBlock;
        this.proximity = proximity;
        this.minMobs = minMobs;
        this.maxMobs = maxMobs;
        this.probability = probability;
        this.kind = kind;
    }

    /**
     * {@code mob_set} is required on purpose: a spawner with no set is a block that does nothing,
     * and defaulting it would make that failure silent. The tuning knobs default, because they have
     * defensible values and most authors will never set them.
     */
    public static Codec<SpawnerMarkerProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        Codec<SpawnerMarkerProcessor> codec = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("mob_set").forGetter(p -> p.mobSet),
                // Strict, unlike its neighbours: a misspelled value here would otherwise decode to
                // Optional.empty() and the boss marker would quietly take the pool's ordinary set --
                // a boss room whose boss is a skeleton, reported by nothing. The neighbours predate
                // #31 and are left alone; see ChestMarkerProcessor's codec note.
                Codecs.strictOptionalFieldOf(ResourceLocation.CODEC, "boss_mob_set")
                        .forGetter(p -> p.bossMobSet),
                // The boss's guard, split by the role the template authored. Strict for the same
                // reason as boss_mob_set: a misspelling here would silently garrison a large
                // dungeon's boss room with the small tier's mobs, which is the fault this whole
                // set of fields exists to remove.
                Codecs.strictOptionalFieldOf(ResourceLocation.CODEC, "escort_mob_set")
                        .forGetter(p -> p.escortMobSet),
                Codecs.strictOptionalFieldOf(ResourceLocation.CODEC, "ranged_escort_mob_set")
                        .forGetter(p -> p.rangedEscortMobSet),
                ResourceLocation.CODEC.optionalFieldOf("marker_block", DEFAULT_MARKER_BLOCK)
                        .forGetter(p -> p.markerBlock),
                // Required, and deliberately NOT defaulted -- same reasoning as the scheme slot's
                // (see SpawnerConfig): a default here is a number in Java deciding how far away
                // every authored ambush fires, invisible to whoever is authoring it. The one
                // asymmetry with the slot is that this side requires it for a vanilla marker too;
                // the value is simply unused there, and a second cross-field rule in a processor
                // that has no validate() hook would cost more than it saves.
                Codec.DOUBLE.fieldOf("proximity").forGetter(p -> p.proximity),
                Codec.INT.optionalFieldOf("min_mobs", 1).forGetter(p -> p.minMobs),
                Codec.INT.optionalFieldOf("max_mobs", 3).forGetter(p -> p.maxMobs),
                // How often an authored marker produces a spawner at all -- the authored answer to
                // what `min_count: 0` does for the procedural slot. A pool default like its
                // neighbours, overridable per marker.
                //
                // floatRange, not FLOAT, and so NOT clamped the way the marker's own NBT is: a
                // datapack value is authored once and read by a human, so "probability": 5.0 should
                // be a load error (#31's posture), whereas marker NBT arrives during worldgen where
                // throwing is not an option.
                //
                // And STRICT, unlike the min_mobs/max_mobs above: DFU's own optionalFieldOf(name,
                // default) SWALLOWS a decode failure and hands back the default, so an out-of-range
                // value there would silently read as 1.0 and the range check would be decorative.
                // The neighbours predate #31 and are left alone; a new field does not get to.
                Codecs.strictOptionalFieldOf(Codec.floatRange(0.0F, 1.0F), "probability",
                        DEFAULT_PROBABILITY).forGetter(p -> p.probability),
                // Same default and the same reason as the scheme slot's: every marker authored
                // before vanilla spawners existed means the ambush block.
                SpawnerConfig.Kind.CODEC.optionalFieldOf("type", SpawnerConfig.Kind.PROXIMITY)
                        .forGetter(p -> p.kind)
        ).apply(instance, SpawnerMarkerProcessor::new));
        return codec;
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos piecePos,
                                                             BlockPos relativePos,
                                                             StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo current,
                                                             StructurePlaceSettings settings) {
        if (!isSpawnerMarker(current)) {
            return current;
        }
        // The marker's own NBT wins over this processor's codec fields, key by key. Resolved once,
        // before the kind branch, because `type` is itself overridable -- a template may hold an
        // ambush marker and a visible cage side by side.
        Overrides overrides = Overrides.of(current);

        // ONE source for this block, not one per use. settings.getRandom(pos) is seeded FROM the
        // position, so a second call returns an equivalently-seeded source and the chance roll
        // would come off the same stream position as the mob draw -- correlated, not independent.
        RandomSource random = settings.getRandom(current.pos());

        if (rollsOut(overrides, random)) {
            Dungeons.LOGGER.debug("[D2-SPAWNER] {} rolled out at {} (probability {})",
                    markerBlock, current.pos().toShortString(),
                    overrides.probability(this.probability));
            // Air, and NOT null. Returning null skips placement, which leaves whatever the piece
            // already generated in that cell -- inside a room that is not guaranteed to be air.
            // PotMarkerProcessor states the same reasoning at its own roll: an empty roll must
            // leave an empty cell rather than a marker.
            //
            // Distinct on purpose from the "no usable mobs" branch below, which leaves the marker
            // UNCONVERTED because that case is a fault somebody needs to see. This one is an
            // authored decision, so it leaves nothing behind.
            return new StructureTemplate.StructureBlockInfo(current.pos(),
                    Blocks.AIR.defaultBlockState(), null);
        }

        // The tier's set when this marker declared itself the boss, otherwise the ordinary chain.
        // Resolved once here and passed down, so the log line, the vanilla cage and the proximity
        // block cannot disagree about which set fired -- the same reason Overrides itself exists.
        ResourceLocation resolvedSet = resolveMobSet(current, overrides);
        // Diagnostic, because every failure downstream of here is invisible: the block this
        // produces cannot be seen, and a spawner that never fires looks exactly like a spawner
        // that was never placed. One line per conversion, at the position it happened.
        //
        //   grep "D2-SPAWNER" run/logs/dungeons2.log
        //
        // Absent => the marker was never matched (wrong block id, or the template did not place).
        // Present but no mobs => look in run/logs/gottschcore.log instead: the block entity's own
        // "proximity met" / "self-destructing" lines are GottschCore's, and that file has its own
        // [logging] level in config/gottschcore-common.toml.
        Dungeons.LOGGER.debug("[D2-SPAWNER] {} -> {} at {} (set {})",
                markerBlock, overrides.kind(kind).getSerializedName(),
                current.pos().toShortString(), resolvedSet);

        if (overrides.kind(kind) == SpawnerConfig.Kind.VANILLA) {
            // The same source the probability roll used, rather than a fresh getRandom(pos) -- see
            // the note where it is created.
            CompoundTag vanilla = vanillaSpawnerTag(random, overrides, resolvedSet);
            if (vanilla == null) {
                // No resolvable mobs, so there is nothing to put in the cage. Leave the marker
                // in place rather than emit an empty spawner: vanilla's own default is a pig, and
                // an unconverted marker is at least visibly wrong to whoever authored it.
                Dungeons.LOGGER.warn("[D2-SPAWNER] mob set {} resolved to no usable mobs at {};"
                        + " leaving the marker unconverted", resolvedSet,
                        current.pos().toShortString());
                return current;
            }
            return new StructureTemplate.StructureBlockInfo(current.pos(),
                    Blocks.SPAWNER.defaultBlockState(), vanilla);
        }

        // The block lookup is the ONLY part of this that needs a populated Forge registry, which is
        // why everything either side of it is separately callable -- see SpawnerMarkerProcessorTest.
        return new StructureTemplate.StructureBlockInfo(current.pos(),
                DungeonsBlocks.MOB_SET_SPAWNER.get().defaultBlockState(),
                spawnerTag(overrides, resolvedSet));
    }

    /**
     * Whether this marker rolls itself out of existence rather than becoming a spawner.
     *
     * <p>Separately callable for the reason the class note gives about the rest of this processor:
     * the marker <em>match</em> needs {@code dungeons2:spawner_marker} in a populated Forge registry
     * and no headless test has one, while this decision needs nothing but the NBT and a random
     * source. So the whole of the probability rule is testable, and
     * {@code SpawnerMarkerProcessorTest} tests it directly.</p>
     *
     * <p><strong>Consumes a random value only when the probability is under 1.0.</strong> That is
     * not an optimisation. Rolling unconditionally would draw a {@code nextFloat()} ahead of the mob
     * draw on every marker in every world, changing which mob already-generated spawners show, for a
     * feature nobody opted into. A marker that states nothing must take byte-identical code to the
     * code that ran before this field existed.</p>
     */
    boolean rollsOut(Overrides overrides, RandomSource random) {
        float probability = overrides.probability(this.probability);
        return probability < 1.0F && random.nextFloat() >= probability;
    }

    /** Matches {@code RoomSpawnerGenerator}, so both routes name the same block entity. */
    static final String VANILLA_SPAWNER_ENTITY = "minecraft:mob_spawner";

    /**
     * The tag for a vanilla cage drawing from this processor mob set, or {@code null} when the set
     * resolves to nothing vanilla could spawn.
     *
     * <h2>Here the two routes DO share code, unlike the proximity pair</h2>
     * <p>The proximity spawner builds the same tag by two different encodings on the two routes and
     * cannot share an implementation, which is the whole reason {@code SpawnerTagParityTest} exists.
     * The vanilla tag has no such split: {@code VanillaSpawnerNbt} emits SNBT, this side parses it
     * and the procedural side posts it through {@code BlockEntityData}. One builder, so there is
     * nothing here that can drift out of step.</p>
     *
     * <p>Unlike the floor index, the mob draw here CAN be seeded properly &mdash;
     * {@code settings.getRandom(pos)} is position-derived, so the same template in the same place
     * shows the same mob.</p>
     */
    CompoundTag vanillaSpawnerTag(RandomSource random, Overrides overrides) {
        return vanillaSpawnerTag(random, overrides, overrides.mobSet(mobSet));
    }

    /** The form used in {@code processBlock}, taking the already-resolved set. */
    CompoundTag vanillaSpawnerTag(RandomSource random, Overrides overrides,
                                  ResourceLocation resolvedSet) {
        List<WeightedMob> mobs = MobSetDataRegistry.get(resolvedSet)
                .map(VanillaSpawnerNbt::usableMobs)
                .orElseGet(List::of);
        if (mobs.isEmpty()) {
            return null;
        }
        int total = mobs.stream().mapToInt(WeightedMob::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        String shown = mobs.get(mobs.size() - 1).id().toString();
        for (WeightedMob mob : mobs) {
            roll -= mob.weight();
            if (roll < 0) {
                shown = mob.id().toString();
                break;
            }
        }
        int min = overrides.minMobs(minMobs);
        int max = overrides.maxMobs(maxMobs);
        int spawnCount = min + (max > min ? random.nextInt(max - min + 1) : 0);

        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", VANILLA_SPAWNER_ENTITY);
            tag.put(VanillaSpawnerNbt.SPAWN_DATA,
                    TagParser.parseTag(VanillaSpawnerNbt.spawnData(shown)));
            // SpawnPotentials is a LIST, and TagParser only parses a compound -- so it is wrapped
            // and unwrapped, the same trick DungeonPiece#parseNbtValue uses for the other route.
            tag.put(VanillaSpawnerNbt.SPAWN_POTENTIALS,
                    TagParser.parseTag("{v:" + VanillaSpawnerNbt.spawnPotentials(mobs) + "}").get("v"));
            VanillaSpawnerNbt.tuning(spawnCount).forEach((k, v) -> tag.putInt(k, Integer.parseInt(v)));
            return tag;
        } catch (Exception malformed) {
            Dungeons.LOGGER.error("[D2-SPAWNER] could not build vanilla spawner tag for set {}: {}",
                    mobSet, malformed.toString());
            return null;
        }
    }

    /**
     * Whether this block is the authored marker.
     *
     * <p>Compared by <strong>registry id</strong> rather than by {@code state().is(block)}: this is
     * reachable without a populated Forge registry, which is what lets the test cover it, and it
     * makes {@code marker_block} a genuine datapack knob rather than a constant with a codec in
     * front of it.</p>
     */
    /**
     * The set this marker's pool offers it: the tier's {@code boss_mob_set} when the marker declared
     * itself the boss, otherwise this processor's ordinary {@code mob_set}.
     *
     * <h2>The boss rung outranks the marker's own {@code mobSetName}</h2>
     * <p>Backwards from every other field on this marker, and for the reason
     * {@code ChestMarkerProcessor#bossLootTable} spells out: {@code boss: true} declares a ROLE and
     * asks the pool for the value, so a pool that could be overridden by the template would leave
     * the flag doing nothing on the one template most likely to also name a set. Only the pool knows
     * which size of dungeon drew this room; the template cannot.</p>
     *
     * <p>A boss marker in a pool that declares no {@code boss_mob_set} falls straight through to the
     * ordinary chain rather than failing &mdash; so the flag is inert outside {@code end_rooms},
     * which is what lets a boss room be probed by tooling and tests that use an ordinary
     * processor list.</p>
     */
    private ResourceLocation resolveMobSet(StructureTemplate.StructureBlockInfo current,
                                           Overrides overrides) {
        if (bossMobSet.isPresent() && flagged(current, SpawnerMarkerBlockEntity.BOSS)) {
            return bossMobSet.get();
        }
        // Ranged first: the two escort flags are meant to be exclusive, and checking the more
        // specific one first makes a marker that carries both behave predictably rather than
        // depending on field order. BossRoomAuthoringTest rejects a marker that carries both.
        if (rangedEscortMobSet.isPresent()
                && flagged(current, SpawnerMarkerBlockEntity.RANGED_ESCORT)) {
            return rangedEscortMobSet.get();
        }
        if (escortMobSet.isPresent() && flagged(current, SpawnerMarkerBlockEntity.ESCORT)) {
            return escortMobSet.get();
        }
        return overrides.mobSet(mobSet);
    }

    /** Whether this marker set the given boolean role flag. */
    static boolean flagged(StructureTemplate.StructureBlockInfo current, String flag) {
        CompoundTag nbt = current.nbt();
        return nbt != null && nbt.getBoolean(flag);
    }

    /** Whether this individual marker declared itself the dungeon's boss spawner. */
    static boolean isBossMarker(StructureTemplate.StructureBlockInfo current) {
        return flagged(current, SpawnerMarkerBlockEntity.BOSS);
    }

    /**
     * Records the boss room's bounds as it generates, for {@code BossRoomProtectionEvent}.
     *
     * <p>This is the <strong>only</strong> moment the room's extent is knowable exactly and cheaply.
     * Afterwards it cannot be recovered: the structure cannot be queried from inside a dungeon (the
     * chunk holds no references to it past eight chunks from the start), and measuring the room from
     * the world by flooding it works only until the weathering pass punches a hole through a wall
     * into a corridor, which it does routinely. Here the piece has not been touched by decay, a
     * player, or anything else, and the block list <em>is</em> the room.</p>
     *
     * <p>A piece is a boss room when it holds a marker that declared itself the boss spawner. The
     * flag is read from the ORIGINAL block list, because by the time processing is finished the
     * marker has become a spawner and the flag has been consumed; the box is measured from the
     * PROCESSED list, whose positions are the world positions actually being written.</p>
     *
     * <p><strong>Hops to the server thread to save.</strong> This runs on a chunk-generation worker,
     * and {@code DimensionDataStorage} is not safe to touch from one. The task only writes data, so
     * it does not care when it lands.</p>
     */
    private void recordBossRoom(ServerLevelAccessor level,
                                List<StructureTemplate.StructureBlockInfo> blocks,
                                List<StructureTemplate.StructureBlockInfo> processedBlocks) {
        if (bossMobSet.isEmpty() || processedBlocks.isEmpty()) {
            return;
        }
        boolean bossRoom = blocks.stream().anyMatch(info -> isSpawnerMarker(info) && isBossMarker(info));
        if (!bossRoom) {
            return;
        }
        BlockPos first = processedBlocks.get(0).pos();
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            BlockPos pos = info.pos();
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        BoundingBox room = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        ServerLevel serverLevel = level.getLevel();
        serverLevel.getServer().execute(() -> BossRoomRegistry.get(serverLevel).add(room));
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level, BlockPos piecePos, BlockPos originalPos,
            List<StructureTemplate.StructureBlockInfo> blocks,
            List<StructureTemplate.StructureBlockInfo> processedBlocks,
            StructurePlaceSettings settings) {
        recordBossRoom(level, blocks, processedBlocks);
        return super.finalizeProcessing(level, piecePos, originalPos, blocks, processedBlocks, settings);
    }

    boolean isSpawnerMarker(StructureTemplate.StructureBlockInfo info) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(info.state().getBlock());
        return markerBlock.equals(id);
    }

    /** The block-entity tag a marker becomes, taking the pool's values. Pure: no registry, no level. */
    CompoundTag spawnerTag() {
        return spawnerTag(Overrides.NONE);
    }

    /** The block-entity tag a marker becomes, with that marker's own overrides applied. */
    CompoundTag spawnerTag(Overrides overrides) {
        return spawnerTag(overrides, overrides.mobSet(mobSet));
    }

    /**
     * The form used in {@code processBlock}, taking the already-resolved set.
     *
     * <p>{@code resolvedSet} is used VERBATIM -- {@link Overrides#mobSet} has already been consulted
     * (or deliberately bypassed, for a boss marker) by {@link #resolveMobSet}. Applying the override
     * again here is the bug that shipped in the first draft of the tier split: a boss marker with a
     * leftover {@code mobSetName} took the template's set back off the tier.</p>
     */
    CompoundTag spawnerTag(Overrides overrides, ResourceLocation resolvedSet) {
        CompoundTag tag = new CompoundTag();
        // The block-entity type's registry id, which is what vanilla's placeInWorld loads against.
        tag.putString("id", new ResourceLocation(Dungeons.MOD_ID, "mob_set_spawner").toString());
        tag.putString(MOB_SET_NAME, resolvedSet.toString());
        tag.putInt(MIN_MOBS, overrides.minMobs(minMobs));
        tag.putInt(MAX_MOBS, overrides.maxMobs(maxMobs));
        // putDouble, matching what the block entity reads. The marker accepts any numeric tag on
        // the way in (see SpawnerMarkerBlockEntity) precisely so this stays the only encoding that
        // ever reaches GottschCore.
        tag.putDouble(PROXIMITY, overrides.proximity(proximity));
        return tag;
    }

    /**
     * One marker's per-cell overrides, read off its block-entity NBT.
     *
     * <h2>Why a value type rather than five lookups at the point of use</h2>
     * <p>Every field has the same rule &mdash; stated wins, absent falls through to the pool &mdash;
     * and it has to be applied identically on the proximity route, the vanilla route and the log
     * line. Spelling it out five times in three places is how the two spawner encodings drifted the
     * first time, which is why {@code SpawnerTagParityTest} exists. Here the rule is written once.</p>
     *
     * <p>Null-not-empty, because {@link StructureTemplate.StructureBlockInfo#nbt()} is itself null
     * for the overwhelming majority of cells and a marker with no NBT is the normal case, not an
     * error. {@link #NONE} is the "this marker said nothing" instance, so callers with no template
     * in hand (tests, and {@link #spawnerTag()}) go through exactly the same code path.</p>
     */
    record Overrides(CompoundTag nbt) {

        /** A marker that states nothing: every accessor returns the pool's value. */
        static final Overrides NONE = new Overrides(null);

        static Overrides of(StructureTemplate.StructureBlockInfo current) {
            return new Overrides(current.nbt());
        }

        ResourceLocation mobSet(ResourceLocation pooled) {
            String stated = string(SpawnerMarkerBlockEntity.MOB_SET_NAME);
            if (stated == null) {
                return pooled;
            }
            // tryParse, not the constructor: a typo in a hand-authored /data merge would otherwise
            // throw out of a worldgen thread, and an unparseable id is exactly the case where
            // falling back to the pool's set leaves a working dungeon and a WARN to read.
            ResourceLocation parsed = ResourceLocation.tryParse(stated);
            if (parsed == null) {
                Dungeons.LOGGER.warn("[D2-SPAWNER] marker names an unparseable mob set '{}';"
                        + " using the pool's {} instead", stated, pooled);
                return pooled;
            }
            return parsed;
        }

        double proximity(double pooled) {
            return nbt != null && nbt.contains(SpawnerMarkerBlockEntity.PROXIMITY, Tag.TAG_ANY_NUMERIC)
                    ? nbt.getDouble(SpawnerMarkerBlockEntity.PROXIMITY) : pooled;
        }

        int minMobs(int pooled) {
            return integer(SpawnerMarkerBlockEntity.MIN_MOBS, pooled);
        }

        int maxMobs(int pooled) {
            return integer(SpawnerMarkerBlockEntity.MAX_MOBS, pooled);
        }

        /**
         * Clamped here as well as in {@link SpawnerMarkerBlockEntity}: this reads {@code current
         * .nbt()} straight off the template, so the block entity's own clamp never runs on this
         * path. An out-of-range authored value has to mean something rather than throw.
         */
        float probability(float pooled) {
            if (nbt == null
                    || !nbt.contains(SpawnerMarkerBlockEntity.PROBABILITY, Tag.TAG_ANY_NUMERIC)) {
                return pooled;
            }
            float stated = nbt.getFloat(SpawnerMarkerBlockEntity.PROBABILITY);
            return Math.max(0.0F, Math.min(1.0F, stated));
        }

        SpawnerConfig.Kind kind(SpawnerConfig.Kind pooled) {
            String stated = string(SpawnerMarkerBlockEntity.TYPE);
            if (stated == null) {
                return pooled;
            }
            for (SpawnerConfig.Kind candidate : SpawnerConfig.Kind.values()) {
                if (candidate.getSerializedName().equals(stated)) {
                    return candidate;
                }
            }
            // Same call as an unparseable set: a misspelled type must not silently become the other
            // kind, and it must not stop the dungeon generating either.
            Dungeons.LOGGER.warn("[D2-SPAWNER] marker names an unknown spawner type '{}';"
                    + " using the pool's {} instead", stated, pooled.getSerializedName());
            return pooled;
        }

        private String string(String key) {
            if (nbt == null || !nbt.contains(key, Tag.TAG_STRING)) {
                return null;
            }
            String value = nbt.getString(key);
            return value.isEmpty() ? null : value;
        }

        private int integer(String key, int pooled) {
            return nbt != null && nbt.contains(key, Tag.TAG_ANY_NUMERIC)
                    ? nbt.getInt(key) : pooled;
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return mod.gottsch.forge.dungeons2.core.setup.Registration.SPAWNER_PROCESSOR.get();
    }
}
