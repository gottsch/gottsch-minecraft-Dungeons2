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

import java.util.List;

/**
 * 0 = rock
 * 1 = wall
 * n = id of region
 */
public class Grid2D {
    public static enum TileIDs {
        ROCK(0),
        WALL(1),
        CONNECTOR(2),
        DOOR(3);

        final int id;
        TileIDs(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    };

    private byte[][] tiles;

    public Grid2D(int width, int height) {
        tiles = new byte[width][height];

        // initialize ie add walls to the borders
        initialize(width, height);
    }

    private void initialize(int width, int height) {
        for (int x = 0; x < width; x++) {
            tiles[x][0] = 1;
            tiles[x][height-1] = 1;
        }

        for (int y = 0; y < height; y++) {
            tiles[0][y] = 1;
            tiles[width-1][y] = 1;
        }
    }

    public int getWidth() {
        return tiles.length;
    }

    public int getHeight() {
        return tiles[0].length;
    }

    /**
     *
     * @param x
     * @param y
     * @return
     */
    public byte getId(int x, int y) {
        return tiles[x][y];
    }

    public void setId(int x, int y, byte id) {
        tiles[x][y] = id;
    }

    public byte getId(Coords2D coords) {
        return tiles[coords.getX()][coords.getY()];
    }

    public void setId(Coords2D coords, byte id) {
        tiles[coords.getX()][coords.getY()] = id;
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
                        tiles[xOffset + x][yOffset + y] = (byte)1;
                    } else {
                        // else update tiles with the id of the room
                        tiles[xOffset + x][yOffset + y] = (byte)room.getId(); //Integer.valueOf(room.getId()).byteValue();
                    }
                }
            }
        });
    }
}
