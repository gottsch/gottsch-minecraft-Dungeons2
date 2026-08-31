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
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.mining.MiningHaul;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places the one Mining Chest a dungeon gets, in the one room
 * {@code MiningChestPlanner} named &mdash; backlog #7.
 *
 * <p>Structurally {@link RoomChestGenerator} with two things removed and one added. Removed: the
 * count roll (there is exactly one) and the loot table (the contents are already decided). Added:
 * the {@code Items} list itself, written straight into the block entity.</p>
 *
 * <h2>A vanilla chest, for now</h2>
 * <p>{@code minecraft:chest}. Mark intends a dedicated model eventually; nothing here needs to
 * change when it arrives beyond the block id, because the contents travel as NBT on the placement
 * rather than as anything chest-specific.</p>
 *
 * <h2>It claims its cell, for the same blunt reason an ordinary chest does</h2>
 * <p>A chest is a solid block, so a pot entity rolled into the same cell would stand inside it and,
 * having gravity, fall and shatter as soon as the chunk ticked.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
public final class RoomMiningChestGenerator {

    /** Vanilla's key for a container's contents. */
    static final String ITEMS = "Items";

    private RoomMiningChestGenerator() {}

    /**
     * Emits the Mining Chest, returning the cell it took (empty when the room had nowhere to put
     * it).
     *
     * <p>An empty return is a real outcome: a small room whose floor is entirely spoken for by
     * pillars, a platform and a pit has no wall-adjacent cell left. The dungeon then has no Mining
     * Chest at all, which is unfortunate and rare and much better than the alternatives &mdash;
     * dropping the chest into a claimed cell, or hunting for another room after the plan has already
     * been serialized onto this piece.</p>
     *
     * @param occupied cells already claimed by architecture, spawners and chests
     */
    public static Set<Coords2D> placeChest(RoomData room, int floorY, MiningHaul haul,
                                           Set<Coords2D> occupied, RandomSource random,
                                           List<BlockPlacement> out) {
        if (haul == null || haul.isEmpty()) {
            return Set.of();
        }
        List<Coords2D> candidates = RoomPropGenerator.eligibleCells(room, occupied);
        if (candidates.isEmpty()) {
            Dungeons.LOGGER.warn("[D2-MINING] room {} had no free wall-adjacent cell for the Mining"
                    + " Chest, so this dungeon pays back nothing", room.getId());
            return Set.of();
        }

        Coords2D cell = candidates.get(random.nextInt(candidates.size()));

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(RoomChestGenerator.FACING, RoomChestGenerator.facingAwayFromWall(room, cell));

        // floorY + 1: resting on the floor surface, the same row the pots, spawners and ordinary
        // chests use.
        BlockPlacement placement = new BlockPlacement(cell.getX(), floorY + 1, cell.getY(),
                CHEST_BLOCK, properties);
        placement.setBlockEntityNbt(new BlockEntityData(RoomChestGenerator.CHEST_ENTITY)
                .withNbt(ITEMS, haul.itemsSnbt()));
        out.add(placement);

        if (haul.slotsNeeded() > MiningHaul.CHEST_SLOTS) {
            Dungeons.LOGGER.warn("[D2-MINING] haul needs {} slots and a chest holds {} -- the"
                    + " commonest items were dropped. Lower a `max` in mining_config, or the table"
                    + " has grown past what one chest can hold: {}",
                    haul.slotsNeeded(), MiningHaul.CHEST_SLOTS, haul);
        }
        // INFO, matching both chest routes: at the shipped "info" level a debug line is invisible to
        // whoever is verifying the feature, and this is the line that says the dungeon paid back at
        // all. toShortString so the position survives a copy-paste into a command.
        Dungeons.LOGGER.info("[D2-MINING] chest at {} holding {}",
                new BlockPos(placement.getX(), placement.getY(), placement.getZ()).toShortString(),
                haul);
        return Set.of(cell);
    }

    /**
     * The block the haul goes in. Vanilla for now &mdash; a dedicated Mining Chest model is
     * intended, and swapping this constant is the whole of that change on this side.
     */
    static final String CHEST_BLOCK = "minecraft:chest";
}
