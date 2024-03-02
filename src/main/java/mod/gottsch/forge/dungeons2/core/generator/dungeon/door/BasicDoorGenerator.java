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

import mod.gottsch.forge.dungeonblocks.core.block.ModBlocks;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonLevel;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * @author Mark Gottschling on Dev 7, 2023
 *
 */
public class BasicDoorGenerator implements IDoorGenerator {

    @Override
    public void addToWorld(ServerLevel level, DungeonLevel dungeonLevel, ICoords spawnCoords) {
        Grid2D grid = dungeonLevel.getGrid();

        // all corridors and doors will generated at y+2
        ICoords normalSpawnCoords = spawnCoords.add(0, 2, 0);

        // scan all the tiles in the grid, looking for corridors
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int z = 0; z < grid.getHeight(); z++) {
                if (grid.get(x, z).getType() == CellType.DOOR) {
                    level.setBlock(normalSpawnCoords.add(x, 1, z).toPos(), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(normalSpawnCoords.add(x, 2, z).toPos(), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(normalSpawnCoords.add(x, 3, z).toPos(), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);

                    // test neighbors if corridor to determine the direction axis
                    Direction direction = Direction.DOWN;
                    if (grid.get(x, z-1).getType() == CellType.CORRIDOR) {
                        direction = Direction.NORTH;
                    } else if (grid.get(x, z+1).getType() == CellType.CORRIDOR) {
                        direction = Direction.SOUTH;
                    } else if (grid.get(x-1, z).getType() == CellType.CORRIDOR) {
                        direction = Direction.WEST;
                    } else if (grid.get(x+1, z).getType() == CellType.CORRIDOR) {
                        direction = Direction.EAST;
                    }

                    if (direction != Direction.DOWN) {
                        // TODO add a dungeon door
                        level.setBlock(normalSpawnCoords.add(x, 1, z).toPos(), ModBlocks.SPRUCE_DUNGEON_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, direction).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
                        level.setBlock(normalSpawnCoords.add(x, 2, z).toPos(), ModBlocks.SPRUCE_DUNGEON_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, direction).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
                    }
                }
            }
        }
    }
}
