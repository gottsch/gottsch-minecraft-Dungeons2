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

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.GradientWallPatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Two materials mixed with a vertical bias: {@code bottom_block} dominates the foot of the wall and
 * gives way to {@code top_block} as it rises.
 *
 * <h2>Not a {@code courses} pattern with two bands</h2>
 * <p>{@code courses} draws a band of one material and a band of another, and the line between them
 * is straight and identical in every column &mdash; which reads as deliberate masonry, and is right
 * for a plinth or a stringcourse. This is the opposite reading: no line anywhere, because the
 * boundary falls in a different place in every column. That is what a wall the ground has half
 * swallowed looks like, and it is why the mud stratum wanted its own type rather than another
 * courses list.</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code bottom_block} / {@code top_block} &mdash; both required. Neither has a default,
 *       because a gradient with one material is a plain wall and should be authored as one.</li>
 *   <li>{@code bottom_probability} &mdash; the chance of {@code bottom_block} on the lowest row.
 *       Defaults to 1.0, a solid base.</li>
 *   <li>{@code top_probability} &mdash; the chance of {@code bottom_block} on the HIGHEST row.
 *       Defaults to 0.0. Naming both ends after the same material is what keeps the ramp readable:
 *       a {@code top_probability} of 0.1 means "a tenth of the top row is still the bottom
 *       material", not "a tenth is the top material".</li>
 *   <li>{@code hold_rows} &mdash; rows at the foot held at {@code bottom_probability} before the
 *       ramp starts. 0 gives a plain linear ramp; 2 gives the base course the mud stratum wants.</li>
 *   <li>{@code bottom_properties} / {@code top_properties} &mdash; block state properties, one map
 *       each. Separate rather than shared: the two materials are unrelated blocks and there is no
 *       reason a property meaningful to one would be meaningful to the other.</li>
 * </ul>
 *
 * <h2>List it FIRST</h2>
 * <p>This is the only wall pattern that fills every cell rather than marking a few &mdash; it is the
 * wall's material, not a treatment over one. Patterns compose by later-non-null-wins, so a scheme
 * naming this first gets its pilasters and courses drawn on top; naming it last erases them.</p>
 *
 * @author Mark Gottschling on Aug 29, 2026
 */
public record GradientWallPattern(String bottomBlock, String topBlock, double bottomProbability,
                                  double topProbability, int holdRows,
                                  Map<String, String> bottomProperties,
                                  Map<String, String> topProperties) implements WallPattern {

    public static final String NAME = "gradient";

    public static final MapCodec<GradientWallPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("bottom_block").forGetter(GradientWallPattern::bottomBlock),
                    Codec.STRING.fieldOf("top_block").forGetter(GradientWallPattern::topBlock),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D),
                                    "bottom_probability", 1.0D)
                            .forGetter(GradientWallPattern::bottomProbability),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D),
                                    "top_probability", 0.0D)
                            .forGetter(GradientWallPattern::topProbability),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE),
                                    "hold_rows", 0)
                            .forGetter(GradientWallPattern::holdRows),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "bottom_properties", Map.of())
                            .forGetter(GradientWallPattern::bottomProperties),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "top_properties", Map.of())
                            .forGetter(GradientWallPattern::topProperties)
            ).apply(instance, GradientWallPattern::new)));

    @Override
    public MapCodec<? extends WallPattern> codec() {
        return CODEC;
    }

    @Override
    public ISurfacePatternProvider provider() {
        BlockState bottom = WallPattern.state(bottomBlock, bottomProperties);
        BlockState top = WallPattern.state(topBlock, topProperties);
        // Either one missing degrades the WHOLE pattern to plain wall rather than filling with the
        // survivor -- a wall drawn entirely in one of the two materials looks authored and would
        // never be reported, where a plain wall reads as a plain wall. Same rule as every other
        // pattern here.
        if (bottom == null || top == null) {
            return null;
        }
        return new GradientWallPatternProvider(bottom, top, bottomProbability, topProbability,
                holdRows);
    }
}
