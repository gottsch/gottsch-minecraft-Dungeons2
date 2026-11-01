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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.DiagonalFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.MaskSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Diagonal stripes {@code width} cells wide: the floor's {@code diagonal}, with {@code block} on the
 * floor's {@code secondary_block} stripes.
 *
 * <p>Only {@code block} is placed; the other cells stay the ceiling's base. {@code inset}
 * confines the pattern to the panel inside a {@code border} at a smaller inset.</p>
 */
public record DiagonalCeilingPattern(String block, int width, boolean flipped, int inset, Map<String, String> properties) implements CeilingPattern {

    public static final String NAME = "diagonal";

    /** See {@link CeilingPattern#withRoles}. */
    @Override
    public CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        if (resolvedBlock.equals(block)) {
            return this;
        }
        return new DiagonalCeilingPattern(resolvedBlock, width, flipped, inset, properties);
    }

    public static final MapCodec<DiagonalCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(DiagonalCeilingPattern::block),
                    // From 1: a band of 0 cells is not a band, and the arithmetic divides by it.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "width",
                                    DiagonalFloorPatternProvider.DEFAULT_WIDTH)
                            .forGetter(DiagonalCeilingPattern::width),
                    Codecs.strictOptionalFieldOf(Codec.BOOL, "flipped", false)
                            .forGetter(DiagonalCeilingPattern::flipped),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset", 0)
                            .forGetter(DiagonalCeilingPattern::inset),
                    CeilingPattern.<DiagonalCeilingPattern>propertiesField(DiagonalCeilingPattern::properties)
            ).apply(instance, DiagonalCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    /** The floor's {@code plan} marks its primary stripes; the ceiling draws the secondary ones. */
    private static boolean[][] invert(boolean[][] grid) {
        for (boolean[] column : grid) {
            for (int v = 0; v < column.length; v++) {
                column[v] = !column[v];
            }
        }
        return grid;
    }

    @Override
    public void addLayers(int projection, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state != null) {
            out.add(new Layer(projection, new MaskSurfacePatternProvider(inset, state,
                    (u, v) -> invert(DiagonalFloorPatternProvider.plan(u, v, width, flipped)))));
        }
    }
}
