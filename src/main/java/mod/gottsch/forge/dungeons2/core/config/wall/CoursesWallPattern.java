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
package mod.gottsch.forge.dungeons2.core.config.wall;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.CoursesWallPatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal bands, each authored as a {@link CourseEntry}.
 *
 * <p>The nested-list type, and the reason wall was said to sit "one level deeper" than floor: this
 * pattern's own config holds a list of whole records. {@code CourseEntry} is unchanged -- it was
 * already its own closed record with its own gate, so the registry reaches it without touching it.
 * </p>
 *
 * <p><strong>{@code courses} is required</strong>, which retires one of the hand-written rules in
 * {@code WallPatternEntry.validate}: "courses is only meaningful on a courses pattern" is now a
 * stray key on any other type, and a courses pattern without it will not decode.</p>
 *
 * <p>An EMPTY list still degrades to a plain wall rather than failing the load. That is unchanged
 * behaviour and deliberately so -- making it an error would be a new rule, not part of this
 * migration, and nothing has said whether an empty list is a legitimate way to author "no bands".</p>
 */
public record CoursesWallPattern(List<CourseEntry> courses) implements WallPattern {

    public static final String NAME = "courses";

    public static final MapCodec<CoursesWallPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    CourseEntry.CODEC.listOf().fieldOf("courses")
                            .forGetter(CoursesWallPattern::courses)
            ).apply(instance, CoursesWallPattern::new)));

    /** See {@code WallPatternEntry.CourseEntry#withRoles}. */
    @Override
    public WallPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        List<mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseEntry> resolved =
                mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseEntry
                        .withRoles(courses, resolver);
        return resolved == courses ? this : new CoursesWallPattern(resolved);
    }

    @Override
    public MapCodec<? extends WallPattern> codec() {
        return CODEC;
    }

    @Override
    public ISurfacePatternProvider provider() {
        if (courses.isEmpty()) {
            return null;
        }
        List<CoursesWallPatternProvider.Course> built = new ArrayList<>(courses.size());
        for (CourseEntry course : courses) {
            // Author-supplied properties (half=top for an upside-down cornice, and anything else
            // the block needs) are baked in here; `orient` is applied later, per wall run, since it
            // is the one property that differs between the four walls.
            BlockState block = WallPattern.state(course.block(), course.properties());
            // An unresolvable alternate/corner degrades the whole entry too, not just its own
            // cells: a band that silently loses its quoins is a subtler wrong than a plain wall.
            BlockState alternate = WallPattern.state(course.alternateBlockOrBase(), course.properties());
            BlockState corner = WallPattern.state(course.cornerBlockOrBase(), course.properties());
            if (block == null || alternate == null || corner == null) {
                return null;
            }
            built.add(new CoursesWallPatternProvider.Course(block, alternate, corner,
                    course.anchor(), course.offset(), course.projection(), course.orient(),
                    course.alternate()));
        }
        return new CoursesWallPatternProvider(built);
    }
}
