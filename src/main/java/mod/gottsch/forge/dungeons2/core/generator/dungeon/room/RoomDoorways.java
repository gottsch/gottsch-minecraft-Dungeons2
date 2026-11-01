/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
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

import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How many ways into a room there are, counted the way a player counts them.
 *
 * <p>The maze stores a 2-wide opening as two adjacent doorway cells, so the number of cells is not
 * the number of doors. This groups 4-connected cells into runs, the same grouping
 * {@code DoorJambsWallPatternProvider} frames, and counts the runs.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class RoomDoorways {

    private RoomDoorways() {}

    /** The number of doorways into {@code room}: runs of adjacent doorway cells. */
    public static int count(RoomData room) {
        return count(room.getDoorways());
    }

    /** As above, over a bare cell list. Duplicate cells count once. */
    public static int count(List<Coords2D> cells) {
        Set<Coords2D> unvisited = new HashSet<>(cells);
        int runs = 0;
        while (!unvisited.isEmpty()) {
            Coords2D start = unvisited.iterator().next();
            unvisited.remove(start);
            runs++;
            Deque<Coords2D> frontier = new ArrayDeque<>();
            frontier.add(start);
            while (!frontier.isEmpty()) {
                Coords2D cell = frontier.poll();
                for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Coords2D next = new Coords2D(cell.getX() + step[0], cell.getY() + step[1]);
                    if (unvisited.remove(next)) {
                        frontier.add(next);
                    }
                }
            }
        }
        return runs;
    }
}
