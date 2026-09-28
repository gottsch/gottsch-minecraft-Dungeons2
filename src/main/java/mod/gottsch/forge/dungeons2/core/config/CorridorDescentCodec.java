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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.data.CorridorDescent;

/**
 * The datapack form of a corridor style's {@code descent} block (#108). The record itself is a
 * pure POJO in {@code core.data} so the planner can hold it; its codec lives on this side of the
 * fence.
 *
 * <pre>
 * "descent": { "chance": 0.35, "min_depth": 2, "max_depth": 4, "run": 3, "landing": 2 }
 * </pre>
 *
 * <p>Depths are not checked against {@code sink_offset} here &mdash; that lives in a different file,
 * and the planner clamps to it anyway. A descent authored deeper than the band simply bottoms out at
 * the band, exactly as a pit does.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class CorridorDescentCodec {

    private CorridorDescentCodec() {}

    public static final double DEFAULT_CHANCE = 1.0;
    public static final int DEFAULT_MIN_DEPTH = 1;
    public static final int DEFAULT_RUN = 3;
    public static final int DEFAULT_LANDING = 2;
    /** The largest {@code sink_offset} a config may declare, so nothing deeper could ever be used. */
    public static final int MAX_DEPTH = 24;

    public static final Codec<CorridorDescent> CODEC = RecordCodecBuilder.<CorridorDescent>create(instance ->
            instance.group(
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0, 1.0), "chance", DEFAULT_CHANCE)
                            .forGetter(CorridorDescent::chance),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, MAX_DEPTH), "min_depth", DEFAULT_MIN_DEPTH)
                            .forGetter(CorridorDescent::minDepth),
                    Codec.intRange(1, MAX_DEPTH).fieldOf("max_depth").forGetter(CorridorDescent::maxDepth),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 16), "run", DEFAULT_RUN)
                            .forGetter(CorridorDescent::run),
                    // At least 1: the cell diagonal to a door has to be level, or the door column's
                    // side is open to a sunk cell below its sill.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 8), "landing", DEFAULT_LANDING)
                            .forGetter(CorridorDescent::landing)
            ).apply(instance, CorridorDescent::new)).flatXmap(CorridorDescentCodec::validate,
            CorridorDescentCodec::validate);

    private static DataResult<CorridorDescent> validate(CorridorDescent descent) {
        if (descent.minDepth() > descent.maxDepth()) {
            return DataResult.error(() -> "corridor descent: min_depth " + descent.minDepth()
                    + " is above max_depth " + descent.maxDepth());
        }
        return DataResult.success(descent);
    }
}
