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

import mod.gottsch.forge.dungeons2.core.collection.Array2D;

import java.util.List;

/**
 * @author Mark Gottschling on Oct Nov 8, 2023
 *
 */
public class Grid2D extends Array2D<Cell> {

    public Grid2D(int width, int height) {
        super(Cell.class, width, height);
//        cells = new Cell[width][height];

        // initialize ie add walls to the borders
        initialize(width, height);
    }

    private Grid2D(Cell[][] cells) {
        super(cells);
    }

    private void initialize(int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
//                cells[x][y] = new Cell(x, y);
                put(x, y, new Cell(x, y));
                if (x == 0 || x == width -1 || y == 0 || y == height -1) {
//                    cells[x][y].setType(CellType.WALL);
                    get(x, y).setType(CellType.WALL);
                }
            }
        }
    }

    /**
     *
     * @param rooms
     */
    public void add(List<IRoom2D> rooms) {
        rooms.forEach(room -> {
            int xOffset = room.getOrigin().getX();
            int yOffset = room.getOrigin().getY();
            for (int x = 0; x < room.getWidth(); x++) {
                for (int y = 0; y < room.getHeight(); y++) {
                    // test for wall indexes
                    if (x ==0 || y == 0 || x == room.getWidth()-1 || y == room.getHeight()-1) {
//                        cells[xOffset + x][yOffset + y].setType(CellType.WALL);
                        get(xOffset + x, yOffset + y).setType(CellType.WALL);
                    } else {
                        // else update tiles with the id of the room
//                        cells[xOffset + x][yOffset + y].setType((CellType.ROOM));
//                        cells[xOffset + x][yOffset + y].setRegionId((int)room.getId());
                        get(xOffset + x, yOffset + y).setType((CellType.ROOM));
                        get(xOffset + x, yOffset + y).setRegionId((int)room.getId());
                    }
                }
            }
        });
    }

    /**
     * Deep clone: the returned grid has its own {@link Cell} instances, so
     * mutating the clone never affects this grid. A shallow copy (sharing Cell
     * objects) silently corrupted the source when callers stamped rooms into a
     * "scratch" clone (see {@code placeFillRooms}).
     */
    @Override
    public Grid2D clone() {
        Grid2D copy = new Grid2D(getWidth(), getHeight());
        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                Cell src = get(x, y);
                if (src == null) {
                    continue;
                }
                Cell dst = copy.get(x, y);
                dst.setType(src.getType());
                dst.setRegionId(src.getRegionId());
            }
        }
        return copy;
    }
}
