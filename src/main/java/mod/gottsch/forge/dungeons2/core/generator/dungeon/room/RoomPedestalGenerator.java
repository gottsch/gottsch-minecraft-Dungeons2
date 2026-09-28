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

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.config.SecretConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.HiddenDoors;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The secret room's prize: one DungeonBlocks pedestal at the centre of the floor, showing one stack.
 *
 * <p>Two ways to fill it, and the block entity reads either:</p>
 * <ul>
 *   <li><strong>{@code Item}</strong>, set outright, when this room holds the dungeon's counter-item
 *       (#97). The counter tables are CHEST tables &mdash; the item plus a filler roll &mdash; and a
 *       pedestal keeps only the first stack a table rolls, which for a chest table is whichever the
 *       shuffle put first. So the item goes on by id and never through a table.</li>
 *   <li><strong>{@code LootTable}</strong> + a non-zero {@code LootTableSeed}, from the scheme's
 *       {@code pedestal_loot_tables}, rolled as chest loot the first time the chunk loads. Tables
 *       written for a pedestal should roll ONE stack.</li>
 * </ul>
 *
 * <p>The cell is the interior cell nearest the centre that nothing has taken: a pit, a column, a
 * dais or a partition all get there first. Draws from the room random only when it places, so
 * every room that is not a secret room is stream-neutral.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class RoomPedestalGenerator {

    static final String PEDESTAL_ENTITY = HiddenDoors.PEDESTAL;

    private RoomPedestalGenerator() {}

    /**
     * Places the pedestal, returning the cell it took &mdash; empty when there is nothing to put on
     * it or nowhere free to stand it.
     *
     * @param counterItem the dungeon's counter-item id when this room carries it, else null
     */
    public static Set<Coords2D> place(RoomData room, int floorY, SecretConfig secret,
                                      String counterItem, Set<Coords2D> taken, RandomSource random,
                                      List<BlockPlacement> out) {
        List<ChestConfig.LootTableEntry> tables = secret == null ? List.of() : secret.pedestalTables();
        if (counterItem == null && tables.isEmpty()) {
            return Set.of();
        }
        Coords2D cell = centremostFree(room, taken);
        if (cell == null) {
            Dungeons.LOGGER.warn("[D2-SECRET] room {} had no free cell for its pedestal", room.getId());
            return Set.of();
        }
        BlockEntityData data = new BlockEntityData(PEDESTAL_ENTITY);
        if (counterItem != null) {
            data.withNbt("Item", "{id:\"" + counterItem + "\",Count:1b}");
        } else {
            data.with(RoomChestGenerator.LOOT_TABLE, ChestConfig.LootTableEntry.pick(tables, random))
                    .with(RoomChestGenerator.LOOT_TABLE_SEED,
                            Long.toString(RoomChestGenerator.lootSeed(random)));
        }
        BlockPlacement placement = new BlockPlacement(cell.getX(), floorY + 1, cell.getY(),
                PEDESTAL_ENTITY);
        placement.setBlockEntityNbt(data);
        out.add(placement);
        return Set.of(cell);
    }

    /**
     * The free interior cell nearest the room's centre. Ties are broken by position so the choice
     * never depends on set iteration order &mdash; this runs once per chunk the room spans, and every
     * run must agree.
     */
    static Coords2D centremostFree(RoomData room, Set<Coords2D> taken) {
        // Doubled coordinates, so an even-sized interior's centre (between cells) is exact.
        int cx2 = 2 * room.getOriginX() + room.getWidth() - 1;
        int cz2 = 2 * room.getOriginZ() + room.getDepth() - 1;
        List<Coords2D> interior = new ArrayList<>();
        for (int x = room.getOriginX() + 1; x <= room.getOriginX() + room.getWidth() - 2; x++) {
            for (int z = room.getOriginZ() + 1; z <= room.getOriginZ() + room.getDepth() - 2; z++) {
                Coords2D cell = new Coords2D(x, z);
                if (!taken.contains(cell)) {
                    interior.add(cell);
                }
            }
        }
        return interior.stream()
                .min(Comparator.<Coords2D>comparingInt(c ->
                                Math.abs(2 * c.getX() - cx2) + Math.abs(2 * c.getY() - cz2))
                        .thenComparingInt(Coords2D::getX)
                        .thenComparingInt(Coords2D::getY))
                .orElse(null);
    }
}
