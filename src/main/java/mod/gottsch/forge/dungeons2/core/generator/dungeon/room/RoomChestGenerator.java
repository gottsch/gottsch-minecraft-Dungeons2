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
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
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
 * wall-adjacent cells without replacement (both via {@link CellDraw}), emit a block carrying block-entity data, and hand the
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
    /** What a chest becomes when it bites (#99). */
    static final String CHEST_MIMIC_ENTITY = "dungeons2:vanilla_chest_mimic";
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
                                            List<BlockPlacement> out,
                                            List<EntityPlacement> entities) {
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

        // floorY + 1: resting on the floor surface, the same row the pots and spawners use.
        return placeChestsOn(RoomPropGenerator.eligibleCells(room, occupied), floorY + 1, config,
                cell -> facingAwayFromWall(room, cell), random, out, entities);
    }

    /**
     * Places chests on exactly {@code candidates}, at exactly {@code y}, each facing whatever
     * {@code facing} says &mdash; the one place a chest BLOCK is built, whoever chose the cells.
     *
     * <p>Extracted when a dais and a sunken court grew centrepiece chests (backlog #86). Neither is
     * a floor cell and neither has a wall to back onto, so the two halves of {@link #placeChests}'s
     * own rule (wall-adjacent cells, face away from the wall) do not apply &mdash; but everything
     * about the chest itself does, and those are the parts worth having one copy of: the weighted
     * variant, the weighted table, and above all the non-zero loot seed, whose failure is invisible
     * and roughly one draw in four billion (see {@link #chestData}).</p>
     *
     * <p>The count still means what it always meant. A centrepiece hands in a single cell, so
     * {@code min_count: 0, max_count: 1} is "a chest here sometimes" without any new vocabulary.</p>
     */
    public static Set<Coords2D> placeChestsOn(List<Coords2D> candidates, int y, ChestConfig config,
                                              java.util.function.Function<Coords2D, String> facing,
                                              RandomSource random, List<BlockPlacement> out,
                                              List<EntityPlacement> entities) {
        List<ChestConfig.ChestVariant> variants = config.variants();
        int totalWeight = variants.stream().mapToInt(ChestConfig.ChestVariant::weight).sum();
        if (variants.isEmpty() || totalWeight <= 0 || candidates.isEmpty()) {
            return Set.of();
        }
        List<ChestConfig.LootTableEntry> tables = config.declaredLootTables();
        int totalTableWeight = tables.stream().mapToInt(ChestConfig.LootTableEntry::weight).sum();
        if (tables.isEmpty() || totalTableWeight <= 0) {
            return Set.of();
        }

        CellDraw draw = CellDraw.of(candidates, config.minCount(), config.clampedMaxCount(), random);
        Set<Coords2D> used = new LinkedHashSet<>();
        while (draw.hasNext()) {
            Coords2D cell = draw.next();

            Map<String, String> properties = new LinkedHashMap<>();
            properties.put(FACING, facing.apply(cell));

            String variant = pickVariant(variants, totalWeight, random);
            String table = pickTable(tables, totalTableWeight, random);

            // #99. The roll happens AFTER the table is drawn, deliberately: the mimic carries the
            // very table the chest would have had, so killing it pays exactly what opening the
            // chest would have. That is why this is a swap and not a deletion -- the player loses
            // the free reward, not the reward.
            //
            // Rolled per chest. A two-chest room can have one of each, which is the worse thing to
            // walk into and the better thing to have built.
            if (config.mimicChance() > 0.0D && random.nextDouble() < config.mimicChance()) {
                entities.add(mimicFor(cell, y, table));
                mod.gottsch.forge.dungeons2.Dungeons.LOGGER.info(
                        "[D2-CHEST] PROC MIMIC at {} (table {})",
                        new net.minecraft.core.BlockPos(cell.getX(), y, cell.getY()).toShortString(),
                        table);
                used.add(cell);
                continue;
            }

            BlockPlacement placement = new BlockPlacement(cell.getX(), y, cell.getY(),
                    variant, properties);
            placement.setBlockEntityNbt(chestData(table, random));
            // The procedural route's probe, and it is INFO for the same reason the marker route's
            // is: at the shipped "info" level a debug line is invisible to the person verifying the
            // feature. Tagged PROC so the two routes can be told apart in one grep -- without it a
            // chest in a finished dungeon says nothing about which half of #48 produced it.
            // toShortString, matching ChestMarkerProcessor: a position in a log is read by someone
            // about to go and look at it, so it has to survive a copy and paste into a command.
            mod.gottsch.forge.dungeons2.Dungeons.LOGGER.info(
                    "[D2-CHEST] PROC {} at {} (table {})",
                    placement.getBlockId(),
                    new net.minecraft.core.BlockPos(placement.getX(), placement.getY(),
                            placement.getZ()).toShortString(),
                    table);
            out.add(placement);
            used.add(cell);
        }
        return used;
    }

    /**
     * One chest's block id and its block-entity data, for a caller that places the block itself
     * rather than handing over cells &mdash; today the sunken court, whose centrepiece is written by
     * the pit generator as part of the plan and not by this class at all.
     *
     * <p>Empty when the config names no variant or no table. An unresolvable table means NO chest,
     * never an empty one: an empty chest costs the player the walk to find out it was empty, which
     * is worse than no chest at all. That rule is the reason this returns an {@code Optional} rather
     * than a chest with a null table.</p>
     */
    public static java.util.Optional<ChestDraw> drawChest(ChestConfig config, RandomSource random) {
        List<ChestConfig.ChestVariant> variants = config.variants();
        int totalWeight = variants.stream().mapToInt(ChestConfig.ChestVariant::weight).sum();
        List<ChestConfig.LootTableEntry> tables = config.declaredLootTables();
        int totalTableWeight = tables.stream().mapToInt(ChestConfig.LootTableEntry::weight).sum();
        if (variants.isEmpty() || totalWeight <= 0 || tables.isEmpty() || totalTableWeight <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ChestDraw(pickVariant(variants, totalWeight, random),
                chestData(pickTable(tables, totalTableWeight, random), random)));
    }

    /** What {@link #drawChest} hands back: the block to place, and the data to hang on it. */
    public record ChestDraw(String block, BlockEntityData data) {}

    /**
     * A horizontal facing chosen at random &mdash; for a chest with no wall behind it.
     *
     * <p>A centrepiece stands in the middle of a dais or a court and is approached from every side,
     * so there is no direction {@link #facingAwayFromWall}'s argument could be made for. Rolled
     * rather than fixed because a fixed one would line every centrepiece in a dungeon up the same
     * way, which reads as generated.</p>
     */
    public static String randomHorizontalFacing(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0 -> "north";
            case 1 -> "south";
            case 2 -> "east";
            default -> "west";
        };
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
     * The chest that is not a chest (#99).
     *
     * <p>No {@code facing}: the mimic is an entity and turns to look at whoever wakes it, so a
     * baked-in rotation would only be wrong a moment later. No loot SEED either &mdash; a chest's
     * seed is fixed at generation so a player cannot re-roll it by reloading, but a mimic's table
     * is rolled when it dies, which is an event the player cannot replay. The seed's whole job is
     * already done.</p>
     *
     * <p>The table rides on {@link EntityPlacement#getLootTable()} and is transferred by
     * {@code EntitySpawner}, which hands it to {@code Mimic#setLootTable} &mdash; the vanilla
     * {@code LootTable} NBT key the field is normally written as means nothing to a Mob.</p>
     */
    static EntityPlacement mimicFor(Coords2D cell, int y, String lootTable) {
        EntityPlacement mimic = new EntityPlacement(cell.getX(), y, cell.getY(), CHEST_MIMIC_ENTITY);
        mimic.setLootTable(lootTable);
        return mimic;
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
        // Delegates since 2026-08-30 (#61): the authored route needs the same draw, so the
        // arithmetic moved to LootTableEntry where both can reach it. The totalWeight parameter is
        // kept because this call site already computed it to reject an all-zero list above.
        return ChestConfig.LootTableEntry.pick(tables, random);
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
