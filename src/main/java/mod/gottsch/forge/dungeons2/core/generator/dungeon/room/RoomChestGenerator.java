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

import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places a room scheme's chests. Backlog #48, the procedural route.
 *
 * <p>Structurally this is {@link RoomSpawnerGenerator}: roll a count, draw that many distinct
 * wall-adjacent cells without replacement, emit a block carrying block-entity data, and hand the
 * claimed cells back so the props placed afterwards keep out of them. The differences are the two
 * things a chest has and a spawner has not &mdash; a facing, and loot.</p>
 *
 * <h2>Facing is derived, not rolled</h2>
 * <p>A chest is drawn from the same wall-adjacent cells the pots and spawners use, so every chest
 * has a wall behind it. It faces <em>away</em> from that wall, into the room, which is the only
 * orientation that reads as furniture rather than as something dropped by the generator. A cell in a
 * corner touches two walls and takes the first in {@code north, south, west, east} order &mdash; an
 * arbitrary tie-break, but a fixed one, which is what keeps a seed reproducible.</p>
 *
 * <h2>The chest claims its cell against the props, and for a different reason than the spawner does</h2>
 * <p>A spawner claims its cell because the mobs would smash a pot on their way out. A chest claims
 * its cell because it is a <strong>solid block</strong>: a pot entity spawned in the same cell would
 * be standing inside it, and pots have gravity, so it would fall and shatter the moment the chunk
 * ticked. This one would be a visible bug rather than a subtle one.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public final class RoomChestGenerator {

    /** Vanilla's key for the table a container fills from when first opened. */
    static final String LOOT_TABLE = "LootTable";
    /** Vanilla's key for the seed that fixes those contents at generation time. */
    static final String LOOT_TABLE_SEED = "LootTableSeed";
    /** The block-entity type {@code DungeonPiece.applyBlockEntity} routes on. */
    static final String CHEST_ENTITY = "minecraft:chest";
    /** Vanilla's chest facing property. */
    static final String FACING = "facing";

    private RoomChestGenerator() {}

    /**
     * Emits this room's chests, returning the cells they took.
     *
     * @param occupied cells already claimed by architecture or spawners; chests avoid them and add
     *                 their own to what they return
     */
    public static Set<Coords2D> placeChests(RoomData room, int floorY, ChestConfig config,
                                            Set<Coords2D> occupied, RandomSource random,
                                            List<BlockPlacement> out) {
        List<ChestConfig.ChestVariant> variants = config.variants();
        int totalWeight = variants.stream().mapToInt(ChestConfig.ChestVariant::weight).sum();
        if (variants.isEmpty() || totalWeight <= 0) {
            return Set.of();
        }

        // declaredLootTables, not the raw Optional: by this point the caller has resolved the slot
        // against the motif's depth table (ChestConfig#resolvedAgainst), so an empty list here means
        // neither the scheme nor the floor had anything to offer. Place NOTHING rather than a chest
        // with no table -- an empty chest costs the player a walk to find out it was empty, which is
        // worse than no chest at all. Same call the spawner slot makes for an unresolvable mob set.
        List<ChestConfig.LootTableEntry> tables = config.declaredLootTables();
        int totalTableWeight = tables.stream().mapToInt(ChestConfig.LootTableEntry::weight).sum();
        if (tables.isEmpty() || totalTableWeight <= 0) {
            return Set.of();
        }

        List<Coords2D> candidates = RoomPropGenerator.eligibleCells(room, occupied);
        if (candidates.isEmpty()) {
            return Set.of();
        }

        int min = config.minCount();
        int max = config.clampedMaxCount();
        int count = min + (max > min ? random.nextInt(max - min + 1) : 0);
        count = Math.min(count, candidates.size());

        Set<Coords2D> used = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            // Draw without replacement -- the same swap-to-the-end trick RoomPropGenerator and
            // RoomSpawnerGenerator use, so two chests never land in one cell.
            int pick = random.nextInt(candidates.size() - i);
            Coords2D cell = candidates.get(pick);
            candidates.set(pick, candidates.get(candidates.size() - 1 - i));
            candidates.set(candidates.size() - 1 - i, cell);

            Map<String, String> properties = new LinkedHashMap<>();
            properties.put(FACING, facingAwayFromWall(room, cell));

            // floorY + 1: resting on the floor surface, the same row the pots and spawners use.
            BlockPlacement placement = new BlockPlacement(cell.getX(), floorY + 1, cell.getY(),
                    pickVariant(variants, totalWeight, random), properties);
            String table = pickTable(tables, totalTableWeight, random);
            placement.setBlockEntityNbt(chestData(table, random));
            // The procedural route's probe, and it is INFO for the same reason the marker route's
            // is: at the shipped "info" level a debug line is invisible to the person verifying the
            // feature. Tagged PROC so the two routes can be told apart in one grep -- without it a
            // chest in a finished dungeon says nothing about which half of #48 produced it.
            mod.gottsch.forge.dungeons2.Dungeons.LOGGER.info(
                    "[D2-CHEST] PROC {} at {},{},{} (table {})",
                    placement.getBlockId(), placement.getX(), placement.getY(), placement.getZ(), table);
            out.add(placement);
            used.add(cell);
        }
        return used;
    }

    /**
     * The block-entity data a chest cell carries: the table, and the seed that fixes its contents.
     *
     * <p>The seed is forced non-zero. Vanilla treats {@code LootTableSeed} 0 as "roll fresh on
     * open", so a zero here would quietly turn a fixed structure chest into one a player can
     * re-roll by reloading the save &mdash; the same trap {@code RoomPropGenerator} avoids for
     * pots, and it fires roughly once in four billion, which is to say never in testing and
     * eventually in the wild.</p>
     */
    static BlockEntityData chestData(String lootTable, RandomSource random) {
        return new BlockEntityData(CHEST_ENTITY)
                .with(LOOT_TABLE, lootTable)
                .with(LOOT_TABLE_SEED, Long.toString(lootSeed(random)));
    }

    /**
     * Weighted draw over the resolved tables, <strong>per chest</strong> rather than once per room.
     *
     * <p>Two chests in one room can therefore differ, which is the point of a weighted list: a floor
     * whose band is "mostly common, occasionally rare" should not turn a two-chest room into two
     * rare chests on one roll.</p>
     */
    static String pickTable(List<ChestConfig.LootTableEntry> tables, int totalWeight,
                            RandomSource random) {
        int roll = random.nextInt(totalWeight);
        for (ChestConfig.LootTableEntry entry : tables) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry.lootTable();
            }
        }
        return tables.get(tables.size() - 1).lootTable();
    }

    /** A non-zero seed; see {@link #chestData}. */
    static long lootSeed(RandomSource random) {
        long seed = random.nextLong();
        return seed == 0L ? 1L : seed;
    }

    /**
     * The direction a chest in this cell should face: away from the wall it backs onto.
     *
     * <p>Cells here are floor-local and the room box includes its wall ring, so the interior runs
     * from {@code origin + 1} to {@code origin + size - 2}. A cell on the first interior row backs
     * onto the north wall and therefore faces south, and so on round. A cell touching no wall cannot
     * arise from {@code eligibleCells}, which returns wall-adjacent cells only; if one ever does,
     * north is as good an answer as any and is at least deterministic.</p>
     */
    static String facingAwayFromWall(RoomData room, Coords2D cell) {
        if (cell.getY() == room.getOriginZ() + 1) {
            return "south";
        }
        if (cell.getY() == room.getOriginZ() + room.getDepth() - 2) {
            return "north";
        }
        if (cell.getX() == room.getOriginX() + 1) {
            return "east";
        }
        if (cell.getX() == room.getOriginX() + room.getWidth() - 2) {
            return "west";
        }
        return "north";
    }

    /** Weighted draw over the declared variants. Mirrors {@code RoomPropGenerator#pickVariant}. */
    static String pickVariant(List<ChestConfig.ChestVariant> variants, int totalWeight,
                              RandomSource random) {
        int roll = random.nextInt(totalWeight);
        for (ChestConfig.ChestVariant variant : variants) {
            roll -= variant.weight();
            if (roll < 0) {
                return variant.block();
            }
        }
        return variants.get(variants.size() - 1).block();
    }
}
