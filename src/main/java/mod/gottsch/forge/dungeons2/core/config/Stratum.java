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
 * <h2>An overlay on the MOTIF &mdash; bands do not inherit from each other</h2>
 * <p>A section omitted from a band <strong>falls through to the motif's own</strong>, never to the
 * band above it. Each band is an independent overlay on one base, which is what makes a band
 * readable on its own: what floor 3 looks like depends on the motif and on band 3, and on nothing
 * in between. {@link MotifConfig#forFloor} is the whole of it &mdash; select the band for the
 * floor, {@code orElse} each section to the motif's.
 *
 * <p>Two things follow, and both are the reason this is an overlay rather than a config per band.
 * A band is a handful of lines rather than a second copy of a 670-line {@code base.json}, so it
 * cannot drift from the base on sections it never meant to touch. And <strong>a band that declares
 * nothing at all is legal and useful</strong>: {@code {"minFloorIndex": 1}} says "from floor 1 down,
 * the motif as authored", which is how you end the band above it.
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
 * <h2>{@code schemes} is the one section that MERGES, and why</h2>
 * <p>Every other section above replaces whole. {@code schemes} instead <strong>merges by
 * name</strong> onto the motif's list: a band entry whose name the motif already declares replaces
 * that one in place, and a band entry with a new name is appended. It is the same rule
 * {@link MotifConfigFragment#resolve} uses to fold one motif's files together, and for the same
 * reason that class writes down &mdash; <em>coherent whole &rarr; replace, independent entries
 * &rarr; merge by key</em>. A depth table or an element section is one authored whole; a scheme
 * list is a bag of independent entries, which is exactly why fragments already merge it.
 *
 * <p>What that buys is the reason this field exists at all (Gottsch, 2026-08-27). A band could
 * always dress its own depth by writing a {@code pattern} on {@code wall}/{@code ceiling}/
 * {@code floor}, but on the {@code ceiling} and {@code floor} that pattern is <strong>tier 2</strong>:
 * it draws in every room whose rolled scheme names no slot of its own, and in nothing else. Which
 * of those two it is depends entirely on what the schemes happen to say, and it lands wrong at both
 * ends. Measured on the mud band's authored joists &mdash; {@code BandCeilingIncidenceProbe}, 60
 * MEDIUM dungeons, 2874 rooms &mdash; the band drew <strong>55.9%</strong> against the scheme's own
 * 11.0%, because seven of classic's ten rollable schemes name no {@code ceiling}: a default became
 * the depth's default look. The same band's authored wall plinth drew <strong>0%</strong>, because
 * ten of eleven schemes DO name {@code wall}.
 *
 * <p>Those two numbers pulled in opposite directions and were fixed separately. The wall got
 * <strong>composition</strong> ({@code WallPatternSelector#providerFor}) &mdash; band first, scheme
 * on top &mdash; so a plinth true of the whole depth is stated once. The ceiling and floor keep
 * first-match-wins, and per-stratum schemes are their answer: a depth gets its own rooms by naming
 * them, not by dressing whatever rolls.
 *
 * <p><strong>Replace-whole was considered and rejected.</strong> It would have matched the other
 * five sections and made the tier-2 problem moot outright (every scheme at that depth is the band's
 * own, so nothing falls through) &mdash; a real advantage this rule gives up. It also costs a band
 * that wants one new room type a restatement
 * of all ten of classic's schemes &mdash; the drift an overlay exists to prevent, and the same
 * argument that made every section optional in the first place.
 *
 * <p>A band declaring {@code "schemes": []} is therefore a <strong>no-op</strong>, not "this depth
 * has no schemes": an empty list merges onto the motif's and changes nothing. There is no way to
 * subtract a scheme at a depth, deliberately &mdash; the same "a later file can only add to or
 * replace something nameable" rule {@code resolve} is built on.
 *
 * <h2>Band schemes get the full {@code extends} / {@code abstract} treatment</h2>
 * <p>They are inherited by {@link MotifConfigFragment#inherit} exactly like the motif's, against
 * the <strong>motif-wide</strong> scheme names &mdash; so a band scheme may extend an abstract
 * template declared in {@code base.json}, which is the authoring win that makes a band short. It
 * cannot happen in this codec: a parent is addressed by name across the whole motif and may live in
 * another file, so it is only resolvable once every fragment has folded. {@code resolve} therefore
 * writes the inherited list back onto the band via {@link #withSchemes}, and {@link #schemes()}
 * holds resolved entries by the time anything renders.
 *
 * <p>Skipping that pass was the live trap here, and it fails <em>silently</em> in both directions:
 * an {@code abstract} band scheme would roll as a real room, and an {@code extends} would render
 * with half its content missing. Nothing about the shape announces it, which is why it is written
 * down rather than left to the reader.
 *
 * <h2>A stratum is not a second motif</h2>
 * <p>The shortcut is tempting enough to write down why not. Pools, weathering lists and motif
 * configs are all motif-keyed, so {@code classic_shallow} / {@code classic_deep} would work almost
 * for free &mdash; and would then multiply against every real motif ({@code catacombs_deep},
 * {@code deepslate_shallow}). Motif is the <em>theme</em> selector; strata must compose with it,
 * not enumerate against it.
 *
 * <h2>The corridor band is simply used, and a band with no styles is a plain corridor</h2>
 * <p>There is no guard on what a band's {@code corridor} may say, because there is nothing to
 * guard. Everything {@code BasicCorridorGenerator} emits is bounded by
 * {@code floorY .. floorY + CorridorData.getWallHeight() - 1} &mdash; the height the PIECE carries,
 * set by the planner &mdash; and the arch sits at {@code ceilingHeight - 2}, strictly inside it. So
 * {@code profile}, {@code archBlock}, {@code narrowHeight} and {@code courses} cannot put a block
 * outside the box no matter what a band says.
 *
 * <p>{@code styles} needs no special case either. A band that declares none renders through
 * {@link CorridorConfig#baseline()} &mdash; the band's own floor, ceiling, arch and courses, which
 * is exactly what "this floor has no flourishes, build it from the base elements" should mean, and
 * exactly what an unstyled motif has always done. A band that DOES want flourishes declares
 * {@code styles} using the same names; the planner rolled one of the motif's names, and
 * {@code styleFor} finds the band's entry under it. Per-style banding therefore needs no mechanism
 * at all.
 *
 * <p><strong>The one field that cannot do what it looks like it does is {@code height}.</strong>
 * The corridor's real height is rolled once for the whole dungeon at plan time, from the unbanded
 * motif, and travels on the piece &mdash; so a band's {@code height} never sets it. It is not
 * inert, though: {@link CorridorStyle#narrowCellHeight()} falls back to it when
 * {@code narrowHeight} is absent, so it still sets the dropped ceiling of 1-cell-wide runs. Author
 * a band's {@code height} to match the motif's unless that is what you want to move.
 *
 * @author Mark Gottschling on Aug 23, 2026
 */
public record Stratum(int minFloorIndex, Optional<String> name, Optional<WallConfig> wall,
                      Optional<CeilingConfig> ceiling, Optional<DoorConfig> door,
                      Optional<CorridorConfig> corridor, Optional<FloorConfig> floor,
                      Optional<List<RoomScheme>> schemes) {

    /** The shape before {@code schemes}: a band that repaints the shell and leaves dressing alone. */
    public Stratum(int minFloorIndex, Optional<String> name, Optional<WallConfig> wall,
                   Optional<CeilingConfig> ceiling, Optional<DoorConfig> door,
                   Optional<CorridorConfig> corridor, Optional<FloorConfig> floor) {
        this(minFloorIndex, name, wall, ceiling, door, corridor, floor, Optional.empty());
    }

    /**
     * This band with its scheme list swapped for {@code resolved}. Used by
     * {@link MotifConfigFragment#resolve} alone, to write back the band's schemes once
     * {@code extends} has been resolved and the abstract templates dropped &mdash; see
     * {@link #schemes()} for why that cannot happen in the codec.
     */
    Stratum withSchemes(List<RoomScheme> resolved) {
        return new Stratum(minFloorIndex, name, wall, ceiling, door, corridor, floor,
                Optional.of(resolved));
    }

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
            Codecs.strictOptionalFieldOf(FloorConfig.CODEC, "floor").forGetter(Stratum::floor),
            // The ONE section that is not whole-replace -- see #schemes(). Optional rather than a
            // defaulted list so that "declares none" stays distinguishable from "declares an empty
            // list", even though both currently resolve to the motif's own.
            Codecs.strictOptionalFieldOf(RoomScheme.CODEC.listOf(), "schemes").forGetter(Stratum::schemes)
    ).apply(instance, Stratum::new))).flatXmap(Stratum::validateBand, Stratum::validateBand);

    /**
     * The only thing a band can get wrong on its own: a {@link #name} that is not a path segment.
     *
     * <p>Notably absent is any objection to a band that declares <strong>no sections at all</strong>.
     * {@code {"minFloorIndex": 1}} is not an authoring slip &mdash; it reads "from floor 1 down, the
     * motif as authored", which is exactly how you end the band above it. Rejecting it forced the
     * author to restate a section they did not want to change, which is the drift an overlay exists
     * to prevent. A band carrying only a {@code name} is legal for the same reason: it moves the
     * room pools for that depth and leaves the shell alone.
     */
    private static DataResult<Stratum> validateBand(Stratum stratum) {
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
}
