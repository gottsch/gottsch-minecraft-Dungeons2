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


import mod.gottsch.forge.gottschcore.spatial.ICoords;

import java.util.List;

/**
 * @author Mark Gottschling on Oct Nov 21, 2023
 *
 */
public class DungeonLevel {
    // x-axis
    private int width;
    // z-axid
    private int depth;
    // y-axis
    private int height;

    private Grid2D grid;

    private IRoom startRoom;
    private IRoom endRoom;

    private List<IRoom> rooms;

    private ICoords spawnCoords;

    public DungeonLevel() {}

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Grid2D getGrid() {
        return grid;
    }

    public void setGrid(Grid2D grid) {
        this.grid = grid;
    }

    public IRoom getStartRoom() {
        return startRoom;
    }

    public void setStartRoom(IRoom startRoom) {
        this.startRoom = startRoom;
    }

    public IRoom getEndRoom() {
        return endRoom;
    }

    public void setEndRoom(IRoom endRoom) {
        this.endRoom = endRoom;
    }

    public List<IRoom> getRooms() {
        return rooms;
    }

    public void setRooms(List<IRoom> rooms) {
        this.rooms = rooms;
    }

    public ICoords getSpawnCoords() {
        return spawnCoords;
    }

    public void setSpawnCoords(ICoords spawnCoords) {
        this.spawnCoords = spawnCoords;
    }
}
