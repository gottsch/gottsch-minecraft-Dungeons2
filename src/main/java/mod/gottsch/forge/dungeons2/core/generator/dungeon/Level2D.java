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
 * @author Mark Gottschling on Oct Nov 8, 2023
 *
 */
public class Level2D implements ILevel2D {
    private int width;
    private int height;
    private Grid2D grid;

    private IRoom2D startRoom;
    private IRoom2D endRoom;
    private List<IRoom2D> rooms;

    /**
     *
     * @param width
     * @param height
     */
    public Level2D(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new Grid2D(width, height);
    }

    @Override
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public IRoom2D getStartRoom() {
        return startRoom;
    }

    @Override
    public void setStartRoom(IRoom2D startRoom) {
        this.startRoom = startRoom;
    }

    @Override
    public IRoom2D getEndRoom() {
        return endRoom;
    }

    @Override
    public void setEndRoom(IRoom2D endRoom) {
        this.endRoom = endRoom;
    }

    @Override
    public List<IRoom2D> getRooms() {
        return rooms;
    }

    @Override
    public void setRooms(List<IRoom2D> rooms) {
        this.rooms = rooms;
    }

    @Override
    public Grid2D getGrid() {
        return this.grid;
    }

    public void setGrid(Grid2D grid) {
        this.grid = grid;
    }

}
