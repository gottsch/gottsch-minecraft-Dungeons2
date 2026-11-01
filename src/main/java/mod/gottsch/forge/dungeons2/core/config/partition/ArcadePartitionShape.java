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
package mod.gottsch.forge.dungeons2.core.config.partition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.partition.StripPartitionShape.Axis;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.ArcadePartitionShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.IPartitionShapeProvider;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * {@code dungeons2:arcade}: a free-standing wall wall to wall, built as a run of corbelled arches.
 *
 * <p>The entry's {@code block} is the wall itself &mdash; the piers and everything above the
 * arches. The shape names the three blocks that are not wall: the foot of each pier, the corbel
 * courses that step the opening in, and the keystone over it. Each falls back to the wall's block
 * when omitted, so an arcade with none of them is a plain stepped arcade in one material.</p>
 *
 * <h2>Two entry fields it does not use, and refuses</h2>
 * <p>{@code gap_block}, because the openings are the way through and there is no door to hang;
 * and a {@code height} below the default, because an arcade always reaches the ceiling &mdash; the
 * arches are a fixed height and the wall above them takes up whatever the room has left. Both are
 * load errors rather than silently ignored keys; see {@code PartitionPatternEntry}.</p>
 *
 * @author Mark Gottschling on 10/30/2026
 */
public record ArcadePartitionShape(Axis axis, int openingWidth, int pierWidth, int openingHeight,
                                   int corbelSteps, Optional<String> baseBlock,
                                   Optional<String> corbelBlock, Optional<String> keystoneBlock)
        implements PartitionShapePattern {

    public static final String NAME = "arcade";

    /** Five: two corbel courses a side still leave one cell for the keystone to close. */
    public static final int DEFAULT_OPENING_WIDTH = 5;
    /** One: a square pier in a one-thick wall. */
    public static final int DEFAULT_PIER_WIDTH = 1;
    /** Three clear rows under the first corbel: a player walks through without ducking. */
    public static final int DEFAULT_OPENING_HEIGHT = 3;
    public static final int DEFAULT_CORBEL_STEPS = 2;

    public ArcadePartitionShape() {
        this(Axis.ANY, DEFAULT_OPENING_WIDTH, DEFAULT_PIER_WIDTH, DEFAULT_OPENING_HEIGHT,
                DEFAULT_CORBEL_STEPS, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static final MapCodec<ArcadePartitionShape> CODEC = Codecs.closedMap(
            // Explicit type witness for flatXmap; see CentrePitShape#CODEC.
            RecordCodecBuilder.<ArcadePartitionShape>mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Axis.CODEC, "axis", Axis.ANY)
                            .forGetter(ArcadePartitionShape::axis),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 15), "opening_width",
                            DEFAULT_OPENING_WIDTH).forGetter(ArcadePartitionShape::openingWidth),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 8), "pier_width",
                            DEFAULT_PIER_WIDTH).forGetter(ArcadePartitionShape::pierWidth),
                    // Two at the least: the two rows a player walks through.
                    Codecs.strictOptionalFieldOf(Codec.intRange(2, 6), "opening_height",
                            DEFAULT_OPENING_HEIGHT).forGetter(ArcadePartitionShape::openingHeight),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, 4), "corbel_steps",
                            DEFAULT_CORBEL_STEPS).forGetter(ArcadePartitionShape::corbelSteps),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "base_block")
                            .forGetter(ArcadePartitionShape::baseBlock),
                    // Written with half=top and facing toward the pier it springs from, so a stair
                    // reads as a corbel; a block without those properties drops them at resolve.
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "corbel_block")
                            .forGetter(ArcadePartitionShape::corbelBlock),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "keystone_block")
                            .forGetter(ArcadePartitionShape::keystoneBlock)
            ).apply(instance, ArcadePartitionShape::new)).flatXmap(ArcadePartitionShape::validate,
                    ArcadePartitionShape::validate));

    private static DataResult<ArcadePartitionShape> validate(ArcadePartitionShape shape) {
        if (shape.openingWidth() < 2 * shape.corbelSteps() + 1) {
            return DataResult.error(() -> "partition 'arcade': opening_width " + shape.openingWidth()
                    + " closes under " + shape.corbelSteps() + " corbel steps a side before the"
                    + " keystone course -- it needs at least " + (2 * shape.corbelSteps() + 1));
        }
        return DataResult.success(shape);
    }

    /** Rows the arches need, floor to keystone: the clear opening, the corbels, the keystone course. */
    public int archRows() {
        return openingHeight + corbelSteps + 1;
    }

    @Override
    public PartitionShapePattern withRoles(UnaryOperator<String> resolver) {
        Optional<String> base = Codecs.resolveRole(baseBlock, resolver);
        Optional<String> corbel = Codecs.resolveRole(corbelBlock, resolver);
        Optional<String> keystone = Codecs.resolveRole(keystoneBlock, resolver);
        if (base.equals(baseBlock) && corbel.equals(corbelBlock) && keystone.equals(keystoneBlock)) {
            return this;
        }
        return new ArcadePartitionShape(axis, openingWidth, pierWidth, openingHeight, corbelSteps,
                base, corbel, keystone);
    }

    @Override
    public MapCodec<? extends PartitionShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPartitionShapeProvider provider() {
        return new ArcadePartitionShapeProvider(this);
    }
}
