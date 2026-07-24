/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.pattern.floor.FloorPattern;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns.FLOOR_PATTERN;

/**
 * Builds the floor surface of a {@link RoomData} as {@link BlockPlacement}s.
 *
 * <p>Floor sits at world Y={@code floorY}. The border cells (1 inset from the
 * room edge) use the primary FLOOR block; interior cells alternate between
 * FLOOR and ALTERNATE_FLOOR via a 45% probability roll for visual variety,
 * matching the original Forge 1.20.1 behavior.</p>
 *
 * @author Mark Gottschling on March 1, 2024 (Phase 2 rewrite May 25, 2026)
 */
public class BasicFloorGenerator implements IDungeonFloorGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        BlockSet blockSet = BlockProvider.get(motif, FLOOR_PATTERN, random);
        BlockState floorState = blockSet.get(FloorPattern.FLOOR).orElse(DEFAULT);
        BlockState alternateState = blockSet.get(FloorPattern.ALTERNATE_FLOOR).orElse(DEFAULT);

        int width = room.getWidth();
        int depth = room.getDepth();
        int originX = room.getOriginX();
        int originZ = room.getOriginZ();

        // Border (1 inset from the wall): two columns inset on the x-axis.
        int[] xBorders = {1, width - 2};
        for (int x : xBorders) {
            for (int z = 1; z < depth - 1; z++) {
                out.add(BlockStateCodec.placement(
                        originX + x, floorY, originZ + z, floorState));
            }
        }
        // Border: two rows inset on the z-axis (avoid double-placing the corners already covered above).
        int[] zBorders = {1, depth - 2};
        for (int z : zBorders) {
            for (int x = 2; x < width - 2; x++) {
                out.add(BlockStateCodec.placement(
                        originX + x, floorY, originZ + z, floorState));
            }
        }
        // Interior body: 45% chance of primary, 55% alternate.
        for (int x = 2; x < width - 2; x++) {
            for (int z = 2; z < depth - 2; z++) {
                BlockState pick = RandomHelper.checkProbability(random, 45) ? floorState : alternateState;
                out.add(BlockStateCodec.placement(
                        originX + x, floorY, originZ + z, pick));
            }
        }
    }
}
