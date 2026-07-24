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
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Cell;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the connectivity guarantee: the start room can always reach the end
 * room, and the end room keeps exactly one entrance (degrees = 1).
 *
 * <p>Pure POJO &mdash; runs the planner and walks the resulting maze grid without
 * Minecraft on the classpath.</p>
 *
 * @author Mark Gottschling on Jun 15, 2026
 */
class MazeConnectivityTest {

    private static final ICoords ANCHOR = new Coords(0, 0, 0);
    private static final int SURFACE_Y = 72;

    @Test
    void endRoomIsAlwaysReachableFromStart() {
        // 200 seeds: the connector-graph split that exercises forceConnect's
        // carve fallback first shows up around seed 84, so a narrower range
        // (e.g. <60) silently misses it.
        for (long seed = 0; seed < 200; seed++) {
            DungeonLayout layout = new DungeonStackPlanner(
                    seed, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.SMALL)
                    .withFloorCount(1)
                    .plan()
                    .orElseThrow(() -> new AssertionError("planning failed for a SMALL single-floor dungeon"));

            FloorLayout floor = layout.getFloors().get(0);
            Grid2D grid = floor.getGrid();
            assertNotNull(grid, "floor should carry its transient grid for the test");

            RoomData start = roomWithRole(floor, RoomRole.START);
            RoomData end = roomWithRole(floor, RoomRole.END);
            assertNotNull(start, "seed " + seed + ": missing START room");
            assertNotNull(end, "seed " + seed + ": missing END room");

            assertTrue(reachable(grid, start, end),
                    "seed " + seed + ": END room is not reachable from START");
        }
    }

    @Test
    void endRoomHasExactlyOneEntrance() {
        for (long seed = 0; seed < 200; seed++) {
            DungeonLayout layout = new DungeonStackPlanner(
                    seed, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.SMALL)
                    .withFloorCount(1)
                    .plan()
                    .orElseThrow();
            FloorLayout floor = layout.getFloors().get(0);
            RoomData end = roomWithRole(floor, RoomRole.END);
            assertEquals(1, end.getDoorways().size(),
                    "seed " + seed + ": terminal room should have exactly one entrance");
        }
    }

    private RoomData roomWithRole(FloorLayout floor, RoomRole role) {
        return floor.getRooms().stream()
                .filter(r -> r.getRole() == role)
                .findFirst().orElse(null);
    }

    /** BFS over walkable cells (room interiors, corridors, doors) from START to END. */
    private boolean reachable(Grid2D grid, RoomData start, RoomData end) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        boolean[][] visited = new boolean[w][h];

        // End footprint interior bounds.
        int endX0 = end.getOriginX() + 1, endX1 = end.getOriginX() + end.getWidth() - 2;
        int endZ0 = end.getOriginZ() + 1, endZ1 = end.getOriginZ() + end.getDepth() - 2;

        Deque<int[]> queue = new ArrayDeque<>();
        int sx = start.getOriginX() + 1, sz = start.getOriginZ() + 1;
        queue.add(new int[]{sx, sz});
        visited[sx][sz] = true;

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int x = cur[0], z = cur[1];
            if (x >= endX0 && x <= endX1 && z >= endZ0 && z <= endZ1) {
                return true;
            }
            for (int[] d : dirs) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h || visited[nx][nz]) {
                    continue;
                }
                if (isWalkable(grid.get(nx, nz))) {
                    visited[nx][nz] = true;
                    queue.add(new int[]{nx, nz});
                }
            }
        }
        return false;
    }

    private boolean isWalkable(Cell cell) {
        CellType t = cell.getType();
        return t == CellType.ROOM || t == CellType.CORRIDOR || t == CellType.DOOR;
    }
}
