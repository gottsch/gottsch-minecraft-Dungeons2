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

import java.util.List;

/**
 * One depth band of the motif's {@code mobSetsByFloorIndex} table: the mob sets a dungeon's spawners
 * draw from from this floor down, until the next band takes over.
 *
 * <h2>Bands are open-ended downward, and that is the design</h2>
 * <p>A band declares only where it <em>starts</em>. Band <em>n</em> runs from its
 * {@link #minFloorIndex} until band <em>n+1</em> begins, and the last band runs forever. The
 * alternative &mdash; a min and a max on each &mdash; can leave a floor covered by nothing, and the
 * consequence of that is not a visible hole but a silently disarmed spawner on every room of that
 * floor. Open-ended bands make the gap <strong>unrepresentable</strong> rather than something a test
 * has to go looking for; the one remaining requirement, that some band covers floor 0, is a single
 * load-time check ({@link #validate}).</p>
 *
 * <h2>floorIndex, not floorY</h2>
 * <p>{@code minFloorIndex} counts floors from the entrance: <strong>0 is the entrance floor</strong>,
 * 1 the one below it, and so on. It is deliberately not a world Y. A dungeon under a mountain has its
 * third floor higher than a ravine dungeon's first, so a Y threshold would make "deep" mean something
 * different per dungeon &mdash; whereas an author writing {@code minFloorIndex: 3} means the fourth
 * floor down, every time. (Note that Stronger Mobs Below's own scaling <em>is</em> keyed on world Y;
 * the two axes coexist deliberately and answer different questions.)</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public record MobSetBand(int minFloorIndex, List<SpawnerConfig.MobSetEntry> mobSets) {

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<MobSetBand> CODEC = Codecs.closed(RecordCodecBuilder.<MobSetBand>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "minFloorIndex", 0)
                    .forGetter(MobSetBand::minFloorIndex),
            SpawnerConfig.MobSetEntry.CODEC.listOf().fieldOf("mobSets").forGetter(MobSetBand::mobSets)
    ).apply(instance, MobSetBand::new))).flatXmap(MobSetBand::validateBand, MobSetBand::validateBand);

    private static DataResult<MobSetBand> validateBand(MobSetBand band) {
        if (band.mobSets.isEmpty()) {
            return DataResult.error(() -> "mob set band at floor " + band.minFloorIndex
                    + ": 'mobSets' is empty, so every spawner on those floors would be an invisible"
                    + " block that spawns nothing");
        }
        return DataResult.success(band);
    }

    /**
     * The band covering {@code floorIndex}, or empty if the table is empty.
     *
     * <p>Reads the table backwards for the deepest band that has started. Linear, over a list an
     * author is realistically going to keep to a handful of entries, and called once per room
     * &mdash; not worth an index.</p>
     */
    public static java.util.Optional<MobSetBand> forFloor(List<MobSetBand> table, int floorIndex) {
        MobSetBand best = null;
        for (MobSetBand band : table) {
            if (band.minFloorIndex <= floorIndex
                    && (best == null || band.minFloorIndex > best.minFloorIndex)) {
                best = band;
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    /**
     * Rejects a table that cannot answer for every floor a dungeon can have.
     *
     * <p>Two faults, and the reasoning for each being an <em>error</em> rather than a repair is the
     * same one that runs through this whole feature: a spawner is invisible, so a floor the table
     * cannot answer for produces a dungeon that looks finished and is quietly empty.</p>
     *
     * <ul>
     *   <li><strong>No band covers floor 0.</strong> Floors below the lowest band are covered by
     *       construction; floors above it are not covered at all. Since the entrance floor is
     *       always index 0, requiring a band there is exactly equivalent to requiring full
     *       coverage &mdash; no sweep needed.</li>
     *   <li><strong>Two bands start on the same floor.</strong> One of them is dead, and which one
     *       depends on list order, which is not something an author should have to reason about.</li>
     * </ul>
     *
     * <p>An <em>empty</em> table is fine and means "this motif's schemes must name their own sets" —
     * see {@code SpawnerConfig}.</p>
     */
    public static DataResult<List<MobSetBand>> validate(List<MobSetBand> table) {
        if (table.isEmpty()) {
            return DataResult.success(table);
        }
        java.util.Set<Integer> starts = new java.util.HashSet<>();
        for (MobSetBand band : table) {
            if (!starts.add(band.minFloorIndex)) {
                return DataResult.error(() -> "mobSetsByFloorIndex: two bands both start at floor "
                        + band.minFloorIndex + ", so one of them can never be reached");
            }
        }
        if (!starts.contains(0)) {
            return DataResult.error(() -> "mobSetsByFloorIndex: no band covers floor 0 (the entrance"
                    + " floor), so its spawners would draw from nothing. Bands run from their"
                    + " minFloorIndex downward, so the shallowest must start at 0. Found: " + starts);
        }
        return DataResult.success(table);
    }
}
