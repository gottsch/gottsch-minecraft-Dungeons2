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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RandomSpeckleFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.SpeckleFloorOverlayProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * {@code primary_block} everywhere, with {@code secondary_block} sprinkled in at {@code probability}
 * per cell &mdash; the rarer, randomized cousin of {@link CheckerboardFloorPattern}.
 *
 * <p>This is what the mud stratum paves with: cobblestone showing packed mud through at 0.12. Its
 * value over the {@code base}/{@code alternate_base} pair is precisely {@code probability} &mdash;
 * that pair is a fixed 45/55 roll, which reads as a checkerboard rather than as wear and cannot
 * express "mostly cobblestone" at all.</p>
 */
public record SpeckleFloorPattern(Optional<String> primaryBlock, String secondaryBlock, double probability)
        implements FloorPattern, CellLocalFloorPattern {

    public static final String NAME = "speckle";

    /** The full-fill form, from before {@code primary_block} could be left out to accent instead. */
    public SpeckleFloorPattern(String primaryBlock, String secondaryBlock, double probability) {
        this(Optional.of(primaryBlock), secondaryBlock, probability);
    }

    /** See {@link FloorPattern#withRoles}. */
    @Override
    public FloorPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        Optional<String> resolvedPrimaryBlock = Codecs.resolveRole(primaryBlock, resolver);
        String resolvedSecondaryBlock = Codecs.resolveRole(secondaryBlock, resolver);
        if (resolvedPrimaryBlock.equals(primaryBlock)
                && resolvedSecondaryBlock.equals(secondaryBlock)) {
            return this;
        }
        return new SpeckleFloorPattern(resolvedPrimaryBlock, resolvedSecondaryBlock, probability);
    }


    public static final MapCodec<SpeckleFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    // OPTIONAL, and absence MEANS something rather than falling back: no
                    // primary_block is overlay mode, where only the accent cells are drawn and
                    // everything else is left to whatever is underneath. That is the whole reason
                    // this pattern can accent a corridor's floor pair or a composite's base
                    // (Mark, 2026-09-09) instead of replacing it.
                    //
                    // Worth flagging against this field's own history: it was made REQUIRED when
                    // the flat record was split up, because back then an absent base degraded
                    // silently to plain floor. This is not that. Absent is now a second authored
                    // mode with a visibly different result, which is exactly the distinction
                    // strictOptionalFieldOf exists to keep -- a malformed value still errors.
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "primary_block")
                            .forGetter(SpeckleFloorPattern::primaryBlock),
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("secondary_block").forGetter(SpeckleFloorPattern::secondaryBlock),
                    // Keeps its own default: it is a pattern-shape knob, not a material, and 0
                    // legitimately means "the accent never appears".
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0, 1.0), "probability",
                                    RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY)
                            .forGetter(SpeckleFloorPattern::probability)
            ).apply(instance, SpeckleFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    /**
     * Speckle is the archetypal cell-local pattern: its answer for a cell reads neither the cell's
     * position nor its neighbours, only one value off the stream. See {@link CellLocalFloorPattern}.
     */
    @Override
    public CellFloor cellFloor() {
        Block accent = FloorPatterns.block(secondaryBlock);
        if (accent == null) {
            return null;
        }
        BlockState accentState = accent.defaultBlockState();
        // No primary: OVERLAY. Null for an unaccented cell leaves the corridor's own
        // floor/alternate_floor roll standing, which is what makes `alternate_floor` still mean
        // something under a speckle.
        if (primaryBlock.isEmpty()) {
            return (x, z, random) -> random.nextFloat() < probability ? accentState : null;
        }
        Block base = FloorPatterns.block(primaryBlock.get());
        if (base == null) {
            return null;
        }
        BlockState baseState = base.defaultBlockState();
        // Exactly one draw per cell in BOTH modes, accented or not -- see CellLocalFloorPattern. A
        // short-circuit here (probability 0, say) would shorten the stream and move every cell
        // after it.
        return (x, z, random) -> random.nextFloat() < probability ? accentState : baseState;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block accent = FloorPatterns.block(secondaryBlock);
        if (accent == null) {
            return PlainFloorPattern.INSTANCE.generator(config);
        }
        if (primaryBlock.isEmpty()) {
            // Overlay mode. The underlay is only consulted when this is a room's WHOLE floor
            // rather than a layer in a composite -- a floor with holes in it would be worse than
            // no accent at all.
            return new SpeckleFloorOverlayProvider(probability, accent,
                    PlainFloorPattern.INSTANCE.generator(config));
        }
        Block base = FloorPatterns.block(primaryBlock.get());
        return base != null
                ? new RandomSpeckleFloorPatternProvider(probability, base, accent)
                : PlainFloorPattern.INSTANCE.generator(config);
    }
}
