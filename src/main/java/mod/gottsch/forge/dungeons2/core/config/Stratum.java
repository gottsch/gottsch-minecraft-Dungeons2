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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One depth band of a motif's {@code strataByFloorIndex} table: the element sections the dungeon's
 * <strong>shell</strong> is built from, from this floor down, until the next band takes over.
 *
 * <p>Backlog #45. The dungeon should read as older architecture the deeper you go &mdash; the upper
 * floors cruder and recently patched, the lower ones the original grand construction. Room schemes
 * could already do that for a room's <em>dressing</em>, because a scheme names explicit block ids in
 * its patterns. What they could not touch is the surface underneath: {@code wall}, {@code ceiling},
 * {@code door}, {@code corridor} and {@code floor} are what {@code BasicWallGenerator} and its
 * siblings draw <em>before</em> any scheme overlays it, and corridors have no schemes at all.
 * Ancient dressing over cobblestone walls in cobblestone corridors is worse than not doing this, so
 * the element sections are the real subject.
 *
 * <h2>An overlay, not a second config</h2>
 * <p>A section omitted from a band <strong>falls through to the motif's own</strong>. A band is
 * therefore a handful of lines rather than a second copy of a 670-line {@code base.json}, which is
 * the whole argument for an overlay: the alternative makes every band an invitation to drift from
 * the base on the sections it did not mean to change. {@link MotifConfig#forFloor} is where the
 * fall-through happens.
 *
 * <h2>Everything true of {@link MobSetBand} is true here</h2>
 * <p>Same shape, deliberately &mdash; bands are <strong>open-ended downward</strong>, each running
 * until the next starts and the deepest running forever, so a floor covered by nothing is
 * <em>unrepresentable</em> rather than something a sweep must hunt for. And the axis is
 * {@code floorIndex} (0 at the entrance, counting down), never a world Y: a dungeon under a mountain
 * has its third floor higher than a ravine dungeon's first, so a Y threshold would make "deep" mean
 * something different per dungeon. Read {@link MobSetBand} for the full reasoning; it is not
 * repeated here.
 *
 * <h2>A stratum is not a second motif</h2>
 * <p>The shortcut is tempting enough to write down why not. Pools, weathering lists and motif
 * configs are all motif-keyed, so {@code classic_shallow} / {@code classic_deep} would work almost
 * for free &mdash; and would then multiply against every real motif ({@code catacombs_deep},
 * {@code deepslate_shallow}). Motif is the <em>theme</em> selector; strata must compose with it,
 * not enumerate against it.
 *
 * <h2>Why corridor GEOMETRY may not be banded</h2>
 * <p>A stratum may repaint a corridor and may not reshape one. {@code height}, {@code profile},
 * {@code narrowHeight} and {@code styles} are consumed at <strong>plan</strong> time &mdash;
 * {@code DungeonStructure} hands {@code planner.withCorridorStyles} a single list for the whole
 * dungeon, resolved from the unbanded motif, before any floor exists to have a stratum. A band
 * declaring its own would therefore be <em>silently ignored</em>, and if it were honoured it would
 * change the layout and re-roll every existing seed. {@link #reshapedCorridorFields} names them, and
 * {@code MotifConfigFragment#repaintOnly} drops such an override and says so at ERROR &mdash; the
 * only outcome that is neither silent nor destructive.
 *
 * @author Mark Gottschling on Aug 23, 2026
 */
public record Stratum(int minFloorIndex, Optional<String> name, Optional<WallConfig> wall,
                      Optional<CeilingConfig> ceiling, Optional<DoorConfig> door,
                      Optional<CorridorConfig> corridor, Optional<FloorConfig> floor) {

    /**
     * What a band's {@link #name} is allowed to be: a single path segment, because it becomes one
     * &mdash; {@code rooms/<motif>/<stratum>/normal}. A name outside this could never name a pool
     * that exists, so the resolver would quietly fall back to the motif's own tier and the author
     * would see a dungeon with no per-stratum prefabs and no reason given.
     */
    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[a-z0-9_.-]+");

    // Codecs.closed -- see RoomScheme.CODEC. Every section is optional because omitting one is how a
    // band says "the motif's answer is still right at this depth"; that is the overlay, and it is
    // why this is not simply a MotifConfig per band.
    public static final Codec<Stratum> CODEC = Codecs.closed(RecordCodecBuilder.<Stratum>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "minFloorIndex", 0)
                    .forGetter(Stratum::minFloorIndex),
            // Optional because a band that only repaints needs no pools of its own. Naming one is
            // what opts this depth into rooms/<motif>/<name>/ -- see MotifConfig#stratumNameFor.
            Codecs.strictOptionalFieldOf(Codec.STRING, "name").forGetter(Stratum::name),
            Codecs.strictOptionalFieldOf(WallConfig.CODEC, "wall").forGetter(Stratum::wall),
            Codecs.strictOptionalFieldOf(CeilingConfig.CODEC, "ceiling").forGetter(Stratum::ceiling),
            Codecs.strictOptionalFieldOf(DoorConfig.CODEC, "door").forGetter(Stratum::door),
            Codecs.strictOptionalFieldOf(CorridorConfig.CODEC, "corridor").forGetter(Stratum::corridor),
            Codecs.strictOptionalFieldOf(FloorConfig.CODEC, "floor").forGetter(Stratum::floor)
    ).apply(instance, Stratum::new))).flatXmap(Stratum::validateBand, Stratum::validateBand);

    /**
     * Rejects a band that changes nothing.
     *
     * <p>An error rather than a no-op because there is no reading of a band with no sections that an
     * author meant: either they intended sections and lost them, or the entry is a leftover. Both
     * are worth a load failure, and neither shows up in game &mdash; the dungeon would simply look
     * like it has no strata.
     */
    private static DataResult<Stratum> validateBand(Stratum stratum) {
        if (stratum.declaresNothing()) {
            return DataResult.error(() -> "stratum at floor " + stratum.minFloorIndex
                    + ": declares no element sections, so it would change nothing. Give it at least"
                    + " one of wall/ceiling/door/corridor/floor, or remove the band");
        }
        if (stratum.name.isPresent() && !NAME.matcher(stratum.name.get()).matches()) {
            return DataResult.error(() -> "stratum at floor " + stratum.minFloorIndex
                    + ": name '" + stratum.name.get() + "' is not a usable path segment. It becomes"
                    + " one (rooms/<motif>/" + stratum.name.get() + "/normal), so a name outside"
                    + " [a-z0-9_.-] could only ever name a pool that does not exist -- and the"
                    + " resolver would silently fall back to the motif's own rooms");
        }
        return DataResult.success(stratum);
    }

    /**
     * True when every <em>section</em> fell through, i.e. the band is indistinguishable from absent.
     *
     * <p>{@link #name} deliberately does not count. A band that names a stratum but declares no
     * sections would change the room pools while leaving the shell alone &mdash; which is a coherent
     * thing to want, but not one this design supports, because the name is only reachable from a
     * band and a band with nothing in it is far more likely to be an authoring slip.
     */
    public boolean declaresNothing() {
        return wall.isEmpty() && ceiling.isEmpty() && door.isEmpty() && corridor.isEmpty()
                && floor.isEmpty();
    }

    /**
     * The band covering {@code floorIndex}, or empty when the table is empty.
     *
     * <p>The deepest band that has started. Linear over a list an author keeps to a handful of
     * entries; {@link MobSetBand#forFloor} makes the same call for the same reason.
     *
     * @param floorIndex 0 at the entrance, counting downward
     */
    public static Optional<Stratum> forFloor(List<Stratum> table, int floorIndex) {
        Stratum best = null;
        for (Stratum stratum : table) {
            if (stratum.minFloorIndex <= floorIndex
                    && (best == null || stratum.minFloorIndex > best.minFloorIndex)) {
                best = stratum;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Rejects a table that cannot answer for every floor a dungeon can have.
     *
     * <p>An <em>empty</em> table is fine and is what every motif ships today: it means "this motif
     * looks the same all the way down", and {@link MotifConfig#forFloor} then returns the motif
     * unchanged.
     *
     * <ul>
     *   <li><strong>No band covers floor 0.</strong> Floors below the shallowest are covered by
     *       construction; floors above it are covered by nothing. Since the entrance floor is
     *       always index 0, requiring a band there is exactly equivalent to requiring full
     *       coverage, with no sweep.</li>
     *   <li><strong>Two bands start on the same floor.</strong> One is dead, and which one depends
     *       on file order &mdash; not something an author should have to reason about.</li>
     * </ul>
     *
     * <p>The corridor-reshaping check is <strong>not</strong> here and cannot be: it needs the
     * motif's own corridor section to compare against, and a fragment's table is validated as it
     * decodes, before the fragments have been folded. {@code MotifConfigFragment#repaintOnly} runs
     * {@link #reshapedCorridorFields} once the base section is known.
     */
    public static DataResult<List<Stratum>> validate(List<Stratum> table) {
        if (table.isEmpty()) {
            return DataResult.success(table);
        }
        Set<Integer> starts = new HashSet<>();
        for (Stratum stratum : table) {
            if (!starts.add(stratum.minFloorIndex)) {
                return DataResult.error(() -> "strataByFloorIndex: two bands both start at floor "
                        + stratum.minFloorIndex + ", so one of them can never be reached");
            }
        }
        if (!starts.contains(0)) {
            return DataResult.error(() -> "strataByFloorIndex: no band covers floor 0 (the entrance"
                    + " floor). Bands run from their minFloorIndex downward, so the shallowest must"
                    + " start at 0. Found: " + starts);
        }
        return DataResult.success(table);
    }

    /**
     * The plan-time corridor fields this band disagrees with {@code base} on; empty when it only
     * repaints. See the class note for why these four are special.
     */
    public List<String> reshapedCorridorFields(CorridorConfig base) {
        if (corridor.isEmpty()) {
            return List.of();
        }
        CorridorConfig banded = corridor.get();
        List<String> reshaped = new ArrayList<>();
        if (banded.height() != base.height()) {
            reshaped.add("height");
        }
        if (banded.profile() != base.profile()) {
            reshaped.add("profile");
        }
        if (!banded.narrowHeight().equals(base.narrowHeight())) {
            reshaped.add("narrowHeight");
        }
        if (!banded.styles().equals(base.styles())) {
            reshaped.add("styles");
        }
        return reshaped;
    }
}
