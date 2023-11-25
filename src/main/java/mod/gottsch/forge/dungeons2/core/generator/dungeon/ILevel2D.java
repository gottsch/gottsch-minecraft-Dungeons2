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
public interface ILevel2D {
    int getWidth();

    int getHeight();

    IRoom2D getStartRoom();
    void setStartRoom(IRoom2D startRoom);
    IRoom2D getEndRoom();
    void setEndRoom(IRoom2D endRoom);

    public List<IRoom2D> getRooms();
    public void setRooms(List<IRoom2D> rooms);

    Grid2D getGrid();
    void setGrid(Grid2D grid);
}
