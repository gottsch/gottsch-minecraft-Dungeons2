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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The single weighted roll that decides how one room is decorated. Filters a motif's scheme list
 * down to those that fit the room's dimensions, then picks one by weight.
 *
 * <p>This is the <em>only</em> place a decorative treatment is chosen. Individual element
 * selectors ({@code FloorPatternSelector} and, in time, its wall/ceiling counterparts) map an
 * already-chosen entry to a generator and do not roll &mdash; that is what keeps a room's elements
 * coordinated instead of independently random. Kept out of the config records for the same reason
 * {@code FloorPatternSelector} is: those stay pure data.</p>
 *
 * <p><strong>Filtering happens before weights are totalled</strong>, so an ineligible scheme's
 * weight is not merely skipped, it never enters the denominator &mdash; the surviving schemes keep
 * their relative proportions in a small room instead of having probability silently pool into
 * whichever one happens to be last. A scheme list with no eligible member degrades to
 * {@link RoomScheme#PLAIN}, the same graceful degradation an absent/empty pool always has
 * elsewhere in this codebase.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public final class RoomSchemeSelector {

    private RoomSchemeSelector() {}

    /**
     * Rolls one scheme for a room of the given dimensions. {@code height} is the full room height
     * (floor block through ceiling block), matching {@code RoomData#getHeight}.
     *
     * <p>Consumes exactly one value from {@code random} whenever any scheme is eligible &mdash;
     * unconditionally, even for a one-element list, so that the number of values drawn never
     * depends on the room. (The <em>argument</em> to that draw is the eligible total weight, so
     * gating a scheme out does still shift the downstream stream; that is unavoidable and is why
     * adding a {@code minHeight} to a shipped motif changes existing seeds.) Callers rely on this
     * being called once per room build to stay deterministic across the repeated
     * {@code postProcess} calls a piece gets per overlapping chunk.</p>
     */
    public static RoomScheme select(List<RoomScheme> schemes, int width, int depth, int height,
                                    RandomSource random) {
        List<RoomScheme> eligible = new ArrayList<>();
        for (RoomScheme scheme : schemes) {
            if (scheme.fits(width, depth, height)) {
                eligible.add(scheme);
            }
        }
        if (eligible.isEmpty()) {
            return RoomScheme.PLAIN;
        }
        int totalWeight = eligible.stream().mapToInt(RoomScheme::weight).sum();
        if (totalWeight <= 0) {
            return RoomScheme.PLAIN;
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (RoomScheme scheme : eligible) {
            cumulative += scheme.weight();
            if (roll < cumulative) {
                return scheme;
            }
        }
        return RoomScheme.PLAIN; // unreachable: roll < totalWeight == cumulative sum
    }
}
