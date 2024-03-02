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


/**
 * @author Mark Gottschling on Oct Nov 10, 2023
 */
public class PrimsTile2D {
    private int id;
    private Coords2D coords;
    private Direction2D direction;

    public PrimsTile2D(int x, int y, Direction2D direction) {
        this(new Coords2D(x, y), direction);
    }

    public PrimsTile2D(Coords2D coords, Direction2D direction) {
        this.coords = coords;
        this.direction = direction;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getX() {
        return getCoords().getX();
    }

    public int getY() {
        return getCoords().getY();
    }

    public Coords2D getCoords() {
        return coords;
    }

    public void setCoords(Coords2D coords) {
        this.coords = coords;
    }

    public Direction2D getDirection() {
        return direction;
    }

    public void setDirection(Direction2D direction) {
        this.direction = direction;
    }
}
