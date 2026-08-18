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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.util.VanillaSpawnerNbt;
import mod.gottsch.forge.gottschcore.mobset.MobSetDataRegistry;
import mod.gottsch.forge.gottschcore.mobset.WeightedMob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Places a room scheme's invisible mob-set spawners: picks cells, rolls a mob set per spawner, and
 * emits {@link BlockPlacement}s carrying {@link BlockEntityData}.
 *
 * <p>This is the procedural half of backlog #10. The authored half is
 * {@code SpawnerMarkerProcessor}, which converts a marker block a template author placed; only a
 * template can carry that marker, so before this a procedurally-built room &mdash; most of a dungeon
 * &mdash; had no monsters of its own. Both halves produce the same block and the same block-entity
 * tag; see {@link #spawnerData} for why the tag is spelled out here rather than shared with the
 * processor.</p>
 *
 * <h2>Where a spawner may stand</h2>
 * <p>The whole interior, not the inner ring the pots are confined to. A pot is furniture and belongs
 * against a wall; a spawner is <em>invisible</em> and non-colliding, so the only thing its cell
 * changes is where the mobs appear, and the middle of a room is the better answer for that. Two
 * exclusions:</p>
 * <ol>
 *   <li><strong>The cell just inside a doorway.</strong> Same rule the pots and columns follow, for
 *       a different reason: a spawn triggered from a threshold cell drops mobs into the corridor a
 *       player is still in, rather than into the room they are entering.</li>
 *   <li><strong>Cells another generator already took.</strong> A column or a dais occupies its cell
 *       with a solid block, and the block placed last wins &mdash; so a spawner emitted into one
 *       would be a coin toss between vanishing and punching a hole in the architecture.</li>
 * </ol>
 *
 * <h2>Determinism</h2>
 * <p>Everything here is a pure function of the room and the {@link RandomSource} it is handed, which
 * the caller seeds from chunk-independent piece state. That is load-bearing rather than tidy: a
 * piece's {@code postProcess} runs once per chunk it overlaps, and the consumer clips each placement
 * to the chunk that owns it. A plan that varied between those runs would drop a spawner that
 * straddles a seam, or place two.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public final class RoomSpawnerGenerator {

    /**
     * The block, and the block entity type, are registered under this one id -- so it is both the
     * placement's block id and the {@code "id"} the block-entity tag is loaded against.
     */
    public static final String SPAWNER_BLOCK = "dungeons2:mob_set_spawner";

    /** Vanilla's cage, for {@code SpawnerConfig.Kind#VANILLA}. */
    public static final String VANILLA_SPAWNER_BLOCK = "minecraft:spawner";
    public static final String VANILLA_SPAWNER_ENTITY = "minecraft:mob_spawner";

    // The block entity's own NBT field names (GottschCore's ProximityMobSetSpawnerBlockEntity /
    // AbstractProximityBlockEntity). Spelled out rather than imported, for the reason every Phase 2
    // builder names its blocks by string: this side of the pipeline is pure data and resolves
    // nothing through a registry.
    static final String MOB_SET_NAME = "mobSetName";
    /** Dungeons2's own field, persisted by {@code DungeonSpawnerBlockEntity}. */
    static final String FLOOR_INDEX = "floorIndex";
    static final String MIN_MOBS = "minMobs";
    static final String MAX_MOBS = "maxMobs";
    static final String PROXIMITY = "proximity";

    private RoomSpawnerGenerator() {}

    /**
     * Emits this room's spawners and returns the floor cells they took.
     *
     * <p>A count is rolled from the config's inclusive range, then that many distinct cells are
     * drawn; a room with fewer eligible cells than the rolled count gets fewer spawners rather than
     * two in one cell. The returned cells are what lets the caller keep the pots out of them &mdash;
     * a spawner is invisible and its block does not collide, so a pot could stand in one quite
     * happily, but the mobs would then materialise inside the pot and smash it on the way out.</p>
     */
    public static Set<Coords2D> placeSpawners(RoomData room, int floorY, int floorIndex,
                                              SpawnerConfig config, Set<Coords2D> occupied,
                                              RandomSource random, List<BlockPlacement> out) {
        // declaredMobSets, not the raw Optional: by this point the caller has resolved the slot
        // against the motif's depth table (SpawnerConfig#resolvedAgainst), so an empty list here
        // means neither the scheme nor the floor had anything to offer -- place nothing rather
        // than an invisible block that spawns nothing.
        List<SpawnerConfig.MobSetEntry> sets = config.declaredMobSets();
        int totalWeight = sets.stream().mapToInt(SpawnerConfig.MobSetEntry::weight).sum();
        if (sets.isEmpty() || totalWeight <= 0) {
            return Set.of();
        }

        List<Coords2D> candidates = eligibleCells(room, occupied);
        if (candidates.isEmpty()) {
            return Set.of();
        }

        int min = config.minCount();
        int max = config.clampedMaxCount();
        int count = min + (max > min ? random.nextInt(max - min + 1) : 0);
        count = Math.min(count, candidates.size());

        Set<Coords2D> used = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            // Draw without replacement -- the same swap-to-the-end trick RoomPropGenerator uses, so
            // two spawners never land in one cell.
            int pick = random.nextInt(candidates.size() - i);
            Coords2D cell = candidates.get(pick);
            candidates.set(pick, candidates.get(candidates.size() - 1 - i));
            candidates.set(candidates.size() - 1 - i, cell);

            String mobSet = pickMobSet(sets, totalWeight, random);
            // floorY + 1: the cell resting on the floor, which is where a mob standing in the room
            // would be. A proximity block is invisible and passes the player through, so nothing
            // about the room reads differently for it being there; a vanilla cage is a real solid
            // block and deliberately does read as one.
            BlockPlacement placement;
            if (config.kind() == SpawnerConfig.Kind.VANILLA) {
                BlockEntityData vanilla = vanillaSpawnerData(config, mobSet, random);
                if (vanilla == null) {
                    // The set could not be resolved to real mobs, so there is nothing to put in the
                    // cage. Skip the cell rather than place an empty spawner: vanilla's own default
                    // is a pig, and a pig cage in a dungeon is worse than no spawner at all. The
                    // cell is NOT claimed, so a pot may still use it.
                    continue;
                }
                placement = new BlockPlacement(cell.getX(), floorY + 1, cell.getY(),
                        VANILLA_SPAWNER_BLOCK);
                placement.setBlockEntityNbt(vanilla);
            } else {
                placement = new BlockPlacement(cell.getX(), floorY + 1, cell.getY(), SPAWNER_BLOCK);
                placement.setBlockEntityNbt(spawnerData(config, mobSet, floorIndex));
            }
            out.add(placement);
            used.add(cell);
        }
        return used;
    }

    /**
     * The block-entity data a spawner cell carries: which set, how many, and how close a player has
     * to come. Mirrors {@code SpawnerMarkerProcessor#spawnerTag} field for field.
     *
     * <p>They are not shared code, and that is deliberate. The processor builds a real
     * {@code CompoundTag} with typed puts, because a jigsaw pool element hands vanilla a tag; this
     * side builds stringified key/values, because that is {@code BlockEntityData}'s loader-portable
     * contract and {@code DungeonPiece.applyBlockEntity} parses them back. Two encodings of one
     * shape &mdash; {@code SpawnerTagParityTest} is what keeps them from drifting.</p>
     *
     * <p>{@code proximity} is written as a decimal on purpose: the parser reaches for
     * {@code Integer} first, and an {@code IntTag} where the block entity calls {@code getDouble}
     * happens to work today only because a numeric tag converts. Writing {@code "8.0"} means the tag
     * has the type the reader names.</p>
     */
    public static BlockEntityData spawnerData(SpawnerConfig config, String mobSet, int floorIndex) {
        return new BlockEntityData(SPAWNER_BLOCK)
                .with(MOB_SET_NAME, mobSet)
                .with(MIN_MOBS, String.valueOf(config.effectiveMinMobs()))
                .with(MAX_MOBS, String.valueOf(config.clampedMaxMobs()))
                .with(PROXIMITY, String.valueOf(config.proximity()))
                // Stamped at generation and persisted, though nothing reads it yet -- see
                // DungeonSpawnerBlockEntity for what it is for and why it needed a field rather
                // than just a tag key.
                .with(FLOOR_INDEX, String.valueOf(floorIndex));
    }

    /**
     * The block-entity data for a vanilla cage drawing from {@code mobSetName}, or {@code null} when
     * the set cannot be resolved to any mob vanilla could draw.
     *
     * <h2>The set is consulted HERE, unlike the proximity path</h2>
     * <p>A proximity spawner stores the set's <em>name</em> and rolls at trigger time. Vanilla's
     * {@code BaseSpawner} has never heard of a mob set, so the ids have to be handed over at
     * generation &mdash; which means this path needs {@code MobSetDataRegistry} during worldgen
     * while the proximity path does not. The registry is filled at datapack reload, well before any
     * chunk generates, so it is populated in a real game; it is empty under a bare test harness,
     * and returning {@code null} is what keeps that case from placing a pig cage.</p>
     *
     * <p>The consequence worth stating: a mob set edited in a datapack later changes every
     * proximity spawner in an existing world and <strong>no</strong> vanilla one, because the
     * vanilla tags were baked when the chunk generated.</p>
     *
     * <p>{@code SpawnCount} is drawn from the same {@code minMobs}..{@code maxMobs} range the
     * proximity spawner uses, so the depth bands reach this kind too &mdash; a floor-2 cage
     * releases 2-4 exactly as a floor-2 ambush does.</p>
     */
    static BlockEntityData vanillaSpawnerData(SpawnerConfig config, String mobSetName,
                                              RandomSource random) {
        Optional<ResourceLocation> id = Optional.ofNullable(ResourceLocation.tryParse(mobSetName));
        if (id.isEmpty()) {
            return null;
        }
        List<WeightedMob> mobs = MobSetDataRegistry.get(id.get())
                .map(VanillaSpawnerNbt::usableMobs)
                .orElseGet(List::of);
        if (mobs.isEmpty()) {
            return null;
        }

        // Drawn with the piece's own seeded random, like every other procedural decision here, so
        // the cage shows the same mob on every regeneration of the same seed.
        String shown = drawMob(mobs, random);
        int min = config.effectiveMinMobs();
        int max = config.clampedMaxMobs();
        int spawnCount = min + (max > min ? random.nextInt(max - min + 1) : 0);

        BlockEntityData data = new BlockEntityData(VANILLA_SPAWNER_ENTITY)
                .withNbt(VanillaSpawnerNbt.SPAWN_DATA, VanillaSpawnerNbt.spawnData(shown))
                .withNbt(VanillaSpawnerNbt.SPAWN_POTENTIALS, VanillaSpawnerNbt.spawnPotentials(mobs));
        VanillaSpawnerNbt.tuning(spawnCount).forEach(data::with);
        return data;
    }

    /** Weighted draw over the set's mobs, matching {@link #pickMobSet}'s shape. */
    private static String drawMob(List<WeightedMob> mobs, RandomSource random) {
        int total = mobs.stream().mapToInt(WeightedMob::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        for (WeightedMob mob : mobs) {
            roll -= mob.weight();
            if (roll < 0) {
                return mob.id().toString();
            }
        }
        return mobs.get(mobs.size() - 1).id().toString();
    }

    /**
     * The room's interior cells, minus the ones just inside a doorway and minus {@code occupied}.
     *
     * <p>Returned in floor-local coords, the same space as {@code RoomData#getOriginX} and
     * {@code getDoorways} &mdash; and the same space {@code RoomPropGenerator.eligibleCells} works
     * in, which is the set this one deliberately differs from: that one keeps to the inner ring
     * because a pot has to look placed, this one uses the whole interior because a spawner is not
     * looked at.</p>
     */
    static List<Coords2D> eligibleCells(RoomData room, Set<Coords2D> occupied) {
        int width = room.getWidth();
        int depth = room.getDepth();
        int originX = room.getOriginX();
        int originZ = room.getOriginZ();

        Set<Coords2D> blocked = RoomInterior.cellsInsideDoorways(room);
        List<Coords2D> cells = new ArrayList<>();
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                Coords2D cell = new Coords2D(originX + x, originZ + z);
                if (!blocked.contains(cell) && !occupied.contains(cell)) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }

    private static String pickMobSet(List<SpawnerConfig.MobSetEntry> sets, int totalWeight,
                                     RandomSource random) {
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (SpawnerConfig.MobSetEntry entry : sets) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry.mobSet();
            }
        }
        return sets.get(sets.size() - 1).mobSet(); // unreachable
    }
}
