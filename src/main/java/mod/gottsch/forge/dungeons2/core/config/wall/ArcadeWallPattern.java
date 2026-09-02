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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.ArcadeWallPatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;

/**
 * A <strong>blind arcade</strong>: a run of arch outlines flat against the wall. Backlog #78.
 *
 * <p>Blind means the opening is not cut. The cells inside each outline are left untouched, so
 * whatever this composes over shows through them &mdash; which is what makes it decoration on a
 * wall rather than a hole in one. A recess is #76, and a different feature.</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code block} &mdash; required. The legs and the crown. Read
 *       {@code project_accent_block_weathering_rule} before picking one: the test is whether the
 *       shape stays legible after weathering, not whether the block appears in the rules.</li>
 *   <li>{@code stair_block} &mdash; the two shoulders. Optional, and its absence squares the arch
 *       off in {@code block} rather than leaving a gap: a partial outline reads as damage, which is
 *       the weathering pass's job to say and not this pattern's.</li>
 *   <li>{@code impost_block} &mdash; the springing course, one cell on each leg directly under the
 *       shoulder. Optional; it is the difference between an arch and an arch that was designed.</li>
 *   <li>{@code width} &mdash; the arch's TOTAL span including both legs, so the opening is
 *       {@code width - 2}. Defaults to 5.</li>
 *   <li>{@code height} &mdash; total rows including the crown. Defaults to 5.</li>
 *   <li>{@code spacing} &mdash; cells of plain wall BETWEEN one arch and the next, not centre to
 *       centre. Defaults to 1. Zero is legal and pairs the legs into a two-cell pier, which is a
 *       real arcade look; a genuinely SHARED pier is not expressible, because each arch draws both
 *       of its own legs.</li>
 *   <li>{@code properties} &mdash; block state properties for {@code block}.</li>
 * </ul>
 *
 * <h2>It needs height, and a short wall gets NOTHING</h2>
 * <p>A wall's usable height is {@code room height - 2}, so 3 to 8 rows and most often at the low
 * end. The default arch needs 5 of them. Rather than clip &mdash; a clipped arcade is a row of legs
 * with no heads, which reads as a fence nobody authored &mdash; the provider draws nothing at all,
 * the same rule {@code diamond} follows. A scheme that wants this reliably states
 * {@code min_height}.</p>
 *
 * <h2>Compose it AFTER a fill</h2>
 * <p>This marks an outline and leaves the rest null, so it belongs after {@code gradient} (which
 * fills every cell and would erase it) and either side of {@code courses}, depending on whether a
 * stringcourse should run across the arches or be interrupted by them.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public record ArcadeWallPattern(String block, Optional<String> stairBlock,
                                Optional<String> impostBlock, int width, int height, int spacing,
                                Map<String, String> properties) implements WallPattern {

    public static final String NAME = "arcade";

    /** A squared arcade in one material, at the default rhythm. */
    public ArcadeWallPattern(String block) {
        this(block, Optional.empty(), Optional.empty(), ArcadeWallPatternProvider.DEFAULT_WIDTH,
                ArcadeWallPatternProvider.DEFAULT_HEIGHT,
                ArcadeWallPatternProvider.DEFAULT_SPACING, Map.of());
    }

    /** The usual authored form: a rib, a stair for the shoulders, and an impost. */
    public ArcadeWallPattern(String block, String stairBlock, String impostBlock) {
        this(block, Optional.of(stairBlock), Optional.of(impostBlock),
                ArcadeWallPatternProvider.DEFAULT_WIDTH, ArcadeWallPatternProvider.DEFAULT_HEIGHT,
                ArcadeWallPatternProvider.DEFAULT_SPACING, Map.of());
    }

    public static final MapCodec<ArcadeWallPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(ArcadeWallPattern::block),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "stair_block")
                            .forGetter(ArcadeWallPattern::stairBlock),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "impost_block")
                            .forGetter(ArcadeWallPattern::impostBlock),
                    // From MIN_WIDTH: two legs and one cell of opening. Narrower is a pilaster, and
                    // there is already a type for that.
                    Codecs.strictOptionalFieldOf(
                                    Codec.intRange(ArcadeWallPatternProvider.MIN_WIDTH, 32), "width",
                                    ArcadeWallPatternProvider.DEFAULT_WIDTH)
                            .forGetter(ArcadeWallPattern::width),
                    // Capped at 8, which is as tall as the taper (#51) lets a wall be -- a bigger
                    // number could never mean more than 8 does, and would silently draw nothing.
                    Codecs.strictOptionalFieldOf(
                                    Codec.intRange(ArcadeWallPatternProvider.MIN_HEIGHT, 8), "height",
                                    ArcadeWallPatternProvider.DEFAULT_HEIGHT)
                            .forGetter(ArcadeWallPattern::height),
                    // From 0: touching arches are a real arcade, not a degenerate one.
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "spacing",
                                    ArcadeWallPatternProvider.DEFAULT_SPACING)
                            .forGetter(ArcadeWallPattern::spacing),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                                    "properties", Map.of())
                            .forGetter(ArcadeWallPattern::properties)
            ).apply(instance, ArcadeWallPattern::new)));

    /** See {@link WallPattern#withRoles}. */
    @Override
    public WallPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        Optional<String> resolvedStair = Codecs.resolveRole(stairBlock, resolver);
        Optional<String> resolvedImpost = Codecs.resolveRole(impostBlock, resolver);
        if (resolvedBlock.equals(block) && resolvedStair.equals(stairBlock)
                && resolvedImpost.equals(impostBlock)) {
            return this;
        }
        return new ArcadeWallPattern(resolvedBlock, resolvedStair, resolvedImpost, width, height,
                spacing, properties);
    }

    @Override
    public MapCodec<? extends WallPattern> codec() {
        return CODEC;
    }

    @Override
    public ISurfacePatternProvider provider() {
        BlockState state = WallPattern.state(block, properties);
        if (state == null) {
            // The rule every wall pattern follows: an unresolvable rib degrades the whole pattern
            // to plain wall rather than drawing part of it.
            return null;
        }
        // The two optional blocks degrade INDEPENDENTLY, and that is the difference between them
        // and `block`: an arcade with no shoulders is a squared arcade and an arcade with no impost
        // is a plainer arcade, both of which are still the pattern the author asked for.
        return new ArcadeWallPatternProvider(state,
                stairBlock.map(id -> WallPattern.state(id, Map.of())).orElse(null),
                impostBlock.map(id -> WallPattern.state(id, Map.of())).orElse(null),
                width, height, spacing);
    }
}
