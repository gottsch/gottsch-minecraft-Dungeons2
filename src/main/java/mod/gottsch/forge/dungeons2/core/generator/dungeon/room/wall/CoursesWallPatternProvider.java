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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * Horizontal bands across a wall. One band per {@link Course}, each filling a whole row of the
 * surface.
 *
 * <p>This one provider is plinth, baseboard, chair rail, string course and crown molding &mdash;
 * they differ only in which row they sit on, which is why it is the wall pattern worth building
 * first. It is also the only one with no join problem: a band is at a constant {@code v}, so it runs
 * unbroken around all four walls regardless of how the corner columns are shared out between runs
 * (see {@code WallSurface}'s ownership rule).</p>
 *
 * <p>{@link Course#anchor} is what makes a crown a crown. Rooms vary in height, so a course measured
 * from the floor drifts away from the ceiling; {@link CourseAnchor#TOP} counts down from the top row
 * instead. A course that resolves outside the wall is dropped rather than clamped &mdash;
 * {@link SurfacePlan#set} ignores out-of-range writes precisely so this stays arithmetic. Dropping
 * is the right degradation: a crown molding squashed onto the plinth row of a short room is worse
 * than no crown at all. Keeping a room tall enough for both is the scheme's {@code minHeight} job,
 * not this class's.</p>
 *
 * <p>Facing is ignored: a band of full cubes has no orientation. A stairs-based cornice would use
 * it, and is the obvious next provider.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public class CoursesWallPatternProvider implements ISurfacePatternProvider {

    /** One band, already resolved to a concrete state. */
    public record Course(BlockState block, CourseAnchor anchor, int offset) {
        public Course {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(anchor, "anchor");
        }
    }

    private final List<Course> courses;

    public CoursesWallPatternProvider(List<Course> courses) {
        this.courses = List.copyOf(Objects.requireNonNull(courses, "courses"));
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        for (Course course : courses) {
            int v = rowFor(course, vSize);
            for (int u = 0; u < uSize; u++) {
                // Out-of-range v is swallowed by set(), so no bounds check here on purpose.
                plan.set(u, v, course.block());
            }
        }
        return plan;
    }

    /**
     * The row a course lands on. Pure arithmetic, package-visible for direct unit testing: may
     * return a value outside {@code [0, vSize)}, which the plan then ignores.
     */
    static int rowFor(Course course, int vSize) {
        return course.anchor() == CourseAnchor.TOP
                ? vSize - 1 - course.offset()
                : course.offset();
    }
}
