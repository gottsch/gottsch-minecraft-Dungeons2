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

import mod.gottsch.forge.dungeons2.core.collection.Array2D;

/**
 * Convenience class.
 * @author Mark Gottschling on Dec 7, 2023
 *
 */
public class BooleanGrid extends Array2D<Boolean> {

    public BooleanGrid(int width, int height) {
        super(Boolean.class, width, height);

        // init
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                put(x, y, false);
            }
        }
    }
}
