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
 * A class that represents a connection between two regions.
 * @author Mark Gottschling on Oct Nov 9, 2023
 *
 */
public class Connector2D {
    private Coords2D coords;
    private Region2D region1;
    private Region2D region2;

    public Connector2D(int x, int y, Region2D region1, Region2D region2) {
        this(new Coords2D(x, y), region1, region2);
    }

    public Connector2D(Coords2D coords, Region2D region1, Region2D region2) {
        this.coords = coords;
        this.region1 = region1;
        this.region2 = region2;
    }

    public Coords2D getCoords() {
        return coords;
    }

    public void setCoords(Coords2D coords) {
        this.coords = coords;
    }

    public Region2D getRegion1() {
        return region1;
    }

    public void setRegion1(Region2D region1) {
        this.region1 = region1;
    }

    public Region2D getRegion2() {
        return region2;
    }

    public void setRegion2(Region2D region2) {
        this.region2 = region2;
    }
}
