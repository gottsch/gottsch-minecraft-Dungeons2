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


import java.util.ArrayList;
import java.util.List;

/**
 * @author Mark Gottschling on Oct Nov 9, 2023
 *
 */
public class Region2D {
    private Integer id;
    private Rectangle2D box;
    private RegionType type;
    private boolean merged;

    private List<Connector2D> connectors;

    public Region2D() {}

    public Region2D(Integer id) {
        this.id = id;
//        tiles = new ArrayList<>();
        connectors = new ArrayList<>();
    }

    public Region2D(Integer id, Rectangle2D box) {
        this(id);
        this.box = box;
    }

//    public void addTile(Tile tile) {
//        tiles.add(tile);
//    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public List<Connector2D> getConnectors() {
        return connectors;
    }

    public void setConnectors(List<Connector2D> connectors) {
        this.connectors = connectors;
    }

    public RegionType getType() {
        return type;
    }

    public void setType(RegionType type) {
        this.type = type;
    }

    public Rectangle2D getBox() {
        return box;
    }

    public void setBox(Rectangle2D box) {
        this.box = box;
    }
}
