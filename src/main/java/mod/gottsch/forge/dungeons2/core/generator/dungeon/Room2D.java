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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mark Gottschling on Oct Nov 8, 2023
 *
 */
public class Room2D implements IRoom2D {
    private int id;
    private Rectangle2D box;

    private boolean isStart;
    private boolean isEnd;

    // TODO degrees is a temporary value and should belong in Region
    private int degrees = 1;

    private List<Coords2D> doorways;


    public Room2D(Rectangle2D box) {
        this.box = box;
    }
    public Room2D(int id, Rectangle2D box) {
        this(box);
        this.id = id;
    }

    /////// convenience methods /////

    @Override
    public Coords2D getOrigin() {
        return box.getOrigin();
    }

    @Override
    public int getWidth() {
        return box.getWidth();
    }

    @Override
    public void setWidth(int width) {
        box.setWidth(width);
    }

    @Override
    public int getHeight() {
        return box.getHeight();
    }

    @Override
    public void setHeight(int height) {
        box.setHeight(height);
    }

    ///// mutators /////
    @Override
    public int getId() {
        return id;
    }
    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public Rectangle2D getBox() {
        return box;
    }
    @Override
    public void setBox(Rectangle2D box) {
        this.box = box;
    }

    @Override
    public boolean isStart() {
        return isStart;
    }

    @Override
    public void setStart(boolean start) {
        isStart = start;
    }

    @Override
    public boolean isEnd() {
        return isEnd;
    }

    @Override
    public void setEnd(boolean end) {
        isEnd = end;
    }

    @Override
    public int getDegrees() {
        return degrees;
    }

    @Override
    public void setDegrees(int degrees) {
        this.degrees = degrees;
    }

    @Override
    public List<Coords2D> getDoorways() {
        if (doorways == null) {
            doorways = new ArrayList<>();
        }
        return doorways;
    }

    @Override
    public void setDoorways(List<Coords2D> doorways) {
        this.doorways = doorways;
    }
}
