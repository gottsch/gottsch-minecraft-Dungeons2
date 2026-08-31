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

import net.minecraft.util.RandomSource;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

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
 * <p>{@code spawners} holds a {@link SpawnerConfig} &mdash; invisible proximity mob-set spawners
 * standing in the room's interior. The only slot whose output is neither seen nor collided with, and
 * the only one that reaches the world through {@code BlockEntityData}; it is what lets a
 * <em>procedural</em> room have monsters, where before only an authored template carrying the marker
 * block could. See that record.</p>
 *
 * <h2>Eligibility</h2>
 * <p>{@link #minHeight} and {@link #minSize} filter a scheme out of the roll for rooms too small to
 * carry it, <em>before</em> weights are totalled. This matters more than it did for floors:
 * {@code DungeonStackPlanner#pickRoomHeight} rolls {@code 5 + rand(6)} and clamps it into the
 * footprint's {@link RoomHeightBand} (#51), so a room has only {@code height - 2} interior wall
 * rows &mdash; between <strong>3 and 8</strong> under the shipped taper. At
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
 * <h3>Depth</h3>
 * <p>{@link #minFloorIndex} and {@link #maxFloorIndex} are the third eligibility axis, and the only
 * one that is not about the room's shape: they say how far into the dungeon a scheme belongs.
 * <strong>0 is the entrance floor</strong>, counting downward &mdash; the same ordinal
 * {@code FloorLayout#getFloorIndex} carries, deliberately not a world Y, since a dungeon under a
 * mountain has its third floor higher up than a ravine dungeon's first.</p>
 *
 * <p>{@code minFloorIndex: 0} and no maximum is the default and means "anywhere", so an existing
 * pack is unaffected. Unlike {@code maxHeight}/{@code maxSize}, {@code maxFloorIndex} accepts
 * <strong>0</strong>: "this scheme only on the entrance floor" is a real thing to author, where a
 * {@code maxHeight} of 0 could only ever be a mistake.</p>
 *
 * <p><strong>Maxima make it possible to leave a gap.</strong> With minimums only, one unconstrained
 * scheme guarantees every room matches something; with bounds, a whole band of room sizes can fall
 * through to the undecorated fallback silently. {@code DatapackResourcesParseTest} sweeps the room
 * dimensions the planner can actually produce and fails if any of them matches nothing.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record RoomScheme(String name, int weight, SizeGate gate,
                         SlotOptions<FloorPatternEntry> floor, SlotOptions<WallPatternEntry> wall,
                         SlotOptions<CeilingPatternEntry> ceiling, SlotOptions<PotConfig> pots,
                         SlotOptions<PillarPatternEntry> pillars,
                         SlotOptions<PlatformPatternEntry> platforms,
                         SlotOptions<SpawnerConfig> spawners,
                         SlotOptions<ChestConfig> chests,
                         SlotOptions<PitPatternEntry> pit,
                         FloorRange floors,
                         Optional<String> parent, boolean isAbstract) {

    /** The shape before the {@code pit} slot (#3): a scheme whose floor is flat everywhere. */
    public RoomScheme(String name, int weight, SizeGate gate,
                      Optional<FloorPatternEntry> floor, Optional<WallPatternEntry> wall,
                      Optional<CeilingPatternEntry> ceiling, Optional<PotConfig> pots,
                      Optional<PillarPatternEntry> pillars,
                      Optional<PlatformPatternEntry> platforms,
                      Optional<SpawnerConfig> spawners,
                      Optional<ChestConfig> chests,
                      FloorRange floors,
                      Optional<String> parent, boolean isAbstract) {
        this(name, weight, gate,
                SlotOptions.of(floor), SlotOptions.of(wall), SlotOptions.of(ceiling),
                SlotOptions.of(pots), SlotOptions.of(pillars), SlotOptions.of(platforms),
                SlotOptions.of(spawners), SlotOptions.of(chests), SlotOptions.empty(),
                floors, parent, isAbstract);
    }

    /**
     * The scheme's own eligibility range, folded into a {@link SizeGate} when the {@code chests}
     * slot took the record past DFU's 16-argument ceiling ({@code RecordCodecBuilder.group} stops
     * at {@code Products.P16}). The four fields were already exactly a {@code SizeGate}; they are
     * simply named as one now.
     *
     * <p><strong>No JSON changed.</strong> {@code SizeGate.MAP_CODEC} is a {@code MapCodec}, so
     * {@code minHeight}/{@code minSize}/{@code maxHeight}/{@code maxSize} stay flat keys on the
     * scheme object exactly as before, and the four accessors below keep the old names working. A
     * pack sees nothing.</p>
     */
    public int minHeight() {
        return gate.minHeight();
    }

    /** See {@link #minHeight}. */
    public int minSize() {
        return gate.minSize();
    }

    /** See {@link #minHeight}. */
    public Optional<Integer> maxHeight() {
        return gate.maxHeight();
    }

    /** See {@link #minHeight}. */
    public Optional<Integer> maxSize() {
        return gate.maxSize();
    }

    /**
     * The pre-fold canonical shape, kept as a constructor so every existing caller -- and there are
     * eighteen -- goes on compiling unchanged.
     */
    public RoomScheme(String name, int weight, int minHeight, int minSize,
                      Optional<Integer> maxHeight, Optional<Integer> maxSize,
                      Optional<FloorPatternEntry> floor, Optional<WallPatternEntry> wall,
                      Optional<CeilingPatternEntry> ceiling, Optional<PotConfig> pots,
                      Optional<PillarPatternEntry> pillars,
                      Optional<PlatformPatternEntry> platforms,
                      Optional<SpawnerConfig> spawners,
                      FloorRange floors,
                      Optional<String> parent, boolean isAbstract) {
        this(name, weight, new SizeGate(minHeight, minSize, maxHeight, maxSize),
                floor, wall, ceiling, pots, pillars, platforms, spawners, Optional.empty(),
                floors, parent, isAbstract);
    }

    /** The entrance-floor index; 0, and named so the arithmetic in a gate reads as depth. */
    public int minFloorIndex() {
        return floors.min();
    }

    /** See {@link #minFloorIndex}. */
    public Optional<Integer> maxFloorIndex() {
        return floors.max();
    }

    /**
     * A scheme with no element slots filled &mdash; an undecorated room of the given weight and
     * eligibility. Exists so that adding the next element slot does not churn every caller that
     * only cares about the roll; the canonical constructor has now widened six times.
     */
    public RoomScheme(String name, int weight, int minHeight, int minSize) {
        this(name, weight, minHeight, minSize, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                FloorRange.ANY, Optional.empty(), false);
    }

    /** The shape before the {@code spawners} slot was added. */
    public RoomScheme(String name, int weight, int minHeight, int minSize,
                      Optional<Integer> maxHeight, Optional<Integer> maxSize,
                      Optional<FloorPatternEntry> floor, Optional<WallPatternEntry> wall,
                      Optional<CeilingPatternEntry> ceiling, Optional<PotConfig> pots,
                      Optional<PillarPatternEntry> pillars,
                      Optional<PlatformPatternEntry> platforms) {
        this(name, weight, minHeight, minSize, maxHeight, maxSize, floor, wall, ceiling, pots,
                pillars, platforms, Optional.empty(), FloorRange.ANY, Optional.empty(), false);
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
            SizeGate.MAP_CODEC.forGetter(RoomScheme::gate),
            SlotOptions.field(FloorPatternEntry.MAP_CODEC, "floor").forGetter(RoomScheme::floor),
            SlotOptions.field(WallPatternEntry.MAP_CODEC, "wall").forGetter(RoomScheme::wall),
            SlotOptions.field(CeilingPatternEntry.MAP_CODEC, "ceiling").forGetter(RoomScheme::ceiling),
            SlotOptions.field(PotConfig.MAP_CODEC, "pots").forGetter(RoomScheme::pots),
            SlotOptions.field(PillarPatternEntry.MAP_CODEC, "pillars").forGetter(RoomScheme::pillars),
            SlotOptions.field(PlatformPatternEntry.MAP_CODEC, "platforms").forGetter(RoomScheme::platforms),
            SlotOptions.field(SpawnerConfig.MAP_CODEC, "spawners").forGetter(RoomScheme::spawners),
            SlotOptions.field(ChestConfig.MAP_CODEC, "chests").forGetter(RoomScheme::chests),
            // #3. Sits beside the surface slots rather than inside `floor` because it is not a
            // paving pattern: it changes the room's GEOMETRY, and it is bounded by the floor's
            // sinkOffset budget rather than by anything a floor pattern knows.
            SlotOptions.field(PitPatternEntry.MAP_CODEC, "pit").forGetter(RoomScheme::pit),
            // The depth axis, flattened into minFloorIndex/maxFloorIndex keys. Spelled that way
            // rather than minFloor/minDepth because a scheme object already has a "floor" key
            // meaning the floor SURFACE pattern, and two unrelated senses of "floor" one line apart
            // is how an author misreads a file. One group argument rather than two because this
            // record kept hitting DFU's 16-argument ceiling -- see FloorRange, and see SizeGate
            // above, which is the fold the chests slot forced.
            FloorRange.MAP_CODEC.forGetter(RoomScheme::floors),
            Codecs.strictOptionalFieldOf(Codec.STRING, "extends").forGetter(RoomScheme::parent),
            Codecs.strictOptionalFieldOf(Codec.BOOL, "abstract", false).forGetter(RoomScheme::isAbstract)
    ).apply(instance, RoomScheme::new))).flatXmap(RoomScheme::validate, RoomScheme::validate);

    /**
     * This scheme with every <strong>unfilled</strong> element slot taken from {@code parent}.
     * Applied by {@link MotifConfigFragment#resolve} after the fragments have merged, so a parent
     * may live in another file and an addon retuning the parent reaches every child for free.
     *
     * <h2>What does NOT inherit, and why</h2>
     * <p><strong>Weight, all four size bounds and both floor bounds stay the child's own.</strong> Two reasons, and the
     * second is the real one:</p>
     * <ul>
     *   <li>{@code weight}, {@code minHeight} and {@code minSize} are primitives with defaults, so
     *       nothing here can tell "the author omitted it" from "the author wrote the default" &mdash;
     *       the same limitation {@code SizeGate} and {@code PatternEntry} keep running into. An
     *       inheriting primitive would silently ignore a deliberate {@code minSize: 0}.</li>
     *   <li>More importantly, <strong>a variant exists because its eligibility differs.</strong>
     *       Inheritance is for schemes that differ in <em>content</em> (the same hall in andesite and
     *       in deepslate); {@code minSize}/{@code maxSize} are how an author says which rooms a
     *       scheme is <em>for</em>, and quietly copying that from a parent is how a whole size band
     *       ends up with no scheme at all.</li>
     * </ul>
     *
     * <p>A slot the child fills <strong>replaces the parent's wholesale</strong>, with no merging of
     * the lists inside it. A child that wants the parent's three ceiling patterns plus one more
     * restates all four &mdash; deliberately, because a list-merge has no way to express removing an
     * inherited entry, and "override with less" is the commoner intent.</p>
     *
     * <p>{@link #parent} is left set on the result rather than cleared: it costs nothing, and it is
     * the only thing a dumped scheme carries to say where half of it came from.</p>
     */
    public RoomScheme inheritFrom(RoomScheme parentScheme) {
        return new RoomScheme(name, weight, gate,
                floor.orElse(parentScheme.floor()),
                wall.orElse(parentScheme.wall()),
                ceiling.orElse(parentScheme.ceiling()),
                pots.orElse(parentScheme.pots()),
                pillars.orElse(parentScheme.pillars()),
                platforms.orElse(parentScheme.platforms()),
                spawners.orElse(parentScheme.spawners()),
                chests.orElse(parentScheme.chests()),
                pit.orElse(parentScheme.pit()),
                floors,
                parent, isAbstract);
    }

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
        // The only inheritance fault a codec can see. A missing parent, or a parent that itself
        // extends, are both cross-file questions and are caught by MotifConfigFragment#resolve.
        if (scheme.parent.map(scheme.name::equals).orElse(false)) {
            return DataResult.error(() -> "scheme '" + scheme.name + "': extends itself");
        }
        if (scheme.maxHeight().isPresent() && scheme.maxHeight().get() < scheme.minHeight()) {
            return DataResult.error(() -> "scheme '" + scheme.name + "': maxHeight "
                    + scheme.maxHeight().get() + " is below minHeight " + scheme.minHeight()
                    + ", so it fits no room at all");
        }
        if (scheme.maxSize().isPresent() && scheme.maxSize().get() < scheme.minSize()) {
            return DataResult.error(() -> "scheme '" + scheme.name + "': maxSize "
                    + scheme.maxSize().get() + " is below minSize " + scheme.minSize()
                    + ", so it fits no room at all");
        }
        // The element gates are validated from here rather than from inside SizeGate's own map
        // codec, because this is the level that knows the scheme's name and which slot it was --
        // "maxHeight 5 is below minHeight 7" is not an actionable error message on its own.
        DataResult<FloorRange> floorRange = scheme.floors.validate("scheme '" + scheme.name + "'");
        if (floorRange.error().isPresent()) {
            return DataResult.error(() -> floorRange.error().get().message());
        }
        DataResult<SizeGate> slots = DataResult.success(SizeGate.UNBOUNDED);
        slots = chain(slots, scheme.floor.all().map(FloorPatternEntry::gate), scheme.name, "floor");
        slots = chain(slots, scheme.wall.all().map(WallPatternEntry::gate), scheme.name, "wall");
        slots = chain(slots, scheme.ceiling.all().map(CeilingPatternEntry::gate), scheme.name, "ceiling");
        slots = chain(slots, scheme.pots.all().map(PotConfig::gate), scheme.name, "pots");
        slots = chain(slots, scheme.pillars.all().map(PillarPatternEntry::gate), scheme.name, "pillars");
        slots = chain(slots, scheme.platforms.all().map(PlatformPatternEntry::gate), scheme.name, "platforms");
        slots = chain(slots, scheme.spawners.all().map(SpawnerConfig::gate), scheme.name, "spawners");
        return slots.map(ignored -> scheme);
    }

    /**
     * Validates EVERY authored alternative's gate, not merely the one a room would roll. An
     * inverted gate on the third of four wall options is a load error the moment it is written,
     * rather than a wall that draws nothing in whichever rooms happen to pick that option --
     * which is indistinguishable from an option that was merely unlucky.
     */
    private static DataResult<SizeGate> chain(DataResult<SizeGate> soFar, Stream<SizeGate> gates,
                                              String scheme, String slot) {
        return gates.reduce(soFar,
                (result, gate) -> result.flatMap(ignored ->
                        gate.validate("scheme '" + scheme + "', " + slot + " slot")),
                (left, right) -> left.flatMap(ignored -> right));
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
        return floor.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<WallPatternEntry> wallFor(int width, int depth, int height) {
        return wall.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<CeilingPatternEntry> ceilingFor(int width, int depth, int height) {
        return ceiling.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /**
     * See {@link #floorFor}. Note this answers only whether the ROOM may have a pit; whether the
     * floor has anywhere to put one is {@code PitPatternEntry#depthWithin}, and it is asked later
     * because {@code sinkOffset} lives in a different datapack registry.
     */
    public Optional<PitPatternEntry> pitFor(int width, int depth, int height) {
        return pit.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<PotConfig> potsFor(int width, int depth, int height) {
        return pots.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<PillarPatternEntry> pillarsFor(int width, int depth, int height) {
        return pillars.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<PlatformPatternEntry> platformsFor(int width, int depth, int height) {
        return platforms.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #floorFor}. */
    public Optional<ChestConfig> chestsFor(int width, int depth, int height) {
        return chests.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /** See {@link #chestsFor}. */
    public Optional<SpawnerConfig> spawnersFor(int width, int depth, int height) {
        return spawners.value().filter(entry -> entry.gate().fits(width, depth, height));
    }

    /**
     * Whether this scheme fills any element slot at all. False for the deliberately undecorated
     * room, which is a legitimate authored outcome rather than a mistake &mdash; the distinction
     * that matters is between a scheme that <em>meant</em> to draw nothing and one whose slots all
     * gated out, and only the second is a fault.
     */
    public boolean declaresAnySlot() {
        // spawners counts, even though it is the one slot that draws nothing a player can see: what
        // this asks is whether the author declared anything, and a spawner slot that gated itself
        // out is exactly as much a fault as a ceiling that did.
        return !floor.isEmpty() || !wall.isEmpty() || !ceiling.isEmpty() || !pots.isEmpty()
                || !pillars.isEmpty() || !platforms.isEmpty() || !spawners.isEmpty()
                || !pit.isEmpty();
    }

    /**
     * Whether this scheme draws anything at all in a room of these dimensions.
     *
     * <p>Asked of an <strong>unresolved</strong> scheme -- by {@code DatapackResourcesParseTest}'s
     * sweep over the room sizes the planner can actually produce, among others -- so it reads every
     * authored alternative rather than a chosen one. A scheme whose every option gates out of a
     * size band is the fault being looked for; that one of its options is a {@code none} is not,
     * because drawing nothing is then what the author asked for.</p>
     */
    public boolean drawsAnything(int width, int depth, int height) {
        return anyOptionFits(floor, FloorPatternEntry::gate, width, depth, height)
                || anyOptionFits(wall, WallPatternEntry::gate, width, depth, height)
                || anyOptionFits(ceiling, CeilingPatternEntry::gate, width, depth, height)
                || anyOptionFits(pots, PotConfig::gate, width, depth, height)
                || anyOptionFits(pillars, PillarPatternEntry::gate, width, depth, height)
                || anyOptionFits(platforms, PlatformPatternEntry::gate, width, depth, height)
                || anyOptionFits(spawners, SpawnerConfig::gate, width, depth, height)
                || anyOptionFits(pit, PitPatternEntry::gate, width, depth, height);
    }

    private static <T> boolean anyOptionFits(SlotOptions<T> slot, Function<T, SizeGate> gate,
                                             int width, int depth, int height) {
        return slot.all().anyMatch(entry -> gate.apply(entry).fits(width, depth, height));
    }

    /**
     * This scheme with each slot's alternatives collapsed to the one this room gets -- the second
     * half of the single per-room roll, and the only shape that may be handed to a generator.
     *
     * <p>Called by {@code RoomSchemeSelector} on the scheme it returns, so that the whole of a
     * room's decoration is still decided in one place and out of one {@code RandomSource}. Slots
     * resolve in <strong>declaration order</strong>, which is what makes the draw reproducible
     * across the repeated {@code postProcess} calls a piece gets per overlapping chunk; a scheme
     * holding no option list consumes nothing here at all, so an unconverted motif generates the
     * dungeons it always did. See {@link SlotOptions#resolve}.</p>
     */
    public RoomScheme resolve(int width, int depth, int height, RandomSource random) {
        return new RoomScheme(name, weight, gate,
                floor.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                wall.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                ceiling.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                pots.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                pillars.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                platforms.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                spawners.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                chests.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                pit.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                floors, parent, isAbstract);
    }

    /**
     * Whether this scheme may be rolled for a room of these dimensions. {@code height} is the full
     * room height; {@code width}/{@code depth} the full footprint, walls included.
     *
     * <p>Both bounds are <strong>inclusive</strong>, and both are measured against the same numbers
     * their minimums are: full height, and the <em>smaller</em> of width and depth.</p>
     */
    /**
     * Whether this scheme may be rolled on this floor. <strong>0 is the entrance floor</strong>,
     * counting downward; both bounds are inclusive and absent {@link #maxFloorIndex} is unbounded.
     *
     * <h2>Why depth is a separate question from size</h2>
     * <p>Size asks "does this pattern degenerate in a room this small"; depth asks "does this
     * content belong this far into the dungeon". They are independent, so a grand hall gated to
     * {@code minFloorIndex 3} still has to be a big room, and both checks apply. Kept as two
     * methods rather than one because the selector wants to reject on depth <em>first</em> &mdash;
     * it is the coarser filter, it is a single comparison against a value that does not vary within
     * a floor, and it is the one an author is more likely to have meant.</p>
     *
     * <p>Combined with a cap on a template ({@code #44}) this is what a mini-boss is made of:
     * {@code minFloorIndex} puts it deep, the cap makes it rare.</p>
     */
    public boolean fitsFloor(int floorIndex) {
        return floors.contains(floorIndex);
    }

    /**
     * Whether this scheme may be rolled for a room of these dimensions <em>on this floor</em> --
     * the whole eligibility question, which is what {@code RoomSchemeSelector} asks.
     */
    public boolean fits(int width, int depth, int height, int floorIndex) {
        return fitsFloor(floorIndex) && fits(width, depth, height);
    }

    public boolean fits(int width, int depth, int height) {
        // Delegated since the fold: this method and SizeGate#fits were the same four comparisons
        // written twice, which is exactly the drift the fold removes.
        return gate.fits(width, depth, height);
    }
}
