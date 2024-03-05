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

import mod.gottsch.forge.dungeons2.core.collection.Array2D;
import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.pattern.floor.FloorPattern;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author Mark Gottschling on March 1, 2024
 *
 */
public class BasicFloorGenerator implements IDungeonFloorGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    @Override
    public Array2D<Integer> addToWorld(ServerLevel level, RandomSource random, IRoom room, ICoords normalSpawnCoords, IDungeonMotif motif) {
        IBlockProvider blockProvider = IBlockProvider.get(motif);

        // TODO determine if using a sunken floor
        // get the size of the footprint
        Coords2D size = new Coords2D(room.getWidth(), room.getDepth());
        Array2D<Integer> floorGrid = new Array2D<>(Integer.class, size.getX(), size.getY());

        int y = 0;
        for (int x = 0; x < room.getWidth(); x++) {
            for (int z = 0; z < room.getDepth(); z++) {
                if (
                        ((x == 0 || x == room.getWidth() - 1) && (z == 0 || z == room.getDepth() - 1))
                                || ((x == 0 || x == room.getWidth() - 1))
                                || ((z == 0 || z == room.getDepth() - 1))
                ) {
                    // skip the edges as they aren't visible anyway
                }
                else if (
                        (x == 1 || x == room.getWidth() - 2)
                        || (z == 1 || z == room.getDepth() - 2)
                ) {
                    level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), blockProvider.get(FloorPattern.FLOOR).orElse(DEFAULT));
                    floorGrid.put(x, z, 1);
                }
                else {
                    level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), blockProvider.get(FloorPattern.ALTERNATE_FLOOR).orElse(DEFAULT));
                    floorGrid.put(x, z, 1);
                }
            }
        }

        return floorGrid;
    }
}
