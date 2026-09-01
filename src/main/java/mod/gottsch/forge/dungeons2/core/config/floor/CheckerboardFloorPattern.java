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

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.CheckerboardFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import net.minecraft.world.level.block.Block;

/** Regular alternation of two blocks, a pure function of {@code (x, z)}. Both blocks required. */
public record CheckerboardFloorPattern(String primaryBlock, String secondaryBlock)
        implements FloorPattern {

    public static final String NAME = "checkerboard";

    public static final MapCodec<CheckerboardFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID.fieldOf("primary_block").forGetter(CheckerboardFloorPattern::primaryBlock),
                    Codecs.BLOCK_ID.fieldOf("secondary_block").forGetter(CheckerboardFloorPattern::secondaryBlock)
            ).apply(instance, CheckerboardFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block primary = FloorPatterns.block(primaryBlock);
        Block secondary = FloorPatterns.block(secondaryBlock);
        return FloorPatterns.allResolve(primary, secondary)
                ? new CheckerboardFloorPatternProvider(primary, secondary)
                : PlainFloorPattern.INSTANCE.generator(config);
    }
}
