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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How many ways into a room a scheme is for: an inclusive range of DOORWAYS, flattened onto the
 * scheme as {@code min_doors}/{@code max_doors}.
 *
 * <h2>A doorway is a RUN, not a cell</h2>
 * <p>The maze stores a 2-wide opening as two adjacent doorway cells, and {@code door_jambs} already
 * frames each run of adjacent cells as one opening. Counting cells would make a dead end with a
 * wide door a "two-door room", which is exactly the case this gate exists to tell apart from a
 * junction. See {@code RoomDoorways#count}.</p>
 *
 * <h2>On the scheme, not on {@link SizeGate}</h2>
 * <p>{@code SizeGate#fits(width, depth, height)} is called from every element slot, and an element
 * slot never knows the room's doors. This is a scheme-level question only, asked once, by
 * {@code RoomSchemeSelector} &mdash; the same reason {@link FloorRange} is its own record.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public record DoorRange(int min, Optional<Integer> max) {

    /** Any number of doors. What a scheme with neither bound authored decodes to. */
    public static final DoorRange ANY = new DoorRange(0, Optional.empty());

    /** Flat in the enclosing object, like {@link FloorRange#MAP_CODEC}. */
    public static final MapCodec<DoorRange> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_doors", 0)
                    .forGetter(DoorRange::min),
            // From 1: every room the planner builds has at least one way in, so "max_doors": 0 could
            // only ever be a mistake that switches the scheme off everywhere.
            Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "max_doors")
                    .forGetter(DoorRange::max)
    ).apply(instance, DoorRange::new));

    /**
     * Whether a room with this many doorways is inside the range. Both bounds inclusive; absent max
     * is unbounded. A negative count means the caller does not know the room's doors, and passes.
     */
    public boolean contains(int doorCount) {
        if (doorCount < 0) {
            return true;
        }
        return doorCount >= min && max.map(bound -> doorCount <= bound).orElse(true);
    }

    /** Rejects an inverted range, naming where it was found. See {@link FloorRange#validate}. */
    public DataResult<DoorRange> validate(String where) {
        if (max.isPresent() && max.get() < min) {
            return DataResult.error(() -> where + ": max_doors " + max.get()
                    + " is below min_doors " + min + ", so it fits no room at all");
        }
        return DataResult.success(this);
    }
}
