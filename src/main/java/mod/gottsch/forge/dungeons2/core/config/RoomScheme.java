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

import java.util.Optional;

/**
 * One weighted way to dress a room: a coordinated set of decorative treatments, one slot per
 * architectural element, rolled <strong>once per room</strong> by {@code RoomSchemeSelector}.
 *
 * <h2>Why the roll is per-room and not per-element</h2>
 * <p>Until Jul 2026 the floor rolled its own pattern from {@code FloorConfig.patterns} and nothing
 * else had a pattern list at all, so there was nothing to coordinate with. Walls, ceilings and
 * pillars each want one too &mdash; and independent per-element rolls guarantee combinations no
 * author chose: pilasters at an offset that doesn't line up with the vault they carry, a formal
 * bordered floor under rough undecorated walls. Elements of a room are not independent variables;
 * an architectural style is one choice with several consequences. So the weighted roll happens
 * here, at the room level, and each element slot just names the treatment that scheme wants.</p>
 *
 * <p>The cost is that shared treatments are repeated across schemes rather than referenced &mdash;
 * two schemes that want the same floor border spell it out twice. That is the same trade
 * {@link MotifConfig} already accepted for one-file-per-motif: authoring redundancy in exchange for
 * one mechanism instead of a lookup layer between two.</p>
 *
 * <h2>Element slots</h2>
 * <p>{@code floor} holds a single {@link FloorPatternEntry} &mdash; the same record the old
 * {@code patterns} list held, minus the roll. Its {@code weight} field is ignored here; only this
 * scheme's own {@link #weight} matters. An absent slot means "plain for that element", so a scheme
 * with no slots at all is the undecorated room.</p>
 *
 * <p>{@code pots} holds a {@link PotConfig} &mdash; loot pots scattered on the room's floor. This
 * is the one slot whose output is not a block: {@code dungeonblocks}' pots are entities, so they
 * travel to the world on {@code RoomPlacements}' entity channel rather than through the block and
 * decoration pass.</p>
 *
 * <p>{@code wall} holds a {@link WallPatternEntry} &mdash; today, horizontal courses (plinth, chair
 * rail, crown molding). Unlike the floor slot it is drawn in the wall's own {@code (u, v)} space and
 * applied to all four runs, so one authored pattern comes out correctly oriented on each.</p>
 *
 * <p>{@code ceiling} holds a {@link CeilingPatternEntry} &mdash; an ordered list of flat treatments
 * (coffers, a soffit ring, a centre boss) layered over the plain ceiling. It is a list where the
 * floor and wall slots are single entries because surface patterns are sparse and therefore compose
 * for free; see that record for why no {@code "composite"} type is needed.</p>
 *
 * <p>{@code pillars} holds a {@link PillarPatternEntry} &mdash; free-standing columns standing in
 * the room's interior. It is the one slot that draws in the room's <em>volume</em> rather than on
 * one of its surfaces, which is why it needed a new element rather than another pattern type; see
 * that record. Declared here only once there was a provider behind it, which was the whole reason it
 * was held back.</p>
 *
 * <p>{@code platforms} holds a {@link PlatformPatternEntry} &mdash; raised daises standing on the
 * floor, optionally carrying a brazier or the like on top. The second volume slot, and it runs after
 * {@code pillars} for the same reason: it draws in the interior air the hollow step cleared.</p>
 *
 * <h2>Eligibility</h2>
 * <p>{@link #minHeight} and {@link #minSize} filter a scheme out of the roll for rooms too small to
 * carry it, <em>before</em> weights are totalled. This matters more than it did for floors:
 * {@code DungeonStackPlanner#pickRoomHeight} rolls {@code min(rand(5..10), max(width, depth))}, so a
 * room has only {@code height - 2} interior wall rows &mdash; between <strong>3 and 8</strong>. At
 * the low end, rows 1 and 2 are the door halves and row 3 is the door lintel, leaving nowhere to put
 * a crown molding course. A vaulted ceiling or a two-course wall is not a pattern that degrades
 * gracefully in a 5-high room; it is a pattern that must not be rolled there.</p>
 *
 * <p>{@code minHeight} is measured against the room's <strong>full</strong> height (floor block
 * through ceiling block inclusive, what {@code RoomData#getHeight} returns), not the interior row
 * count, because that is the number the planner actually rolls. {@code minSize} is measured against
 * the <em>smaller</em> of width and depth, so a long thin room is gated by its narrow axis &mdash;
 * which is the one that makes a centred pattern degenerate.</p>
 *
 * <p>Both default to 0 (always eligible). Authors should keep at least one unconstrained scheme in
 * the list; if a room matches none, {@code RoomSchemeSelector} degrades to the undecorated room
 * rather than forcing an ineligible one.</p>
 *
 * <h3>Upper bounds</h3>
 * <p>{@link #maxHeight} and {@link #maxSize} are the mirror image, and they exist because minimums
 * alone can only push a scheme <em>up</em> the size range, never confine it to the bottom. Without
 * them every modest scheme stays eligible in the largest rooms, so the grand schemes are always a
 * minority of the eligible weight there &mdash; and no amount of raising a grand scheme's weight
 * fixes that, because weight cannot remove a competitor. Capping the modest ones is the only lever
 * that makes a big room reliably feel big. The aesthetic case runs the same way: a
 * {@code centre} boss of size 1 is a lonely dot in a 15-wide ceiling, and a one-block
 * {@code cross} is a thread.</p>
 *
 * <p>Absent means unbounded, which is why they are {@link Optional} rather than an {@code int} with
 * a sentinel: {@code maxHeight: 0} would otherwise read as a legitimate bound and silently disable
 * the scheme everywhere. A bound below its matching minimum is rejected at load &mdash; it makes a
 * scheme eligible nowhere, which is indistinguishable at generation time from a scheme that is
 * merely unlucky.</p>
 *
 * <p><strong>Maxima make it possible to leave a gap.</strong> With minimums only, one unconstrained
 * scheme guarantees every room matches something; with bounds, a whole band of room sizes can fall
 * through to the undecorated fallback silently. {@code DatapackResourcesParseTest} sweeps the room
 * dimensions the planner can actually produce and fails if any of them matches nothing.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record RoomScheme(String name, int weight, int minHeight, int minSize,
                         Optional<Integer> maxHeight, Optional<Integer> maxSize,
                         Optional<FloorPatternEntry> floor, Optional<WallPatternEntry> wall,
                         Optional<CeilingPatternEntry> ceiling, Optional<PotConfig> pots,
                         Optional<PillarPatternEntry> pillars,
                         Optional<PlatformPatternEntry> platforms) {

    /**
     * A scheme with no element slots filled &mdash; an undecorated room of the given weight and
     * eligibility. Exists so that adding the next element slot does not churn every caller that
     * only cares about the roll; the canonical constructor has now widened six times.
     */
    public RoomScheme(String name, int weight, int minHeight, int minSize) {
        this(name, weight, minHeight, minSize, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /** Element slots with lower bounds only &mdash; the shape before {@code max*} was added. */
    public RoomScheme(String name, int weight, int minHeight, int minSize,
                      Optional<FloorPatternEntry> floor, Optional<WallPatternEntry> wall,
                      Optional<CeilingPatternEntry> ceiling, Optional<PotConfig> pots) {
        this(name, weight, minHeight, minSize, Optional.empty(), Optional.empty(),
                floor, wall, ceiling, pots, Optional.empty(), Optional.empty());
    }

    /** The four surface slots plus bounds &mdash; the shape before {@code pillars} was added. */
    public RoomScheme(String name, int weight, int minHeight, int minSize,
                      Optional<Integer> maxHeight, Optional<Integer> maxSize,
                      Optional<FloorPatternEntry> floor, Optional<WallPatternEntry> wall,
                      Optional<CeilingPatternEntry> ceiling, Optional<PotConfig> pots) {
        this(name, weight, minHeight, minSize, maxHeight, maxSize, floor, wall, ceiling, pots,
                Optional.empty(), Optional.empty());
    }

    /** The shape before {@code platforms} was added. */
    public RoomScheme(String name, int weight, int minHeight, int minSize,
                      Optional<Integer> maxHeight, Optional<Integer> maxSize,
                      Optional<FloorPatternEntry> floor, Optional<WallPatternEntry> wall,
                      Optional<CeilingPatternEntry> ceiling, Optional<PotConfig> pots,
                      Optional<PillarPatternEntry> pillars) {
        this(name, weight, minHeight, minSize, maxHeight, maxSize, floor, wall, ceiling, pots,
                pillars, Optional.empty());
    }

    /** The undecorated room: plain floor, plain walls, plain ceiling, no props, eligible everywhere. */
    public static final RoomScheme PLAIN = new RoomScheme("plain", 1, 0, 0);

    // Codecs.closed: a key this record does not declare is a load error rather than being dropped,
    // so a misspelled slot name ("celing") fails the pack instead of quietly leaving the room plain.
    public static final Codec<RoomScheme> CODEC = Codecs.closed(RecordCodecBuilder.<RoomScheme>mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(RoomScheme::name),
            Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                    .forGetter(RoomScheme::weight),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "minHeight", 0)
                    .forGetter(RoomScheme::minHeight),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "minSize", 0)
                    .forGetter(RoomScheme::minSize),
            // intRange(1, ..) not (0, ..): a bound of 0 fits no room the planner builds, so it can
            // only ever be a mistake, and a range codec turns it into a load error for free.
            Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "maxHeight")
                    .forGetter(RoomScheme::maxHeight),
            Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "maxSize")
                    .forGetter(RoomScheme::maxSize),
            Codecs.strictOptionalFieldOf(FloorPatternEntry.CODEC, "floor").forGetter(RoomScheme::floor),
            Codecs.strictOptionalFieldOf(WallPatternEntry.CODEC, "wall").forGetter(RoomScheme::wall),
            Codecs.strictOptionalFieldOf(CeilingPatternEntry.CODEC, "ceiling").forGetter(RoomScheme::ceiling),
            Codecs.strictOptionalFieldOf(PotConfig.CODEC, "pots").forGetter(RoomScheme::pots),
            Codecs.strictOptionalFieldOf(PillarPatternEntry.CODEC, "pillars").forGetter(RoomScheme::pillars),
            Codecs.strictOptionalFieldOf(PlatformPatternEntry.CODEC, "platforms").forGetter(RoomScheme::platforms)
    ).apply(instance, RoomScheme::new))).flatXmap(RoomScheme::validate, RoomScheme::validate);

    /**
     * Rejects an inverted range. A codec cannot express "at least the value of that other field",
     * so this is the one cross-field check that has to live outside the record's own field codecs.
     *
     * <p>Worth failing rather than clamping (which is what {@code PotConfig} does to its count
     * range): a scheme with {@code minHeight} 7 and {@code maxHeight} 5 is eligible for nothing, and
     * at generation time that is indistinguishable from a scheme that simply never won its roll --
     * exactly the silent-nothing class of failure the strict codecs here exist to prevent. Clamping
     * would instead invent a range the author never asked for.</p>
     */
    private static DataResult<RoomScheme> validate(RoomScheme scheme) {
        if (scheme.maxHeight.isPresent() && scheme.maxHeight.get() < scheme.minHeight) {
            return DataResult.error(() -> "scheme '" + scheme.name + "': maxHeight "
                    + scheme.maxHeight.get() + " is below minHeight " + scheme.minHeight
                    + ", so it fits no room at all");
        }
        if (scheme.maxSize.isPresent() && scheme.maxSize.get() < scheme.minSize) {
            return DataResult.error(() -> "scheme '" + scheme.name + "': maxSize "
                    + scheme.maxSize.get() + " is below minSize " + scheme.minSize
                    + ", so it fits no room at all");
        }
        // The element gates are validated from here rather than from inside SizeGate's own map
        // codec, because this is the level that knows the scheme's name and which slot it was --
        // "maxHeight 5 is below minHeight 7" is not an actionable error message on its own.
        DataResult<SizeGate> slots = DataResult.success(SizeGate.UNBOUNDED);
        slots = chain(slots, scheme.floor.map(FloorPatternEntry::gate), scheme.name, "floor");
        slots = chain(slots, scheme.wall.map(WallPatternEntry::gate), scheme.name, "wall");
        slots = chain(slots, scheme.ceiling.map(CeilingPatternEntry::gate), scheme.name, "ceiling");
        slots = chain(slots, scheme.pots.map(PotConfig::gate), scheme.name, "pots");
        slots = chain(slots, scheme.pillars.map(PillarPatternEntry::gate), scheme.name, "pillars");
        slots = chain(slots, scheme.platforms.map(PlatformPatternEntry::gate), scheme.name, "platforms");
        return slots.map(ignored -> scheme);
    }

    private static DataResult<SizeGate> chain(DataResult<SizeGate> soFar, Optional<SizeGate> gate,
                                              String scheme, String slot) {
        return soFar.flatMap(ignored -> gate
                .map(g -> g.validate("scheme '" + scheme + "', " + slot + " slot"))
                .orElseGet(() -> DataResult.success(SizeGate.UNBOUNDED)));
    }

    /**
     * The element slots that a room of these dimensions actually renders.
     *
     * <p>A slot whose own {@link SizeGate} the room fails is dropped, while the rest of the scheme
     * still applies &mdash; so one scheme can carry a crown moulding that simply is not there in a
     * room too short for it, instead of needing a second scheme that is identical but for the wall.
     * <strong>This does not change any probability</strong>: the scheme was already chosen, and it
     * still fires at its full weight.</p>
     *
     * <p>These live here, next to the data, rather than being applied by {@code BasicRoomGenerator}
     * at each call site. A gate that a caller can forget to check is a gate that will eventually be
     * forgotten &mdash; and the failure would be silent, since a slot drawn where it does not fit
     * looks like an authoring mistake rather than a missing check.</p>
     */
    public Optional<FloorPatternEntry> floorFor(int width, int depth, int height) {
        return floor.filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<WallPatternEntry> wallFor(int width, int depth, int height) {
        return wall.filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<CeilingPatternEntry> ceilingFor(int width, int depth, int height) {
        return ceiling.filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<PotConfig> potsFor(int width, int depth, int height) {
        return pots.filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<PillarPatternEntry> pillarsFor(int width, int depth, int height) {
        return pillars.filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<PlatformPatternEntry> platformsFor(int width, int depth, int height) {
        return platforms.filter(entry -> entry.gate().fits(width, depth, height));
    }

    /**
     * Whether this scheme fills any element slot at all. False for the deliberately undecorated
     * room, which is a legitimate authored outcome rather than a mistake &mdash; the distinction
     * that matters is between a scheme that <em>meant</em> to draw nothing and one whose slots all
     * gated out, and only the second is a fault.
     */
    public boolean declaresAnySlot() {
        return floor.isPresent() || wall.isPresent() || ceiling.isPresent() || pots.isPresent()
                || pillars.isPresent() || platforms.isPresent();
    }

    /** Whether this scheme draws anything at all in a room of these dimensions. */
    public boolean drawsAnything(int width, int depth, int height) {
        return floorFor(width, depth, height).isPresent()
                || wallFor(width, depth, height).isPresent()
                || ceilingFor(width, depth, height).isPresent()
                || potsFor(width, depth, height).isPresent()
                || pillarsFor(width, depth, height).isPresent()
                || platformsFor(width, depth, height).isPresent();
    }

    /**
     * Whether this scheme may be rolled for a room of these dimensions. {@code height} is the full
     * room height; {@code width}/{@code depth} the full footprint, walls included.
     *
     * <p>Both bounds are <strong>inclusive</strong>, and both are measured against the same numbers
     * their minimums are: full height, and the <em>smaller</em> of width and depth.</p>
     */
    public boolean fits(int width, int depth, int height) {
        int size = Math.min(width, depth);
        return height >= minHeight
                && size >= minSize
                && maxHeight.map(max -> height <= max).orElse(true)
                && maxSize.map(max -> size <= max).orElse(true);
    }
}
