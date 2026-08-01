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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.List;

/**
 * A {@link RoomScheme}'s {@code wall} slot: the decorative treatment laid over a room's plain wall
 * block.
 *
 * <p>{@code type} is a plain string discriminator, same as {@link FloorPatternEntry}'s.
 * {@code "courses"} &mdash; horizontal bands &mdash; is the only type today; anything else means no
 * treatment, the same graceful degradation an unrecognized floor type gets.</p>
 *
 * <h2>Why courses first</h2>
 * <p>One provider covers plinth, chair rail, string course <em>and</em> crown molding, because all
 * four are the same thing at a different height. They are also the only wall pattern with no join
 * problem: a band sits at a constant {@code v}, so it runs continuously around all four walls no
 * matter how the corner columns are shared out.</p>
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
public record WallPatternEntry(String type, List<CourseEntry> courses) {

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
     */
    public record CourseEntry(String block, CourseAnchor anchor, int offset) {
        public static final Codec<CourseEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("block").forGetter(CourseEntry::block),
                // strictOptionalFieldOf, not DFU's own: optionalFieldOf cannot tell "absent" from
                // "present but malformed" and returns the default for both, so `"anchor": "topp"`
                // would silently read as BOTTOM and put the crown molding on the floor. That is the
                // whole failure mode Codecs.strictOptionalFieldOf exists to close.
                Codecs.strictOptionalFieldOf(CourseAnchor.CODEC, "anchor", CourseAnchor.BOTTOM)
                        .forGetter(CourseEntry::anchor),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "offset", 0)
                        .forGetter(CourseEntry::offset)
        ).apply(instance, CourseEntry::new));
    }

    public static final Codec<WallPatternEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(WallPatternEntry::type),
            Codecs.strictOptionalFieldOf(CourseEntry.CODEC.listOf(), "courses", List.of())
                    .forGetter(WallPatternEntry::courses)
    ).apply(instance, WallPatternEntry::new));
}
