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
package mod.gottsch.forge.dungeons2.core.config.ceiling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector.Layer;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.MaskSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * The floor's checkerboard on the ceiling: {@code block} takes the cells the floor gives its
 * {@code secondary_block}, so it lines up with a checkerboard floor below.
 *
 * <p>Only {@code block} is placed; the other cells stay the ceiling's base. {@code inset}
 * confines the pattern to the panel inside a {@code border} at a smaller inset.</p>
 */
public record CheckerboardCeilingPattern(String block, int inset, Map<String, String> properties) implements CeilingPattern {

    public static final String NAME = "checkerboard";

    /** See {@link CeilingPattern#withRoles}. */
    @Override
    public CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        if (resolvedBlock.equals(block)) {
            return this;
        }
        return new CheckerboardCeilingPattern(resolvedBlock, inset, properties);
    }

    public static final MapCodec<CheckerboardCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(CheckerboardCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset", 0)
                            .forGetter(CheckerboardCeilingPattern::inset),
                    CeilingPattern.<CheckerboardCeilingPattern>propertiesField(CheckerboardCeilingPattern::properties)
            ).apply(instance, CheckerboardCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    /** The floor checkerboard's secondary cells. */
    private static boolean[][] checker(int uSize, int vSize) {
        boolean[][] grid = new boolean[uSize][vSize];
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                grid[u][v] = (u + v) % 2 != 0;
            }
        }
        return grid;
    }

    @Override
    public void addLayers(int projection, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state != null) {
            out.add(new Layer(projection, new MaskSurfacePatternProvider(inset, state,
                    CheckerboardCeilingPattern::checker)));
        }
    }
}
