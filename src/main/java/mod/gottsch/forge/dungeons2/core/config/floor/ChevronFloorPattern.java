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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.ChevronFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import net.minecraft.world.level.block.Block;

/**
 * A run of chevrons up the room's depth axis, over the {@link FloorConfig}'s own base. Backlog #82.
 *
 * <p>Every {@code spacing} rows a V restarts at the floor's centre column, its arms walking outward
 * at {@code slope} cells per row; {@code filled} turns the outline into solid triangles. The wall
 * {@code diamond} is the closest existing thing, right down to {@code filled}, and for the same
 * reason: this is an inlay, defined by nothing but its own geometry.</p>
 *
 * <p><strong>There is no axis option.</strong> The V's point up the depth axis, always. A floor has
 * no up, so which way a chevron points is arbitrary in a procedural room until something else in it
 * justifies the direction &mdash; the same reasoning {@code gradient} gives for running
 * edge-to-centre and {@code cross} gives for always drawing both bands.</p>
 *
 * @see ChevronFloorPatternProvider
 */
public record ChevronFloorPattern(String block, int spacing, int slope, boolean filled)
        implements FloorPattern {

    public static final String NAME = "chevron";

    /** A one-cell outline at the default rhythm. */
    public ChevronFloorPattern(String block) {
        this(block, ChevronFloorPatternProvider.DEFAULT_SPACING,
                ChevronFloorPatternProvider.DEFAULT_SLOPE, false);
    }

    /** See {@link FloorPattern#withRoles}. */
    @Override
    public FloorPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        if (resolvedBlock.equals(block)) {
            return this;
        }
        return new ChevronFloorPattern(resolvedBlock, spacing, slope, filled);
    }

    public static final MapCodec<ChevronFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(ChevronFloorPattern::block),
                    // From 1: a spacing of 0 has no next V to space against.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "spacing",
                                    ChevronFloorPatternProvider.DEFAULT_SPACING)
                            .forGetter(ChevronFloorPattern::spacing),
                    // From 0, not from 1: a slope of 0 draws the centre line, which is plain but is
                    // a thing an author can mean. Negative is the same V mirrored, i.e. nothing new.
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "slope",
                                    ChevronFloorPatternProvider.DEFAULT_SLOPE)
                            .forGetter(ChevronFloorPattern::slope),
                    Codecs.strictOptionalFieldOf(Codec.BOOL, "filled", false)
                            .forGetter(ChevronFloorPattern::filled)
            ).apply(instance, ChevronFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block accent = FloorPatterns.block(block);
        return FloorPatterns.allResolve(accent)
                ? new ChevronFloorPatternProvider(spacing, slope, filled, accent, config.baseState())
                : PlainFloorPattern.INSTANCE.generator(config);
    }
}
