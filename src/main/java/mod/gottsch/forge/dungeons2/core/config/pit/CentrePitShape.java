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
package mod.gottsch.forge.dungeons2.core.config.pit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.CentrePitShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.IPitShapeProvider;

import java.util.Optional;

/**
 * A square sunken court of {@code size} cells a side, centred, terraced one block per ring inward.
 *
 * <p>{@code depth} is a MAXIMUM in two directions. It is clamped to the floor's {@code sinkOffset}
 * as the generator writes, and it is bounded by the footprint too &mdash; the court steps down one
 * block per ring, so a 3x3 reaches two and stops however deep it is authored. 5x5 is the smallest
 * that descends three.</p>
 */
public record CentrePitShape(int size, int depth, Optional<String> rimBlock,
                             SurfaceOrient rimOrient) implements PitShapePattern {

    public static final String NAME = "centre";

    /** Three: the smallest square that has a middle and an edge on every side. */
    public static final int DEFAULT_SIZE = 3;

    /** Two: one step down and one along, the shallowest thing that reads as a court not a kerb. */
    public static final int DEFAULT_DEPTH = 2;

    /** The default rim: a vanilla stair's solid half away from the pit, low edge toward it. */
    public static final SurfaceOrient DEFAULT_RIM_ORIENT = SurfaceOrient.OUTWARD;

    public CentrePitShape() {
        this(DEFAULT_SIZE, DEFAULT_DEPTH, Optional.empty(), DEFAULT_RIM_ORIENT);
    }

    /** Un-rimmed, for a test or a court that wants a plain kerb. */
    public CentrePitShape(int size, int depth) {
        this(size, depth, Optional.empty(), DEFAULT_RIM_ORIENT);
    }

    public static final MapCodec<CentrePitShape> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "size",
                            DEFAULT_SIZE).forGetter(CentrePitShape::size),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 24), "depth", DEFAULT_DEPTH)
                            .forGetter(CentrePitShape::depth),
                    // A ring of stairs on the floor cells just OUTSIDE the court. Omit for a plain
                    // kerb; see PitPlans#stairRim for what it buys and which way it faces.
                    Codecs.strictOptionalFieldOf(Codec.STRING, "rimBlock")
                            .forGetter(CentrePitShape::rimBlock),
                    Codecs.strictOptionalFieldOf(SurfaceOrient.CODEC, "rimOrient",
                            DEFAULT_RIM_ORIENT).forGetter(CentrePitShape::rimOrient)
            ).apply(instance, CentrePitShape::new)));

    @Override
    public MapCodec<? extends PitShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPitShapeProvider provider() {
        return new CentrePitShapeProvider(size, depth, rimBlock.orElse(null), rimOrient);
    }
}
