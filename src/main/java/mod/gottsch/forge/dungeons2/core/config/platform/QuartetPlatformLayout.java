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
package mod.gottsch.forge.dungeons2.core.config.platform;

import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.*;

/** Four platforms marking a square at the room's centre, sized off {@code size}. */
public record QuartetPlatformLayout(int inset) implements PlatformLayoutPattern {

    public static final String NAME = "quartet";

    public QuartetPlatformLayout() {
        this(PlatformLayoutPattern.DEFAULT_INSET);
    }

    public static final MapCodec<QuartetPlatformLayout> CODEC =
            PlatformLayoutPattern.INSET(QuartetPlatformLayout::new, QuartetPlatformLayout::inset);

    @Override
    public MapCodec<? extends PlatformLayoutPattern> codec() {
        return CODEC;
    }

    @Override
    public IPillarPatternProvider provider(int size) {
        return new QuartetPillarPatternProvider(Math.max(2, size + 1), inset);
    }
}
