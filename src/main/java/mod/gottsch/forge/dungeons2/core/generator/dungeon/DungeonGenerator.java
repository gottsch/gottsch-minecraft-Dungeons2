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
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import mod.gottsch.forge.dungeons2.core.generator.GeneratorData;
import mod.gottsch.forge.dungeons2.core.generator.GeneratorResult;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.MazeLevelGenerator2D;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * @author Mark Gottschling on Oct Nov 14, 2023
 *
 */
public class DungeonGenerator {

    private Random random = new Random();

    private ICoords spawnCoords;

    /**
     *
     * @param level
     * @param random
     * @param spawnCoords the coords at which the top/surface level will generate at. ie offset coords
     * @return
     */
    public Optional<GeneratorResult<GeneratorData>> generate(ServerLevel level, RandomSource random, ICoords spawnCoords) {
        // TODO determine all the variables - # of levels, # of rooms per level, etc

        // TODO from spawn coords calculate how many levels are possible

        // TODO need to get the footprint of the entrance

        // TODO build from the bottom level up - does it matter? a inter-level room
        // will need to be calculated, number of stories etc, and added to the next level
        // as a fixed room

        // TODO currently this is level generator will ALL DEFAULT values.
        // generate a 2d level
        MazeLevelGenerator2D levelGenerator2D = new MazeLevelGenerator2D.Builder().build();

        Optional<ILevel2D> level2DOptional = levelGenerator2D.generate();
        if (level2DOptional.isEmpty()) {
            // TODO handle
        }

        ILevel2D level2D = level2DOptional.get();

        // TODO convert 2d level into 3d minecraft level
        List<IRoom> rooms = convertRooms(level2D.getRooms());

        // TODO
        addToWorld(level, spawnCoords, level2D.getGrid());

        return null;
    }

    private void addToWorld(ServerLevel level, ICoords spawnCoords, Grid2D grid) {
        BlockPos pos = spawnCoords.toPos();

        // TODO test add all grid walls to world
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int z = 0; z < grid.getHeight(); z++) {
                if (grid.getId(x, z) == Grid2D.TileIDs.WALL.getId()) {
                    level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     *
     * @param rooms2D
     * @return
     */
    public List<IRoom> convertRooms(List<IRoom2D> rooms2D) {
        List<IRoom> rooms = new ArrayList<>();
        for(IRoom2D room2D : rooms2D) {
            IRoom room = new Room();
            room
                    .setWidth(room2D.getWidth())
                    .setDepth(room2D.getHeight());

            // TODO test for start or end room or custom
            // TODO check against the custom room registry

            // set the height randomly with a max height of min(max, and walls)
            room.setHeight(Math.min(RandomHelper.randomInt(random, 5, 10), Math.max(room.getWidth(), room.getDepth())));

            rooms.add(room);
        }

        return rooms;
    }
}
