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

import mod.gottsch.forge.dungeons2.core.decorator.WallPattern;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonLevel;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.BasicFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * @author Mark Gottschling on Dec 7, 2023
 *
 */
public class BasicRoomGenerator implements IRoomGenerator {

    /**
     * TODO add config / theme
     *
     * @param level
     * @param dungeonLevel
     * @param spawnCoords
     */
    @Override
    public void addToWorld(ServerLevel level, DungeonLevel dungeonLevel, ICoords spawnCoords, DungeonMotif motif) {
        // a typical room will generate at y+2
        ICoords normalizedSpawnCoords = spawnCoords.add(0, 2, 0);

        dungeonLevel.getRooms().forEach(room -> {
            // TODO determine if custom room
            // TODO use resource location to get template
            // TODO calculate offset if any

            addRoomToWorld(level, room, normalizedSpawnCoords, motif);

        });
    }

    private void addRoomToWorld(ServerLevel level, IRoom room, ICoords normalSpawnCoords, DungeonMotif motif) {
        // TODO separate into different sections/builders
        // ie floor builder, ceiling builder
        IDungeonFloorGenerator floorGenerator = selectFloorGenerator(level, motif);

        // build box
        for (int y = 0; y < room.getHeight(); y++) {
            for (int x = 0; x < room.getWidth(); x++) {
                for (int z = 0; z < room.getDepth(); z++) {
                    // edges
                    if (
                            ((x == 0 || x == room.getWidth() - 1) && (z == 0 || z == room.getDepth() - 1))
                                    || ((x == 0 || x == room.getWidth() - 1) && (y == 0 || y == room.getHeight() - 1))
                                    || ((z == 0 || z == room.getDepth() - 1) && (y == 0 || y == room.getHeight() - 1))
                    ) {
                        // skip the edges as they aren't visible anyway
                    } else {
                        // NOTE rooms will overwrite corridor walls? no can't do that! arg.
                        // NOTE meaning - can't have good wall designs unless make the wall thicker
                        // NOTE OR build a buffer around all rooms during 2D build - but then connectors would be out of wack
                        // build floor && ceiling
                        if (y == 0 || y == room.getHeight()-1) {
                            // TODO this will be separate as there will be a ceiling builder and a floor builder
                            level.setBlock(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), Blocks.COBBLESTONE.defaultBlockState(), 3);
                        }
                        // build walls
                        else if (x == 0 || x == room.getWidth()-1 || z == 0 || z == room.getDepth()-1) {
                            // top/bottom corners of wall
                            // TODO need to check if room has sunken floor
                            if (y == 1 || y == room.getHeight() - 2) {
                                if (
                                        ((x == 0 || x == room.getWidth()-1) && (z == 1 || z == room.getDepth() -2))
                                        || ((x ==1 || x == room.getWidth() -2) && ( z == 0 || z == room.getDepth() - 1))) {
                                    level.setBlock(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                                    level.setBlock(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), blockProvider.get(WallPattern.TOP_CORNER), 3);
                                }
                            } else {
                                level.setBlock(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 3);
                            }
                        } else {
                            // replace with air ie inside the room
                            level.setBlock(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    public IDungeonFloorGenerator selectFloorGenerator(ServerLevel level, DungeonMotif motif) {
        // TODO lookup registry for the floors by motif
        return new BasicFloorGenerator();
    }
}
