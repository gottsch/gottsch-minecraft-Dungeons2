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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.PilastersWallPatternProvider;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link RoomScheme}'s {@code wall} slot: an <strong>ordered list</strong> of treatments laid over
 * a room's plain wall block, each drawn on top of the last.
 *
 * <h2>Why a list, and why the type moved down a level</h2>
 * <p>Until Aug 2026 this slot was a single typed entry, which said that a wall had one treatment and
 * the {@code type} chose <em>which</em>. That was true while {@code courses} was the only type and
 * false the moment a second arrived: courses are horizontal and pilasters vertical, and a wall wants
 * <strong>both</strong> &mdash; a plinth running between engaged columns is one look, not two
 * competing ones. So {@code type} belongs on the pattern, not on the slot.</p>
 *
 * <p>This is the shape {@link CeilingPatternEntry} has had since it shipped, and its reasoning
 * carries over unchanged: surface patterns are sparse, so a cell a pattern does not mark is left
 * null and the next pattern draws it. Layering is therefore just applying them in sequence, which
 * makes the list itself the composition mechanism and a {@code "composite"} type unnecessary.
 * Ordering is execution order, the same convention the {@code processor_list} files use &mdash;
 * which is also how an author says whether a course runs across a pilaster or the pilaster
 * interrupts the course. Put the one that should win last.</p>
 *
 * <h2>Why courses first</h2>
 * <p>One provider covers plinth, chair rail, string course <em>and</em> crown molding, because all
 * four are the same thing at a different height. They are also the only wall pattern with no join
 * problem: a band sits at a constant {@code v}, so it runs continuously around all four walls no
 * matter how the corner columns are shared out.</p>
 *
 * <h2>Pilasters &mdash; the vertical counterpart</h2>
 * <p>Evenly spaced vertical strips, engaged into the wall when {@code projection} is 0 and standing
 * out from it when it is not. They are laid out <strong>symmetric about each run's own centre</strong>
 * rather than counted from {@code u = 0}: the Z-edge runs are {@code width} long and the X-edge runs
 * {@code depth - 2}, so a fixed stride from the origin gives the four walls different phase and
 * reads accidental. {@code WallSurface} guarantees runs are symmetric about their own centre for
 * exactly this reason.</p>
 *
 * <h2>Projection &mdash; trim that stands out from the wall</h2>
 * <p>{@code projection} moves a course off the wall plane and into the room: {@code 0} (the
 * default) replaces a wall cell, {@code 1} puts the block in the interior cell in front of it. That
 * is what turns a flat band into a real cornice or moulding, and it is only possible because the
 * room's interior air is emitted by its own step rather than by the wall generator.</p>
 *
 * <p>Two things a projecting course must respect, both handled by {@code WallSurface}: it is
 * skipped in front of a doorway at door height (it would block the way through), and it writes
 * <em>only</em> its own cells rather than filling the rest of the layer, since that layer is the
 * room's open air.</p>
 *
 * <p><strong>A bottom-anchored projection collides with pots.</strong> Loot pots stand on the
 * interior cells that touch a wall, at exactly the height a {@code bottom}/0 projecting course
 * occupies, so a scheme carrying both puts a pot inside a block. Project the top (a cornice), not
 * the bottom, or leave the pots slot empty &mdash; {@code DatapackResourcesParseTest} fails the
 * build on a shipped scheme that combines them.</p>
 *
 * <h2>Anchoring</h2>
 * <p>{@link CourseAnchor#TOP} is not a convenience &mdash; it is required for the feature to work at
 * all. Crown molding is defined relative to the ceiling, and room height is
 * {@code min(rand(5..10), max(width, depth))}, so a course measured from the floor drifts away from
 * the ceiling as rooms vary. A wall is only {@code height - 2} rows tall, i.e. <strong>3 to
 * 8</strong>; a course that resolves outside that range is simply not drawn, so a scheme carrying
 * both a plinth and a crown wants a {@code minHeight} that leaves plain wall between them rather
 * than relying on this clipping.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public record WallPatternEntry(List<PatternEntry> patterns, SizeGate gate) {

    /** An ungated treatment -- drawn whenever its scheme is rolled. */
    public WallPatternEntry(List<PatternEntry> patterns) {
        this(patterns, SizeGate.UNBOUNDED);
    }

    /**
     * The shape before the slot became a list: a single treatment of one type. Kept because most
     * walls really are one pattern, and because it leaves every pre-Aug-2026 call site meaning
     * exactly what it used to.
     */
    public WallPatternEntry(String type, List<CourseEntry> courses) {
        this(List.of(new PatternEntry(type, courses)), SizeGate.UNBOUNDED);
    }

    /** As above, with the slot gate the single treatment used to carry itself. */
    public WallPatternEntry(String type, List<CourseEntry> courses, SizeGate gate) {
        this(List.of(new PatternEntry(type, courses)), gate);
    }

    /**
     * One treatment. {@code type} is a plain string discriminator, same as
     * {@link FloorPatternEntry}'s; an unrecognized value draws nothing, the same graceful
     * degradation an unrecognized floor type gets.
     *
     * <ul>
     *   <li>{@code "courses"} &mdash; horizontal bands. Uses {@code courses}, an ordered list of
     *       {@link CourseEntry}.</li>
     *   <li>{@code "pilasters"} &mdash; evenly spaced vertical strips. Uses {@code block}, optional
     *       {@code baseBlock}/{@code capBlock} for the bottom and top rows with their own optional
     *       {@code baseProperties}/{@code capProperties}, and {@code spacing}
     *       (default {@value PilastersWallPatternProvider#DEFAULT_SPACING}).
     *       <p>The per-row property maps are the one place this schema differs from
     *       {@link CourseEntry}, which shares one map across its three block slots. A course's three
     *       slots are the same block family wanting the same state; a pilaster's plinth and capital
     *       are typically the same block at <em>opposite</em> values of a vertical property, so one
     *       map cannot describe a column at all.</p></li>
     *   <li>{@code "end_pilasters"} &mdash; one strip at each end of a wall instead of a rhythm.
     *       Same blocks; uses {@code inset} (default
     *       {@value PilastersWallPatternProvider#DEFAULT_INSET}) rather than {@code spacing}.</li>
     * </ul>
     *
     * <p>Fields are a flat superset across the types, exactly as {@code CeilingPatternEntry}'s
     * {@code SurfacePatternEntry} is, rather than a codec union per type. Writing a field the type
     * cannot use is a <strong>load error</strong> ({@link WallPatternEntry#validate}) and not a
     * silent no-op, because a wall that draws correctly while quietly ignoring a line the author
     * wrote is the hardest kind of authoring mistake to see.</p>
     */
    public record PatternEntry(String type, List<CourseEntry> courses,
                               Optional<String> block, Optional<String> baseBlock,
                               Optional<String> capBlock, int spacing, int projection,
                               CourseOrient orient, Map<String, String> properties,
                               Optional<Map<String, String>> baseProperties,
                               Optional<Map<String, String>> capProperties,
                               int inset, SizeGate gate) {

        /** A courses treatment, ungated -- the shape the whole slot used to have. */
        public PatternEntry(String type, List<CourseEntry> courses) {
            this(type, courses, Optional.empty(), Optional.empty(), Optional.empty(),
                    PilastersWallPatternProvider.DEFAULT_SPACING, 0, CourseOrient.NONE,
                    Map.of(), Optional.empty(), Optional.empty(),
                    PilastersWallPatternProvider.DEFAULT_INSET, SizeGate.UNBOUNDED);
        }

        /** A strip's shape before the base and cap could be propertied separately. */
        public PatternEntry(String type, List<CourseEntry> courses,
                            Optional<String> block, Optional<String> baseBlock,
                            Optional<String> capBlock, int spacing, int projection,
                            CourseOrient orient, Map<String, String> properties,
                            int inset, SizeGate gate) {
            this(type, courses, block, baseBlock, capBlock, spacing, projection, orient,
                    properties, Optional.empty(), Optional.empty(), inset, gate);
        }

        /** The base block, falling back to {@link #block} when unauthored. */
        public Optional<String> baseBlockOrBase() {
            return baseBlock.or(() -> block);
        }

        /** The cap block, falling back to {@link #block} when unauthored. */
        public Optional<String> capBlockOrBase() {
            return capBlock.or(() -> block);
        }

        /**
         * The base row's block properties, falling back to {@link #properties} when unauthored --
         * the same defaulting {@link #baseBlockOrBase} does, and for the same reason: absent means
         * "whatever the strip uses", not "no properties".
         */
        public Map<String, String> basePropertiesOrBase() {
            return baseProperties.orElse(properties);
        }

        /** See {@link #basePropertiesOrBase}. */
        public Map<String, String> capPropertiesOrBase() {
            return capProperties.orElse(properties);
        }

        /** Whether this entry is the {@code courses} type, compared the way the selector dispatches. */
        public boolean isCourses() {
            return COURSES.equals(type().trim().toLowerCase(Locale.ROOT));
        }

        /** Whether this entry is either strip type -- both need a {@code block} and neither takes courses. */
        public boolean isPilasters() {
            String name = type().trim().toLowerCase(Locale.ROOT);
            return PILASTERS.equals(name) || END_PILASTERS.equals(name);
        }

        /** Whether this entry is specifically the end-strip type. */
        public boolean isEndPilasters() {
            return END_PILASTERS.equals(type().trim().toLowerCase(Locale.ROOT));
        }

        public static final Codec<PatternEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(PatternEntry::type),
                Codecs.strictOptionalFieldOf(CourseEntry.CODEC.listOf(), "courses", List.of())
                        .forGetter(PatternEntry::courses),
                // Bare Optionals for the same reason CourseEntry's alternateBlock is: absent means
                // "fall back to another authored value", not "use this default block".
                Codec.STRING.optionalFieldOf("block").forGetter(PatternEntry::block),
                Codec.STRING.optionalFieldOf("baseBlock").forGetter(PatternEntry::baseBlock),
                Codec.STRING.optionalFieldOf("capBlock").forGetter(PatternEntry::capBlock),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "spacing",
                        PilastersWallPatternProvider.DEFAULT_SPACING).forGetter(PatternEntry::spacing),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, MAX_PROJECTION), "projection", 0)
                        .forGetter(PatternEntry::projection),
                Codecs.strictOptionalFieldOf(CourseOrient.CODEC, "orient", CourseOrient.NONE)
                        .forGetter(PatternEntry::orient),
                Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                        "properties", Map.of()).forGetter(PatternEntry::properties),
                // Bare Optionals, like baseBlock/capBlock: absent falls back to another AUTHORED
                // value rather than to a default this record invented.
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("baseProperties")
                        .forGetter(PatternEntry::baseProperties),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("capProperties")
                        .forGetter(PatternEntry::capProperties),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                                PilastersWallPatternProvider.DEFAULT_INSET)
                        .forGetter(PatternEntry::inset),
                SizeGate.MAP_CODEC.forGetter(PatternEntry::gate)
        ).apply(instance, PatternEntry::new));
    }

    /** The horizontal-band type. Lower-cased after trimming, matching how the selector dispatches. */
    public static final String COURSES = "courses";

    /** The evenly spaced vertical-strip type. See {@link #COURSES} for the comparison rule. */
    public static final String PILASTERS = "pilasters";

    /**
     * A strip at each end of a wall rather than a repeating rhythm &mdash; the paired corner, when
     * two adjacent walls both draw one. Listed alongside {@link #PILASTERS} it gives corner piers
     * with an even rhythm between them.
     */
    public static final String END_PILASTERS = "end_pilasters";

    /**
     * How a course's block should be turned to face, for blocks that have a {@code facing} property
     * (stairs, cornices, crown mouldings, sills).
     *
     * <p>This is the payoff of authoring in the wall's own {@code (u, v)} space: one authored course
     * comes out correctly oriented on all four walls, because each run applies its own facing.</p>
     */
    public enum CourseOrient implements StringRepresentable {
        /** Leave {@code facing} alone -- for full cubes, or when the author sets it explicitly. */
        NONE("none"),
        /**
         * Point {@code facing} at the wall. For a stair this puts the <em>full-height</em> half
         * against the wall and steps down into the room, which is a cornice. (Verified against the
         * 1.20.1 blockstate: {@code facing=east} renders at y=0 and the raised element spans
         * x 8-16, i.e. the solid half is on the {@code facing} side.)
         */
        TOWARD_WALL("toward_wall"),
        /** Point {@code facing} into the room -- the solid half toward the room, stepping up to the wall. */
        TOWARD_ROOM("toward_room");

        // NOTE FOR AUTHORS: the names describe where a VANILLA block's solid side ends up.
        // dungeonblocks' directional trim (cornice, crown moulding, sill) is modelled
        // facing-inverted relative to vanilla, so the same visual result needs the opposite value:
        // a cornice of vanilla stairs wants TOWARD_WALL, the equivalent dungeonblocks moulding wants
        // TOWARD_ROOM. Confirmed in game, not derivable from the block at runtime;
        // DatapackResourcesParseTest#projectedTrimIsOrientedForItsBlockFamily pins it.

        private final String name;

        CourseOrient(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static final Codec<CourseOrient> CODEC = StringRepresentable.fromEnum(CourseOrient::values);
    }

    /**
     * How a course's {@code alternateBlock} is mixed in against its {@code block}.
     *
     * <p>{@link #RANDOM} is the default and matches the floor's {@code base}/{@code alternateBase}
     * behaviour: an independent per-cell roll, which is what you want for breaking up a run of one
     * texture. {@link #STRICT} lays them down every other cell instead.</p>
     *
     * <p><strong>The distinction matters for a mirrored block pair.</strong> A family like
     * {@code left_large_stone_brick}/{@code right_large_stone_brick} is two halves of one wide
     * brick: mixed randomly you get adjacent left-left and right-right runs, and the halves stop
     * pairing up. That is a texture bug, not variety, and only {@code strict} avoids it. Anything
     * that is genuinely two <em>different</em> blocks wants {@code random}.</p>
     */
    public enum CourseAlternate implements StringRepresentable {
        /** Independent 45/55 roll per cell. */
        RANDOM("random"),
        /** Every other cell, starting from {@code block} at {@code u = 0}. */
        STRICT("strict");

        private final String name;

        CourseAlternate(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Failing, for the same reason {@link CourseAnchor#CODEC} is. */
        public static final Codec<CourseAlternate> CODEC =
                StringRepresentable.fromEnum(CourseAlternate::values);
    }

    /** Which edge of the wall a course counts its offset from. */
    public enum CourseAnchor implements StringRepresentable {
        BOTTOM("bottom"),
        TOP("top");

        private final String name;

        CourseAnchor(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /**
         * Deliberately a failing codec rather than a lenient string with a default. The set is
         * closed and tiny, so a value outside it is a typo &mdash; and silently reading
         * {@code "topp"} as BOTTOM would put crown molding on the floor with no error anywhere,
         * exactly the class of silent-default failure {@code Codecs#strictOptionalFieldOf} exists
         * to prevent.
         */
        public static final Codec<CourseAnchor> CODEC = StringRepresentable.fromEnum(CourseAnchor::values);
    }

    /**
     * One horizontal band. {@code offset} counts rows from {@code anchor}: {@code bottom}/0 is the
     * lowest wall row (a plinth), {@code top}/0 the highest (a crown). Two courses resolving to the
     * same row is not an error &mdash; later in the list wins, the same
     * ordering-is-execution-order convention used everywhere else here.
     *
     * <h2>alternateBlock and cornerBlock</h2>
     * <p>The same two knobs the floor has, and both <strong>default to {@code block}</strong>, so a
     * band authored with {@code block} alone behaves exactly as it did before they existed.</p>
     *
     * <p>{@code alternate} chooses <em>how</em> they mix &mdash; {@code "random"} (the default) or
     * {@code "strict"}, every other cell. A mirrored pair of block halves needs {@code strict}; see
     * {@link CourseAlternate}.</p>
     *
     * <p>{@code alternateBlock} is mixed in per cell at 45/55, the same roll
     * {@code FloorConfig}'s {@code base}/{@code alternateBase} pair gets &mdash; a band of a single
     * block reads as a machined stripe, which is right for polished trim and wrong for a rough
     * stone course. {@code cornerBlock} goes on the room's four corner columns, which is the quoin
     * every real masonry course has and the one place a band's rhythm is visibly interrupted.
     * Whether a given wall run owns those columns depends on the run and on whether the course
     * projects; {@code CoursesWallPatternProvider#ownsCorners} carries that rule so authors do not
     * have to.</p>
     *
     * <p>{@code properties} applies to all three: they are meant to be the same block family (a
     * course of stairs and its corner stair both want {@code half=top}), and a per-slot property map
     * would be a schema nobody needs yet.</p>
     */
    public record CourseEntry(String block, Optional<String> alternateBlock, Optional<String> cornerBlock,
                              CourseAnchor anchor, int offset,
                              int projection, CourseOrient orient, Map<String, String> properties,
                              CourseAlternate alternate, SizeGate gate) {

        /** Convenience for a flat, uniform, ungated course on the wall plane. */
        public CourseEntry(String block, CourseAnchor anchor, int offset) {
            this(block, Optional.empty(), Optional.empty(), anchor, offset, 0, CourseOrient.NONE,
                    Map.of(), CourseAlternate.RANDOM, SizeGate.UNBOUNDED);
        }

        /** An ungated course -- the shape before per-course gates existed. */
        public CourseEntry(String block, Optional<String> alternateBlock, Optional<String> cornerBlock,
                           CourseAnchor anchor, int offset, int projection, CourseOrient orient,
                           Map<String, String> properties) {
            this(block, alternateBlock, cornerBlock, anchor, offset, projection, orient, properties,
                    CourseAlternate.RANDOM, SizeGate.UNBOUNDED);
        }

        /** The alternate block id, falling back to {@link #block} when unauthored. */
        public String alternateBlockOrBase() {
            return alternateBlock.orElse(block);
        }

        /** The corner block id, falling back to {@link #block} when unauthored. */
        public String cornerBlockOrBase() {
            return cornerBlock.orElse(block);
        }

        public static final Codec<CourseEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("block").forGetter(CourseEntry::block),
                // Absent means "same as block", which is why these are bare Optionals rather than
                // strictOptionalFieldOf with a fallback: there is no default block to name here.
                Codec.STRING.optionalFieldOf("alternateBlock").forGetter(CourseEntry::alternateBlock),
                Codec.STRING.optionalFieldOf("cornerBlock").forGetter(CourseEntry::cornerBlock),
                // strictOptionalFieldOf, not DFU's own: optionalFieldOf cannot tell "absent" from
                // "present but malformed" and returns the default for both, so `"anchor": "topp"`
                // would silently read as BOTTOM and put the crown molding on the floor. That is the
                // whole failure mode Codecs.strictOptionalFieldOf exists to close.
                Codecs.strictOptionalFieldOf(CourseAnchor.CODEC, "anchor", CourseAnchor.BOTTOM)
                        .forGetter(CourseEntry::anchor),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "offset", 0)
                        .forGetter(CourseEntry::offset),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, MAX_PROJECTION), "projection", 0)
                        .forGetter(CourseEntry::projection),
                Codecs.strictOptionalFieldOf(CourseOrient.CODEC, "orient", CourseOrient.NONE)
                        .forGetter(CourseEntry::orient),
                Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                        "properties", Map.of()).forGetter(CourseEntry::properties),
                Codecs.strictOptionalFieldOf(CourseAlternate.CODEC, "alternate",
                        CourseAlternate.RANDOM).forGetter(CourseEntry::alternate),
                SizeGate.MAP_CODEC.forGetter(CourseEntry::gate)
        ).apply(instance, CourseEntry::new));
    }

    /**
     * How far a course may stand out from the wall. Capped low on purpose: a projection eats room
     * interior, and anything past one cell stops reading as trim and starts reading as a ledge --
     * which is a different feature with its own support and headroom questions.
     */
    public static final int MAX_PROJECTION = 2;

    /**
     * This treatment with only the courses a room of these dimensions actually draws.
     *
     * <h2>Why a course needs its own gate, when the slot already has one</h2>
     * <p>A slot gate is all-or-nothing, and a wall is routinely <em>partly</em> height-dependent: a
     * plinth belongs on every wall in the dungeon, while the crown above it needs headroom that a
     * 5-high room does not have. With only a slot gate that is two schemes again &mdash; exactly the
     * duplication element gates were added to remove &mdash; or a plinth that vanishes from short
     * rooms for no reason.</p>
     *
     * <p>Courses are already an independent ordered list, and one that resolves off the wall is
     * already dropped rather than clamped, so per-course gating is the same idea stated in the
     * author's terms rather than left to arithmetic. Note the two are not interchangeable: clipping
     * happens when a row falls outside the wall, whereas a top course in a 5-high room lands on the
     * <em>lintel</em> row and draws perfectly happily &mdash; it just looks cramped. Only a gate
     * expresses that.</p>
     *
     * <p>All courses gating out leaves an empty list, which {@code WallPatternSelector} already
     * renders as a plain wall.</p>
     */
    public WallPatternEntry forRoom(int width, int depth, int height) {
        List<PatternEntry> fitting = new ArrayList<>(patterns.size());
        boolean changed = false;
        for (PatternEntry pattern : patterns) {
            if (!pattern.gate().fits(width, depth, height)) {
                changed = true;
                continue;
            }
            List<CourseEntry> courses = pattern.courses().stream()
                    .filter(course -> course.gate().fits(width, depth, height))
                    .toList();
            if (courses.size() == pattern.courses().size()) {
                fitting.add(pattern);
                continue;
            }
            changed = true;
            // A courses pattern whose every band gated out contributes nothing; dropping it here
            // rather than carrying an empty one keeps "no patterns left" the single test for
            // "plain wall" in the selector.
            if (!courses.isEmpty()) {
                fitting.add(new PatternEntry(pattern.type(), courses, pattern.block(),
                        pattern.baseBlock(), pattern.capBlock(), pattern.spacing(),
                        pattern.projection(), pattern.orient(), pattern.properties(),
                        pattern.baseProperties(), pattern.capProperties(),
                        pattern.inset(), pattern.gate()));
            }
        }
        return changed ? new WallPatternEntry(fitting, gate) : this;
    }

    /**
     * {@code patterns} is <strong>required</strong>, unlike {@code CeilingPatternEntry}'s.
     *
     * <p>Not a style choice: this slot used to be a single typed entry
     * ({@code {"type": "courses", "courses": [...]}}), and a codec ignores keys it does not know.
     * Left optional, every unmigrated datapack would decode <em>cleanly</em> to a slot with no
     * patterns &mdash; a room whose authored trim silently vanished, with no error anywhere and
     * nothing in game to distinguish it from a plain wall. Requiring the key turns that into a load
     * failure naming {@code patterns}, which is the whole point of the strict codecs in this
     * package. An empty slot was never worth authoring anyway.</p>
     */
    public static final Codec<WallPatternEntry> CODEC = RecordCodecBuilder.<WallPatternEntry>create(
            instance -> instance.group(
                    PatternEntry.CODEC.listOf().fieldOf("patterns")
                            .forGetter(WallPatternEntry::patterns),
                    SizeGate.MAP_CODEC.forGetter(WallPatternEntry::gate)
            ).apply(instance, WallPatternEntry::new))
            .flatXmap(WallPatternEntry::validate, WallPatternEntry::validate);

    /**
     * Rejects a field the pattern's own type cannot act on.
     *
     * <p>Same rule and same reasoning as {@code CeilingPatternEntry#validate}: the fields are a flat
     * superset, so nothing stops an author writing {@code spacing} on a {@code courses} band or
     * {@code courses} on a {@code pilasters} strip. Ignoring either would produce a wall exactly as
     * correct as it was before the line was written &mdash; a silent nothing, with the pattern still
     * drawing, which is the failure mode hardest to spot in game.</p>
     *
     * <p>{@code projection}, {@code orient} and {@code properties} are deliberately <em>not</em>
     * checked: both types use all three, with the same meaning.</p>
     */
    private static DataResult<WallPatternEntry> validate(WallPatternEntry entry) {
        for (PatternEntry pattern : entry.patterns()) {
            if (!pattern.courses().isEmpty() && !pattern.isCourses()) {
                return DataResult.error(() -> "wall pattern '" + pattern.type()
                        + "': 'courses' is only meaningful on a 'courses' pattern");
            }
            if (pattern.block().isPresent() && pattern.isCourses()) {
                return DataResult.error(() -> "wall pattern 'courses': 'block' belongs on each entry"
                        + " of 'courses', not on the pattern itself");
            }
            if (pattern.isPilasters() && pattern.block().isEmpty()) {
                return DataResult.error(() -> "wall pattern 'pilasters': 'block' is required"
                        + " -- there is no default material for a pilaster");
            }
        }
        return DataResult.success(entry);
    }
}
