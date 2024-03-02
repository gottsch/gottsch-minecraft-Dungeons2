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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.BasicCorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.ICorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.BasicDoorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.IDoorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.MazeLevelGenerator2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.IRoomGenerator;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.*;

/**
 * @author Mark Gottschling on Oct Nov 14, 2023
 *
 */
public class DungeonGenerator {

    private Random random = new Random();

    private ICoords spawnCoords;

    /**
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
//        MazeLevelGenerator2D levelGenerator2D = new MazeLevelGenerator2D.Builder().build();
        MazeLevelGenerator2D levelGenerator2D = new MazeLevelGenerator2D.Builder()
                .with($ -> {
                    $.width = 45;
                    $.height = 45;
                    $.numberOfRooms = 35;
                    $.attemptsMax = 1000;
                    $.runFactor = 0.9;
                    $.curveFactor = 0.75;
                    $.minCorridorSize = 25;
                    $.maxCorridorSize = 50;
                    $.fillAttempts = 4;
                    $.fillRoomsPerSize = 5;
                 //   $.fillRoomSizes = {{7, 7}, {5,5}}; // TODO provide sizes of fill rooms
                }).build();

        Optional<ILevel2D> level2DOptional = levelGenerator2D.generate();
        if (level2DOptional.isEmpty()) {
            // TODO handle
        }

        ILevel2D level2D = level2DOptional.get();

        // convert 2d level into 3d minecraft level
        List<IRoom> rooms = convertRooms(level2D.getRooms());

        DungeonLevel dungeonLevel = new DungeonLevel();
        dungeonLevel.setGrid(level2D.getGrid());
        dungeonLevel.setRooms(rooms);


        // TODO
        addToWorld(level, spawnCoords, dungeonLevel);

        return null;
    }

    /**
     * adds the hallways/corridors to the world
     *
     * @param level
     * @param spawnCoords
     * @param dungeonLevel
     */
    private void addToWorld(ServerLevel level, ICoords spawnCoords, DungeonLevel dungeonLevel) {
        // get the BlocksPos from the coords
        BlockPos pos = spawnCoords.toPos();
        Grid2D grid = dungeonLevel.getGrid();

        // TODO should roomMap be a property of DungeonGenerator ?
        // map rooms by id
        Map<Integer, IRoom> roomMap = new HashMap<>();
        dungeonLevel.getRooms().forEach(room -> {
            roomMap.put(room.getId(), room);
        });

        // TODO a dungeon level is 12 blocks high, so calculate the correct position for
        // corridors and standard (non-offset) rooms ie spawnCoords.y + 2

        /*
         * a coridoor generator
         */
        // TODO randomly pick from registered generators by theme/config
        ICorridorGenerator corridorGenerator = new BasicCorridorGenerator();

        corridorGenerator.addToWorld(level, grid, spawnCoords.add(0, 2, 0));

        /*
         * a room generator
         */
        IRoomGenerator roomGenerator = new BasicRoomGenerator();
        roomGenerator.addToWorld(level, dungeonLevel, spawnCoords, DungeonMotif.STONEBRICK);

        /*
         * a door generator
         */
        IDoorGenerator doorGenerator = new BasicDoorGenerator();
        doorGenerator.addToWorld(level, dungeonLevel, spawnCoords);
    }

    private void addCorridorToWorld (ServerLevel level, DungeonLevel dungeonLevel, Coords2D coords2D){

    }

    /**
     *
     * @param rooms2D
     * @return
     */
    public List<IRoom> convertRooms (List < IRoom2D > rooms2D) {
        List<IRoom> rooms = new ArrayList<>();
        for (IRoom2D room2D : rooms2D) {
            IRoom room = new Room();
            room
                    .setId(room2D.getId())
                    .setWidth(room2D.getWidth())
                    .setDepth(room2D.getHeight())
                    .setCoords(new Coords(room2D.getOrigin().getX(), 0, room2D.getOrigin().getY()));


            // TODO test for start or end room or custom
            // TODO check against the custom room registry

            // set the height randomly with a max height of min(max, and walls)
            room.setHeight(Math.min(RandomHelper.randomInt(random, 5, 10), Math.max(room.getWidth(), room.getDepth())));

            rooms.add(room);
        }

        return rooms;
    }
}
