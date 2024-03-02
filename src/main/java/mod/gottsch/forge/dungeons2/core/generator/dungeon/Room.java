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

/**
 * @author Mark Gottschling on Oct Nov 22, 2023
 *
 */
public class Room implements IRoom {
    private int id;
    private int width;
    private int depth;
    private int height;

    private ICoords coords;

    // TODO is start
    // TODO is end

    public Room() {}

    @Override
    public int getId() {
        return id;
    }

    @Override
    public IRoom setId(int id) {
        this.id = id;
        return this;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public IRoom setWidth(int width) {
        this.width = width;
        return this;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public IRoom setDepth(int depth) {
        this.depth = depth;
        return this;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public IRoom setHeight(int height) {
        this.height = height;
        return this;
    }

    @Override
    public ICoords getCoords() {
        return coords;
    }

    @Override
    public IRoom setCoords(ICoords coords) {
        this.coords = coords;
        return this;
    }
}
