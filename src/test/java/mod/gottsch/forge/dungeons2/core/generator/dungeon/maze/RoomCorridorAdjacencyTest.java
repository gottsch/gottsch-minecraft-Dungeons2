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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.maze;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression guard: a ROOM (interior) cell must never be orthogonally adjacent
 * to a CORRIDOR cell. That means the room's wall ring is missing there and the
 * corridor renders as a wall cutting through the room.
 *
 * <p>Root cause when this last failed: {@code Grid2D.clone()} was a shallow copy
 * that shared {@code Cell} objects, so {@code placeFillRooms} stamping fill rooms
 * into its scratch clone silently mutated the real grid. Rooms later dropped from
 * the room list (e.g. near an anchor) left orphan ROOM cells no room owned, whose
 * walls dilation then ate. Fixed by making {@code Grid2D.clone()} a deep copy.</p>
 */
class RoomCorridorAdjacencyTest {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Test
    void roomsAreNeverDirectlyAdjacentToCorridors() {
        assertNoAdjacency(1);
    }

    /**
     * Re-tests the Jul 21 "dilation merges parallel corridors into a room-touching
     * blob" theory now that the Jul 23 Z-mirror render bug (which corrupted every
     * in-game observation made about corridor width) is fixed. This works purely at
     * the grid/layout level via {@link DungeonStackPlanner#withCorridorWidth}, so it
     * cannot see the Z-mirror class of bug itself &mdash; it only re-checks whether
     * dilation is geometrically safe on its own terms.
     */
    @Test
    void dilatedCorridorsAreStillNeverAdjacentToRooms() {
        assertNoAdjacency(2);
        assertNoAdjacency(3);
    }

    private void assertNoAdjacency(int corridorWidth) {
        int offending = 0;
        long firstBadSeed = -1;
        for (long seed = 0; seed < 200; seed++) {
            DungeonLayout layout = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.LARGE).withFloorCount(1)
                    .withCorridorWidth(corridorWidth)
                    .plan().orElseThrow();
            FloorLayout f0 = layout.getFloors().get(0);
            Grid2D grid = f0.getGrid();
            for (int x = 0; x < grid.getWidth(); x++) {
                for (int z = 0; z < grid.getHeight(); z++) {
                    if (grid.get(x, z) == null || grid.get(x, z).getType() != CellType.ROOM) {
                        continue;
                    }
                    for (int[] d : DIRS) {
                        int nx = x + d[0], nz = z + d[1];
                        if (nx < 0 || nz < 0 || nx >= grid.getWidth() || nz >= grid.getHeight()) {
                            continue;
                        }
                        var nc = grid.get(nx, nz);
                        if (nc != null && nc.getType() == CellType.CORRIDOR) {
                            offending++;
                            if (firstBadSeed < 0) {
                                firstBadSeed = seed;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(0, offending,
                "corridor_width=" + corridorWidth + ": ROOM cells must never touch CORRIDOR cells "
                        + "(no wall between). " + offending + " offending cells; first at seed " + firstBadSeed);
    }
}
