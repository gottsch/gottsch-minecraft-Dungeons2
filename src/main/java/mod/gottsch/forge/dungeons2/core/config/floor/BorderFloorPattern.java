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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorBorderPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import net.minecraft.world.level.block.Block;

/**
 * A ring inset from the room edge, drawn in a corner block and a left/right pair of edge blocks;
 * the interior is filled from the {@link FloorConfig}'s own base. Also usable as a composite
 * overlay.
 *
 * <p>All three blocks are required. Set {@code edge_left_block} and {@code edge_right_block} to the
 * same id for an edge with no left/right texture variant.</p>
 */
public record BorderFloorPattern(int inset, String cornerBlock, String edgeLeftBlock,
                                 String edgeRightBlock) implements FloorPattern {

    public static final String NAME = "border";

    public static final MapCodec<BorderFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                                    FloorBorderPatternProvider.DEFAULT_INSET)
                            .forGetter(BorderFloorPattern::inset),
                    Codecs.BLOCK_ID.fieldOf("corner_block").forGetter(BorderFloorPattern::cornerBlock),
                    Codecs.BLOCK_ID.fieldOf("edge_left_block").forGetter(BorderFloorPattern::edgeLeftBlock),
                    Codecs.BLOCK_ID.fieldOf("edge_right_block").forGetter(BorderFloorPattern::edgeRightBlock)
            ).apply(instance, BorderFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block corner = FloorPatterns.block(cornerBlock);
        Block left = FloorPatterns.block(edgeLeftBlock);
        Block right = FloorPatterns.block(edgeRightBlock);
        return FloorPatterns.allResolve(corner, left, right)
                ? new FloorBorderPatternProvider(inset, corner, left, right, config.baseState())
                : PlainFloorPattern.INSTANCE.generator(config);
    }
}
