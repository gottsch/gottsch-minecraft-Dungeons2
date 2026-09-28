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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the tombs hold at a given depth (#104) &mdash; the {@link ChestLootBand} shape, carrying a
 * whole {@link TombContents} instead of a loot list.
 *
 * <p>The motif owns it for the reason it owns the chest and mob bands: a crypt scheme is authored
 * once and its dead get worse the deeper it is rolled. A scheme's own {@code tombs} slot overrides
 * it key by key; see {@link TombContents#over}.</p>
 *
 * <p>Open-ended downward, one band per start, and floor 0 must be covered &mdash;
 * {@link MobSetBand#validate}'s rules, for its reasons: an uncovered floor is then unrepresentable
 * rather than something a sweep has to find.</p>
 *
 * @author Mark Gottschling on Sep 26, 2026
 */
public record TombBand(int minFloorIndex, TombContents contents) {

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<TombBand> CODEC = Codecs.closed(RecordCodecBuilder.<TombBand>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_floor_index", 0)
                    .forGetter(TombBand::minFloorIndex),
            TombContents.MAP_CODEC.forGetter(TombBand::contents)
    ).apply(instance, TombBand::new))).flatXmap(TombBand::validateBand, TombBand::validateBand);

    /**
     * A band whose tombs can never release anything is a load error. It reads as "these floors have
     * tombs" and produces tombs that are always empty &mdash; or, since neither route places an inert
     * tomb, produces no tombs at all, which is the same silence from the other side.
     */
    private static DataResult<TombBand> validateBand(TombBand band) {
        if (!band.contents.releasesAnything()) {
            return DataResult.error(() -> "tomb band at floor " + band.minFloorIndex
                    + ": names no loot_tables or guardians with a non-zero weight, so no tomb on"
                    + " those floors could ever hold anything");
        }
        return DataResult.success(band);
    }

    /** The table-level rules; see the class note. */
    public static DataResult<List<TombBand>> validate(List<TombBand> table) {
        if (table.isEmpty()) {
            return DataResult.success(table);
        }
        Set<Integer> starts = new HashSet<>();
        for (TombBand band : table) {
            if (!starts.add(band.minFloorIndex)) {
                return DataResult.error(() -> "tomb_contents_by_floor_index: two bands both start at"
                        + " floor " + band.minFloorIndex + ", so one of them can never be reached");
            }
        }
        if (!starts.contains(0)) {
            return DataResult.error(() -> "tomb_contents_by_floor_index: no band covers floor 0 (the"
                    + " entrance floor). Bands run from their min_floor_index downward, so the"
                    + " shallowest must start at 0. Found: " + starts);
        }
        return DataResult.success(table);
    }

    /** The band covering {@code floorIndex}; mirrors {@link ChestLootBand#forFloor}. */
    public static Optional<TombBand> forFloor(List<TombBand> table, int floorIndex) {
        TombBand best = null;
        for (TombBand band : table) {
            if (band.minFloorIndex <= floorIndex
                    && (best == null || band.minFloorIndex > best.minFloorIndex)) {
                best = band;
            }
        }
        return Optional.ofNullable(best);
    }
}
