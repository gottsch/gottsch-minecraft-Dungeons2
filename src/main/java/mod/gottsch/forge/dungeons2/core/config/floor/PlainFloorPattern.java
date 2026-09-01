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
package mod.gottsch.forge.dungeons2.core.config.floor;

import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.BasicFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;

/**
 * The undecorated floor: the motif or stratum's own {@code base}/{@code alternate_base} roll.
 *
 * <p>This is what the old {@code "empty"} type meant, and also what every unrecognized type
 * silently became. Only the first of those survives &mdash; an unregistered type is now a load
 * error (see {@link FloorPatternRegistry}), so naming this pattern is the one way to ask for a
 * plain floor, and asking for it is now distinguishable from getting it by accident.</p>
 */
public record PlainFloorPattern() implements FloorPattern {

    public static final String NAME = "plain";

    public static final PlainFloorPattern INSTANCE = new PlainFloorPattern();

    /** No fields, so the whole {@code config} object may be omitted by the enclosing entry. */
    public static final MapCodec<PlainFloorPattern> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        return new BasicFloorGenerator().withFloorConfig(config);
    }
}
