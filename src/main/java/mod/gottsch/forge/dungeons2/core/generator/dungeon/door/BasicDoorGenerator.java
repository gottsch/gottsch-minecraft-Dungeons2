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

import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonLevel;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.pattern.door.DoorPattern;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Arrays;
import java.util.List;

/**
 * @author Mark Gottschling on Dev 7, 2023
 *
 */
public class BasicDoorGenerator implements IDoorGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();
    private IDungeonMotif cachedMotif;
    private IBlockProvider cachedBlockProvider;

    @Override
    public void addToWorld(ServerLevel level, RandomSource random, DungeonLevel dungeonLevel, ICoords spawnCoords, IDungeonMotif motif) {
        Grid2D grid = dungeonLevel.getGrid();

        // all corridors and doors will generated at y+2
        ICoords normalSpawnCoords = spawnCoords.add(0, 2, 0);

        IBlockProvider blockProvider = getBlockProvider(motif);

        // scan all the tiles in the grid, looking for corridors
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int z = 0; z < grid.getHeight(); z++) {
                if (grid.get(x, z).getType() == CellType.DOOR) {
                    level.setBlockAndUpdate(normalSpawnCoords.add(x, 0, z).toPos(), blockProvider.get(DoorPattern.FLOOR).orElse(DEFAULT));
                    level.setBlockAndUpdate(normalSpawnCoords.add(x, 1, z).toPos(), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(normalSpawnCoords.add(x, 2, z).toPos(), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(normalSpawnCoords.add(x, 3, z).toPos(), blockProvider.get(DoorPattern.LINTEL).orElse(DEFAULT));

                    // test neighbors if corridor to determine the direction axis
                    List<CellType> validCellTypes = Arrays.asList(CellType.CORRIDOR, CellType.ROOM);

                    // TODO add random conditional if door gets added
                    // ie if corridor to corridor, then 35%, else 85%
                    Direction direction = Direction.DOWN;
                    if (validCellTypes.contains(grid.get(x, z-1).getType())) {
                        direction = Direction.NORTH;
                    } else if (validCellTypes.contains(grid.get(x, z+1).getType())) {
                        direction = Direction.SOUTH;
                    } else if (validCellTypes.contains(grid.get(x-1, z).getType())) {
                        direction = Direction.WEST;
                    } else if (validCellTypes.contains(grid.get(x+1, z).getType())) {
                        direction = Direction.EAST;
                    }

                    if (direction != Direction.DOWN) {
                        // add a dungeon door
                        level.setBlockAndUpdate(normalSpawnCoords.add(x, 1, z).toPos(), blockProvider.get(DoorPattern.DOOR).orElse(Blocks.OAK_DOOR.defaultBlockState()).setValue(DoorBlock.FACING, direction).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                        level.setBlockAndUpdate(normalSpawnCoords.add(x, 2, z).toPos(), blockProvider.get(DoorPattern.DOOR).orElse(Blocks.OAK_DOOR.defaultBlockState()).setValue(DoorBlock.FACING, direction).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    }
                }
            }
        }
    }

    public IBlockProvider getBlockProvider(IDungeonMotif motif) {
        IBlockProvider blockProvider;
        if (cachedMotif != null && cachedMotif == motif) {
            blockProvider = cachedBlockProvider;
        }
        else {
            cachedMotif = motif;
            blockProvider = IBlockProvider.get(motif);
            cachedBlockProvider = blockProvider;
        }
        return blockProvider;
    }
}
