/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.door;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;
import mod.gottsch.forge.dungeons2.core.pattern.door.DoorPattern;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

import static mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns.DOOR_PATTERN;

/**
 * Builds one doorway as a 4-block column of {@link BlockPlacement}s.
 *
 * <p>Column layout (relative to floor surface Y):</p>
 * <ul>
 *     <li>Y = floorY: door-sill block (a floor-style block from
 *         {@link DoorPattern#FLOOR})</li>
 *     <li>Y = floorY+1: door lower half (or air if there's no valid facing)</li>
 *     <li>Y = floorY+2: door upper half (or air)</li>
 *     <li>Y = floorY+3: lintel block ({@link DoorPattern#LINTEL})</li>
 * </ul>
 *
 * <p>The {@link DoorData#getFacing()} direction (planner-resolved at Phase 1
 * convert time) is used to set the {@code FACING} property of both door
 * halves. Doors with {@link Direction2D#NONE} facing emit air halves instead
 * of doors &mdash; the column is still walkable but has no actual door.</p>
 *
 * @author Mark Gottschling on Dev 7, 2023 (Phase 2 rewrite May 25, 2026)
 */
public class BasicDoorGenerator implements IDoorGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    @Override
    public void build(DoorData door, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        BlockSet blockSet = BlockProvider.get(motif, DOOR_PATTERN, random);

        int x = door.getX();
        int z = door.getZ();

        // Sill (floor) and lintel (top).
        out.add(BlockStateCodec.placement(x, floorY, z,
                blockSet.get(DoorPattern.FLOOR).orElse(DEFAULT)));
        out.add(BlockStateCodec.placement(x, floorY + 3, z,
                blockSet.get(DoorPattern.LINTEL).orElse(DEFAULT)));

        // Door halves.
        Direction direction = toMcDirection(door.getFacing());
        BlockState doorBase = blockSet.get(DoorPattern.DOOR).orElse(Blocks.OAK_DOOR.defaultBlockState());
        if (direction != null) {
            BlockState lower = doorBase.setValue(DoorBlock.FACING, direction)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            BlockState upper = doorBase.setValue(DoorBlock.FACING, direction)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            out.add(BlockStateCodec.placement(x, floorY + 1, z, lower));
            out.add(BlockStateCodec.placement(x, floorY + 2, z, upper));
        } else {
            // No valid facing: emit air halves so the doorway is at least walkable.
            BlockState airState = Blocks.AIR.defaultBlockState();
            out.add(BlockStateCodec.placement(x, floorY + 1, z, airState));
            out.add(BlockStateCodec.placement(x, floorY + 2, z, airState));
        }
    }

    /** Map planner-level {@link Direction2D} to Minecraft {@link Direction}, or null for NONE. */
    private static Direction toMcDirection(Direction2D facing) {
        if (facing == null) return null;
        return switch (facing) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            default -> null;
        };
    }
}
