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
 * How deep into a dungeon something is allowed: an inclusive range of floor indices, with
 * <strong>0 at the entrance</strong> and counting downward.
 *
 * <h2>Why a record for two fields</h2>
 * <p>Partly the same reason {@link SizeGate} is one &mdash; a bounds pair with its own validation
 * rule belongs together, and a {@link MapCodec} keeps the fields <strong>flat</strong> in the JSON
 * so an author writes {@code "min_floor_index": 3} directly on the scheme rather than nested under
 * some {@code "floors": {...}} wrapper.</p>
 *
 * <p>Partly a hard constraint: DFU's {@code RecordCodecBuilder.group} tops out at <strong>16</strong>
 * arguments and {@link RoomScheme} was already at 15. Two more loose fields would not compile.
 * <strong>The scheme record is now exactly at that ceiling</strong>, so the next scheme-level field
 * has to fold the four size bounds into a nested record too &mdash; {@code SizeGate} is already
 * exactly the right shape for them and is used for element slots today.</p>
 *
 * <h2>Not a world Y</h2>
 * <p>Floor <em>index</em>, deliberately. A dungeon under a mountain has its third floor at a higher
 * Y than a ravine dungeon's first, so a Y threshold would make "deep" mean something different per
 * dungeon, while {@code minFloorIndex: 3} means the fourth floor down every time. (Stronger Mobs
 * Below's scaling <em>is</em> keyed on world Y; the two axes answer different questions and coexist
 * on purpose.)</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public record FloorRange(int min, Optional<Integer> max) {

    /** Every floor. What a scheme with neither bound authored decodes to. */
    public static final FloorRange ANY = new FloorRange(0, Optional.empty());

    /**
     * Flat in the enclosing object, like {@link SizeGate#MAP_CODEC}.
     *
     * <p>{@code max_floor_index} accepts <strong>0</strong>, unlike {@code max_height}/{@code max_size}
     * which start at 1: floor 0 is the entrance floor, so "only on the entrance floor" is a real
     * thing to author. For a height or a size, 0 could only ever be a mistake.</p>
     */
    public static final MapCodec<FloorRange> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_floor_index", 0)
                    .forGetter(FloorRange::min),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "max_floor_index")
                    .forGetter(FloorRange::max)
    ).apply(instance, FloorRange::new));

    /** Whether this floor is inside the range. Both bounds inclusive; absent max is unbounded. */
    public boolean contains(int floorIndex) {
        return floorIndex >= min && max.map(bound -> floorIndex <= bound).orElse(true);
    }

    /** Whether this range constrains anything at all. */
    public boolean isUnbounded() {
        return min == 0 && max.isEmpty();
    }

    /**
     * Rejects an inverted range, naming where it was found.
     *
     * <p>An error rather than a clamp, for the reason {@link RoomScheme}'s size bounds are: a scheme
     * eligible for no floor at all is indistinguishable at generation time from one that simply
     * never won its roll, and clamping would invent a range the author never asked for.</p>
     */
    public DataResult<FloorRange> validate(String where) {
        if (max.isPresent() && max.get() < min) {
            return DataResult.error(() -> where + ": max_floor_index " + max.get()
                    + " is below min_floor_index " + min + ", so it fits no floor at all");
        }
        return DataResult.success(this);
    }
}
