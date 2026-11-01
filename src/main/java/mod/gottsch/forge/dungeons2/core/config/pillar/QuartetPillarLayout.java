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
package mod.gottsch.forge.dungeons2.core.config.pillar;

import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.QuartetPillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.GridPillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.IPillarPatternProvider;

/** The {@code quartet} column footprint, at {@code spacing} apart and {@code inset} from the wall. */
public record QuartetPillarLayout(int spacing, int inset) implements PillarLayoutPattern {

    public static final String NAME = "quartet";

    /** The default rhythm, for a callers-with-no-JSON convenience. */
    public QuartetPillarLayout() {
        this(GridPillarPatternProvider.DEFAULT_SPACING, GridPillarPatternProvider.DEFAULT_INSET);
    }

    public static final MapCodec<QuartetPillarLayout> CODEC = PillarLayoutPattern.RHYTHM(
            QuartetPillarLayout::new, QuartetPillarLayout::spacing, QuartetPillarLayout::inset);

    @Override
    public MapCodec<? extends PillarLayoutPattern> codec() {
        return CODEC;
    }

    @Override
    public IPillarPatternProvider provider() {
        return new QuartetPillarPatternProvider(spacing, inset);
    }
}
