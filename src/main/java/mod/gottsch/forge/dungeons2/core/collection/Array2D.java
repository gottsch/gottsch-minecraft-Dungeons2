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
package mod.gottsch.forge.dungeons2.core.collection;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;

import java.lang.reflect.Array;

/**
 * @author Mark Gottschling on Dec 7, 2023
 *
 */
public class Array2D<T> {
    private T[][] data;

    public Array2D(Class<T> clazz, int width, int height) {
        this.data = (T[][]) Array.newInstance(clazz, width, height);
    }

    private Array2D(T[][] data) {
        this.data = data.clone();
    }

    @Override
    protected Array2D clone() throws CloneNotSupportedException {
        return new Array2D(this.data);
    }

    public Coords2D getSize() {
        return new Coords2D(data.length, data[0].length);
    }

    public void put(int x, int y, T value) {
        data[x][y] = value;
    }

    public T get(int x, int y) {
        return data[x][y];
    }

    public T get(Coords2D coords) {
        return get(coords.getX(), coords.getY());
    }

    public void put(Coords2D coords, T value) {
        put(coords.getX(), coords.getY(), value);
    }

    public int getWidth() {
        return data == null ? 0 : data.length;
    }

    public int getHeight() {
        return data == null ? 0 : data[0] == null ? 0 : data[0].length;
    }
}
