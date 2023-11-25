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
public interface IRoom2D {

    /////// convenience methods /////
    Coords2D getOrigin();

    int getWidth();

    void setWidth(int width);

    int getHeight();
    void setHeight(int height);

    ///// mutators /////
    int getId();

    void setId(int id);

    ///// mutators /////
    Rectangle2D getBox();

    void setBox(Rectangle2D box);

    boolean isStart();

    void setStart(boolean start);

    boolean isEnd();

    void setEnd(boolean end);

    int getDegrees();

    void setDegrees(int degrees);

    List<Coords2D> getDoorways();
    void setDoorways(List<Coords2D> doorways);
}
