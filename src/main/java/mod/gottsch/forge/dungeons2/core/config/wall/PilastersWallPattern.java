/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.config.wall;

import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.PilastersWallPatternProvider;

/** Pilaster strips on an even rhythm across the wall. See {@link PilasterShape}. */
public record PilastersWallPattern(PilasterShape shape) implements WallPattern {

    public static final String NAME = "pilasters";

    /** xmap, not a nested object: the shape's keys stay flat inside `config`. */
    public static final MapCodec<PilastersWallPattern> CODEC =
            PilasterShape.MAP_CODEC.xmap(PilastersWallPattern::new, PilastersWallPattern::shape);

    @Override
    public MapCodec<? extends WallPattern> codec() {
        return CODEC;
    }

    @Override
    public ISurfacePatternProvider provider() {
        return shape.provider(PilastersWallPatternProvider.Layout.EVEN);
    }
}
