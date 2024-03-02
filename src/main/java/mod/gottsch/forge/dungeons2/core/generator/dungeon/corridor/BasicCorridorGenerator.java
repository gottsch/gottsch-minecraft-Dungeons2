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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.*;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * @author Mark Gottschling on Dec 5, 2023
 */
public class BasicCorridorGenerator implements ICorridorGenerator {

    /*
     * A DOOR is treated as a wall when generating the corridors.
     */
    private static final List<CellType> WALL_ELEMENTS = Arrays.asList(CellType.ROCK, CellType.WALL, CellType.DOOR, CellType.CONNECTOR);

    @Override
    public void addToWorld(ServerLevel level, Grid2D grid, ICoords spawnCoords) {
        // some working variables
        List<PrimsTile2D> activeList = new ArrayList<>();

        /*
         * a grid to keep track of what tiles have been placed/visited with same dimensions as the grid
         */
        BooleanGrid visitedGrid = new BooleanGrid(grid.getWidth(), grid.getHeight());

        // scan all the tiles in the grid, looking for corridors
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int z = 0; z < grid.getHeight(); z++) {

                // if already visited then move to the next tile
                if (visitedGrid.get(x, z)) {
                    continue;
                }

                if (isCorridor(grid, x, z)) {
                    // create a Tile
                    PrimsTile2D tile = new PrimsTile2D(new Coords2D(x, z), Direction2D.NONE);
                    activeList.add(tile);
                }

                while (!activeList.isEmpty()) {
                    // randomly select a cell from the active list
                    PrimsTile2D active = activeList.get(0);

                    /*
                     * examine all 8 surrounding tiles
                     */
                    visitGrid(level, spawnCoords, activeList, grid, visitedGrid, new Coords2D(active.getX(), active.getY()-1));
                    visitGrid(level, spawnCoords, activeList, grid, visitedGrid, new Coords2D(active.getX(), active.getY()+1));
                    visitGrid(level, spawnCoords, activeList, grid, visitedGrid, new Coords2D(active.getX()-1, active.getY()));
                    visitGrid(level, spawnCoords, activeList, grid, visitedGrid, new Coords2D(active.getX()+1, active.getY()));

                    visitGrid(level, spawnCoords, activeList, grid, visitedGrid, new Coords2D(active.getX()-1, active.getY()-1));
                    visitGrid(level, spawnCoords, activeList, grid, visitedGrid, new Coords2D(active.getX()+1, active.getY()-1));
                    visitGrid(level, spawnCoords, activeList, grid, visitedGrid, new Coords2D(active.getX()-1, active.getY()+1));
                    visitGrid(level, spawnCoords, activeList, grid, visitedGrid, new Coords2D(active.getX()+1, active.getY()+1));

                    // build the current corridor to the world
                    addCorridorToWorld(level, spawnCoords.add(active.getX(), 0, active.getY()));
                    visitedGrid.put(active.getCoords(), true);

                    // remove active from list
                    activeList.remove(active);
                }
            }
        }
    }

    /**
     * @param level the ServerLevel
     * @param spawnCoords the spawn coords / offset
     * @param grid the populated grid of the dungeon level
     * @param visitedGrid the boolean grid of visited cells
     * @param visitCoords the current coords to visit on the grids
     * @return
     */
    private void visitGrid(ServerLevel level, ICoords spawnCoords, List<PrimsTile2D> activeList, Grid2D grid, BooleanGrid visitedGrid, Coords2D visitCoords) {
        if (!visitedGrid.get(visitCoords)) {
            if (WALL_ELEMENTS.contains(grid.get(visitCoords).getType())) {
                addWallToWorld(level, spawnCoords.add(visitCoords.getX(), 0, visitCoords.getY()));
                visitedGrid.put(visitCoords, true);
            } else if (isCorridor(grid, visitCoords)) {
                activeList.add(new PrimsTile2D(visitCoords, Direction2D.NONE));
            }
        }
    }


    // TODO could take in some sort of config for the style etc
    @Override
    public void addWallToWorld(ServerLevel level, ICoords coords) {
        // build bottom-up
        for (int i = 0; i < 5; i++) {
            level.setBlock(coords.add(0, i, 0).toPos(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
    }

    @Override
    public void addCorridorToWorld(ServerLevel level, ICoords coords) {
        level.setBlock(coords.toPos(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        for (int i = 1; i < 4; i++) {
            level.setBlock(coords.add(0, i, 0).toPos(), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(coords.add(0, 4, 0).toPos(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
    }

    private boolean isCorridor(Grid2D grid, int x, int z) {
        return grid.get(x, z).getType() == CellType.CORRIDOR;
    }

    private boolean isCorridor(Grid2D grid, Coords2D coords) {
        return isCorridor(grid, coords.getX(), coords.getY());
    }
}
