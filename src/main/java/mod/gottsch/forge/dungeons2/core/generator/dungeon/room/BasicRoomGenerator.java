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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.collection.Array2D;
import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.IRoomElementDecorator;
import mod.gottsch.forge.dungeons2.core.enums.FloorElementType;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.WallElementType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonLevel;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.BasicCeilingGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.IDungeonCeilingGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.BasicFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.BasicWallGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.IDungeonWallGenerator;
import mod.gottsch.forge.dungeons2.core.registry.DecoratorRegistry;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * @author Mark Gottschling on Dec 7, 2023
 */
public class BasicRoomGenerator implements IRoomGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    /**
     * TODO add config / theme
     *
     * @param level
     * @param dungeonLevel
     * @param spawnCoords
     */
    @Override
    public void addToWorld(ServerLevel level, RandomSource random, DungeonLevel dungeonLevel, ICoords spawnCoords, IDungeonMotif motif) {
        // a typical room will generate at y+2
        ICoords normalizedSpawnCoords = spawnCoords.add(0, 2, 0);

        // TODO revisit how decorators are registered - why introduce FloorElementType when Pattern already exist
        IRoomElementDecorator floorDecorator;
        List<IRoomElementDecorator> floorDecoratorList = DecoratorRegistry.get(FloorElementType.FLOOR);
        if (floorDecoratorList.isEmpty()) {
            return;
        }
        floorDecorator = floorDecoratorList.get(RandomHelper.randomInt(random, 0, floorDecoratorList.size() - 1));

        Optional<IRoomElementDecorator> wallDecorator = Optional.empty();
        List<IRoomElementDecorator> wallDecorators = DecoratorRegistry.get(WallElementType.WALL);
        if (!wallDecorators.isEmpty()) {
            wallDecorator = Optional.of(wallDecorators.get(random.nextInt(wallDecorators.size())));
        }

        for (IRoom room : dungeonLevel.getRooms()) {
            // TODO determine if custom room
            // TODO use resource location to get template
            // TODO calculate offset if any

            addRoomToWorld(level, random, room, normalizedSpawnCoords, motif);

            // perform decorations
            if (wallDecorator.isPresent()) {
                // TODO replace grid with Array2D
                Grid2D grid = wallDecorator.get().decorate(level, random, null, room, normalizedSpawnCoords, motif);
            }
            wallDecorator.get().decorate(level, random, null, room, normalizedSpawnCoords, motif);
            floorDecorator.decorate(level, random, null, room, normalizedSpawnCoords, motif);
            // ceilingDecorator.decorate(level, random, null, room, normailzedSpawnCoords, motif);
        }
    }

    // TODO will have to return a map of grids - each decorator may need to know about the others
    private void addRoomToWorld(ServerLevel level, RandomSource random, IRoom room, ICoords normalSpawnCoords, IDungeonMotif motif) {
        // TODO wallGenerator
        IDungeonWallGenerator wallGenerator = selectWallGenerator(level, motif);
        // TODO properly develop the 2d map from the walls
        IDungeonFloorGenerator floorGenerator = selectFloorGenerator(level, motif);
        IDungeonCeilingGenerator ceilingGenerator = selectCeilingGenerator(level, motif);

        IBlockProvider blockProvider = IBlockProvider.get(motif);

        // TODO how to pass ALL the grids back up to the generators and decorators
        // generate the walls
        Array2D<Integer> wallGrid = wallGenerator.addToWorld(level, random, room, normalSpawnCoords, motif);

        // generate the floor
        Array2D<Integer> floorGrid = floorGenerator.addToWorld(level, random, room, normalSpawnCoords, motif);

        Array2D<Integer> ceilingGrid = ceilingGenerator.addToWorld(level, random, room, normalSpawnCoords, motif);
//        // build box (minus the floor)
//        for (int y = 1; y < room.getHeight(); y++) {
//            for (int x = 0; x < room.getWidth(); x++) {
//                for (int z = 0; z < room.getDepth(); z++) {
//                    // edges
//                    if (
//                            ((x == 0 || x == room.getWidth() - 1) && (z == 0 || z == room.getDepth() - 1))
//                                    || ((x == 0 || x == room.getWidth() - 1) && (y == 0 || y == room.getHeight() - 1))
//                                    || ((z == 0 || z == room.getDepth() - 1) && (y == 0 || y == room.getHeight() - 1))
//                    ) {
//                        // skip the edges as they aren't visible anyway
//                    } else {
//                        // NOTE rooms will overwrite corridor walls? no can't do that! arg.
//                        // NOTE meaning - can't have good wall designs unless make the wall thicker
//                        // NOTE OR build a buffer around all rooms during 2D build - but then connectors would be out of wack
//                        // build ceiling
////                        if (y == room.getHeight() - 1) {
//                            // TODO extract to a ceiling generator
//                            // TEMP don't gen the ceiling
////                            level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), blockProvider.get(FloorPattern.FLOOR).orElse(DEFAULT));
////                        }
//                        // TODO extract to a wall generator and it goes first as it will determine how
//                        // floor and ceiling is generated & decorated
//                        // build walls
////                        else if (x == 0 || x == room.getWidth() - 1 || z == 0 || z == room.getDepth() - 1) {
////                            // top/bottom corners of wall
////                            if (
////                                    (y == 1 || y == room.getHeight() - 2) &&
////                                            (
////                                                    ((x == 0 || x == room.getWidth() - 1) && (z == 1 || z == room.getDepth() - 2))
////                                                            || ((x == 1 || x == room.getWidth() - 2) && (z == 0 || z == room.getDepth() - 1))
////                                            )
////                            ) {
////                                level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(),
////                                        blockProvider.get(WallPattern.CORNER).orElse(DEFAULT));
////                            } else {
////                                level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(),
////                                        blockProvider.get(WallPattern.WALL).orElse(DEFAULT));
////                            }
////                        } else {
////                            // replace with air ie inside the room
////                            level.setBlock(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), Blocks.AIR.defaultBlockState(), 3);
////                        }
//                    }
//                }
//            }
//        }
    }

    public IDungeonWallGenerator selectWallGenerator(ServerLevel level, IDungeonMotif motif) {
        return new BasicWallGenerator();
    }

    public IDungeonFloorGenerator selectFloorGenerator(ServerLevel level, IDungeonMotif motif) {
        // TODO lookup registry for the floors by motif
        return new BasicFloorGenerator();
    }

    public IDungeonCeilingGenerator selectCeilingGenerator(ServerLevel level, IDungeonMotif motif) {
        return new BasicCeilingGenerator();
    }
}