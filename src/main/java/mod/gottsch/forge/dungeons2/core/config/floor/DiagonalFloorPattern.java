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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.DiagonalFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import net.minecraft.world.level.block.Block;

/**
 * Alternating bands at 45 degrees &mdash; {@link CheckerboardFloorPattern}'s diagonal partner, and
 * a pure function of {@code (x, z)} like it. Backlog #82.
 *
 * <p>{@code width} is the band's thickness measured across the diagonal, so 1 is a pinstripe and 3
 * is a ribbon; {@code flipped} runs the bands the other way. Both blocks are required, as on the
 * checkerboard: a two-material pattern with one material is not a degraded version of itself, it is
 * a plain floor, and the closed schema says so at load rather than at draw time.</p>
 *
 * <p>Like the checkerboard it <strong>fills every cell</strong>, so inside a {@code composite} it is
 * a base layer and goes first. Listed anywhere else it is silently dropped: a composite takes its
 * base from its first entry and every later entry has to be an {@code IFloorOverlayGenerator}, which
 * a fill deliberately is not.</p>
 *
 * @see DiagonalFloorPatternProvider
 */
public record DiagonalFloorPattern(String primaryBlock, String secondaryBlock, int width,
                                   boolean flipped) implements FloorPattern {

    public static final String NAME = "diagonal";

    /** Bands at the default width, running from the room's near-left corner. */
    public DiagonalFloorPattern(String primaryBlock, String secondaryBlock) {
        this(primaryBlock, secondaryBlock, DiagonalFloorPatternProvider.DEFAULT_WIDTH, false);
    }

    /** See {@link FloorPattern#withRoles}. */
    @Override
    public FloorPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedPrimaryBlock = Codecs.resolveRole(primaryBlock, resolver);
        String resolvedSecondaryBlock = Codecs.resolveRole(secondaryBlock, resolver);
        if (resolvedPrimaryBlock.equals(primaryBlock)
                && resolvedSecondaryBlock.equals(secondaryBlock)) {
            return this;
        }
        return new DiagonalFloorPattern(resolvedPrimaryBlock, resolvedSecondaryBlock, width, flipped);
    }

    public static final MapCodec<DiagonalFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("primary_block")
                            .forGetter(DiagonalFloorPattern::primaryBlock),
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("secondary_block")
                            .forGetter(DiagonalFloorPattern::secondaryBlock),
                    // From 1: a band of 0 cells is not a band, and the arithmetic divides by it.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "width",
                                    DiagonalFloorPatternProvider.DEFAULT_WIDTH)
                            .forGetter(DiagonalFloorPattern::width),
                    Codecs.strictOptionalFieldOf(Codec.BOOL, "flipped", false)
                            .forGetter(DiagonalFloorPattern::flipped)
            ).apply(instance, DiagonalFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block primary = FloorPatterns.block(primaryBlock);
        Block secondary = FloorPatterns.block(secondaryBlock);
        return FloorPatterns.allResolve(primary, secondary)
                ? new DiagonalFloorPatternProvider(primary, secondary, width, flipped)
                : PlainFloorPattern.INSTANCE.generator(config);
    }
}
