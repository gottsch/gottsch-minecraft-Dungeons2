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
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
            // would be. The block is invisible and passes the player through, so nothing about the
            // room reads differently for it being there.
            BlockPlacement placement = new BlockPlacement(cell.getX(), floorY + 1, cell.getY(),
                    SPAWNER_BLOCK);
            placement.setBlockEntityNbt(spawnerData(config, mobSet, floorIndex));
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
