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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.DiamondWallPatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * A run of diamonds (lozenges) along the wall &mdash; the first purely GEOMETRIC wall pattern, as
 * opposed to an architectural one.
 *
 * <h2>How it differs from the four that came before</h2>
 * <p>{@code courses}, {@code pilasters}, {@code end_pilasters} and {@code panels} all describe
 * building elements: a stringcourse, a pier, a recessed field. Each is defined by where the wall's
 * structure puts it. This one is defined by nothing but its own geometry, which is why its fields
 * are {@code size} and {@code spacing} rather than {@code anchor} and {@code projection} &mdash;
 * there is no architectural feature for it to be positioned relative to. It is an inlay.</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code block} &mdash; required. The accent the diamond is drawn in; there is no default
 *       material for an inlay, the same rule every other pattern here follows. Read
 *       {@code project_accent_block_weathering_rule} before picking one: the test is whether the
 *       shape stays legible after the weathering pass, not whether the block appears in it.</li>
 *   <li>{@code size} &mdash; cells from the centre to a tip, so the lozenge spans
 *       {@code 2 * size + 1} in both directions. Defaults to 2, a 5x5 diamond.</li>
 *   <li>{@code spacing} &mdash; cells between diamond centres along the wall. Defaults to 6, which
 *       at the default size leaves a one-cell gap. Below {@code 2 * size + 1} the diamonds overlap
 *       into a continuous lattice, which is a real look and is not clamped away.</li>
 *   <li>{@code filled} &mdash; false (the default) draws the one-cell outline, true fills the whole
 *       lozenge. The outline is the default because a filled diamond at size 2 is 13 cells of solid
 *       accent, which on a 5-row wall reads as a blob rather than as a motif.</li>
 *   <li>{@code properties} &mdash; block state properties for {@code block}.</li>
 * </ul>
 *
 * <h2>It needs height, and a short wall gets NOTHING</h2>
 * <p>A wall's usable height is {@code roomHeight - 2}, so 3 to 8 rows. {@code size} 2 needs 5 of
 * them and {@code size} 3 needs 7. Rather than clip &mdash; a clipped diamond is a triangle, and a
 * wall of triangles is a pattern nobody authored &mdash; the provider draws nothing at all. So a
 * scheme that wants this to be reliable states {@code min_height}, exactly as
 * {@code mud_timber_pillars} does for its posts.</p>
 *
 * <h2>Compose it AFTER a fill</h2>
 * <p>Patterns compose by later-non-null-wins. This marks a few cells and leaves the rest null, so
 * it belongs after {@code gradient} (which fills every cell and would erase it) and either side of
 * {@code courses}, depending on whether a stringcourse should cross the diamonds or be interrupted
 * by them.</p>
 *
 * @author Mark Gottschling on Aug 30, 2026
 */
public record DiamondWallPattern(String block, int size, int spacing, boolean filled,
                                 Map<String, String> properties) implements WallPattern {

    public static final String NAME = "diamond";

    /** A plain outlined lozenge at the default rhythm. */
    public DiamondWallPattern(String block) {
        this(block, DiamondWallPatternProvider.DEFAULT_SIZE,
                DiamondWallPatternProvider.DEFAULT_SPACING, false, Map.of());
    }

    public static final MapCodec<DiamondWallPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID.fieldOf("block").forGetter(DiamondWallPattern::block),
                    // From 1: a size of 0 is a single cell, which is a speck and not a diamond.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "size",
                                    DiamondWallPatternProvider.DEFAULT_SIZE)
                            .forGetter(DiamondWallPattern::size),
                    // From 1, not from 2*size+1: overlapping diamonds are the lattice look, and the
                    // schema has no business forbidding it. 0 would stack every diamond on one
                    // centre, which is the only value that means nothing.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "spacing",
                                    DiamondWallPatternProvider.DEFAULT_SPACING)
                            .forGetter(DiamondWallPattern::spacing),
                    Codecs.strictOptionalFieldOf(Codec.BOOL, "filled", false)
                            .forGetter(DiamondWallPattern::filled),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                                    "properties", Map.of())
                            .forGetter(DiamondWallPattern::properties)
            ).apply(instance, DiamondWallPattern::new)));

    @Override
    public MapCodec<? extends WallPattern> codec() {
        return CODEC;
    }

    @Override
    public ISurfacePatternProvider provider() {
        BlockState state = WallPattern.state(block, properties);
        // Null degrades the whole pattern to plain wall rather than drawing part of it -- the rule
        // every wall pattern follows. Here there is no part to draw anyway: one block is all it has.
        return state == null ? null : new DiamondWallPatternProvider(state, size, spacing, filled);
    }
}
