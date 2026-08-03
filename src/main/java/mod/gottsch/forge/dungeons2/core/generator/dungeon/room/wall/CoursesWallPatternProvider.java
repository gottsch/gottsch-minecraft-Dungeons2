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

import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAlternate;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Horizontal bands across a wall. One band per {@link Course}, each filling a whole row of the
 * surface.
 *
 * <p>This one provider is plinth, baseboard, chair rail, string course and crown molding &mdash;
 * they differ only in which row they sit on, which is why it is the wall pattern worth building
 * first. It is also the only one with no join problem: a band is at a constant {@code v}, so it runs
 * unbroken around all four walls no matter how the corner columns are shared out between runs (see
 * {@code WallSurface}'s ownership rule). The band only has to <em>know</em> that rule to give a
 * corner its own block &mdash; see {@link #ownsCorners} &mdash; never to stay continuous.</p>
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
public class CoursesWallPatternProvider implements ISurfacePatternProvider, IProjectingPatternProvider {

    /**
     * One band, already resolved to concrete states.
     *
     * @param alternate  mixed in against {@code block} per cell at the same 45/55 split
     *                   {@code BasicFloorGenerator} uses for {@code base}/{@code alternateBase}.
     *                   Equal to {@code block} for a uniform band, which is the default.
     * @param corner     the block at the room's corner columns. Equal to {@code block} unless the
     *                   author wants a quoin. See {@link #ownsCorners}.
     * @param projection how far the band stands out from the wall, in cells. 0 sits flush in the
     *                   wall plane; 1 puts it in the interior cell in front, which is what turns a
     *                   flat band into a cornice.
     * @param orient     how to turn a block that has a {@code facing} property. Applied per wall run,
     *                   which is the whole reason patterns are authored in the wall's own space.
     */
    public record Course(BlockState block, BlockState alternate, BlockState corner,
                         CourseAnchor anchor, int offset, int projection, CourseOrient orient,
                         CourseAlternate alternateMode) {
        public Course {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(alternate, "alternate");
            Objects.requireNonNull(corner, "corner");
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(orient, "orient");
            Objects.requireNonNull(alternateMode, "alternateMode");
        }

        /** Randomly mixed, the default. */
        public Course(BlockState block, BlockState alternate, BlockState corner,
                      CourseAnchor anchor, int offset, int projection, CourseOrient orient) {
            this(block, alternate, corner, anchor, offset, projection, orient, CourseAlternate.RANDOM);
        }

        /** A uniform band: alternate and corner both fall back to {@code block}. */
        public Course(BlockState block, CourseAnchor anchor, int offset,
                      int projection, CourseOrient orient) {
            this(block, block, block, anchor, offset, projection, orient);
        }

        /** A flat, unoriented, uniform band on the wall plane. */
        public Course(BlockState block, CourseAnchor anchor, int offset) {
            this(block, anchor, offset, 0, CourseOrient.NONE);
        }
    }

    private final List<Course> courses;

    public CoursesWallPatternProvider(List<Course> courses) {
        this.courses = List.copyOf(Objects.requireNonNull(courses, "courses"));
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        return planFor(uSize, vSize, facing, 0, random);
    }

    @Override
    public Map<Integer, SurfacePlan> projectedPlans(int uSize, int vSize, Direction facing,
                                                    RandomSource random) {
        Map<Integer, SurfacePlan> plans = new LinkedHashMap<>();
        for (Course course : courses) {
            int depth = course.projection();
            if (depth > 0) {
                plans.computeIfAbsent(depth, d -> planFor(uSize, vSize, facing, d, random));
            }
        }
        return plans;
    }

    /** The band(s) at one projection depth. */
    private SurfacePlan planFor(int uSize, int vSize, Direction facing, int depth, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        boolean corners = ownsCorners(facing, depth);
        for (Course course : courses) {
            if (course.projection() != depth) {
                continue;
            }
            int v = rowFor(course, vSize);
            for (int u = 0; u < uSize; u++) {
                boolean atCorner = corners && (u == 0 || u == uSize - 1);
                BlockState state = atCorner ? course.corner() : mixed(course, u, random);
                // Out-of-range v is swallowed by set(), so no bounds check here on purpose.
                plan.set(u, v, oriented(state, course.orient(), facing));
            }
        }
        return plan;
    }

    /**
     * The band's block for one non-corner cell.
     *
     * <p>{@link CourseAlternate#RANDOM} is 45% {@code block} / 55% {@code alternate}, the same split
     * and the same order {@code BasicFloorGenerator} rolls the floor's base pair with. With the two
     * equal &mdash; the default when a datapack names only {@code block} &mdash; the roll is still
     * made but cannot change the result, so a uniform band stays uniform without a branch.</p>
     *
     * <p>{@link CourseAlternate#STRICT} lays them down every other cell instead, and
     * <strong>consumes no randomness</strong>. That is what a mirrored pair of block halves needs:
     * randomly mixed, {@code left_large_stone_brick} and {@code right_large_stone_brick} produce
     * adjacent left-left runs and stop reading as whole bricks.</p>
     *
     * <p>Parity is on {@code u}, which is per wall run. Runs are planned independently and {@code u}
     * always restarts at 0, so the sequence does not carry around a corner &mdash; the same
     * consequence of per-run authoring that {@code WallSurface} documents for asymmetric patterns.
     * For a two-cell repeat it is only visible where a run length is odd.</p>
     */
    private static BlockState mixed(Course course, int u, RandomSource random) {
        if (course.alternateMode() == CourseAlternate.STRICT) {
            return u % 2 == 0 ? course.block() : course.alternate();
        }
        return RandomHelper.checkProbability(random, 45) ? course.block() : course.alternate();
    }

    /**
     * Whether the cells at {@code u = 0} and {@code u = uSize - 1} of this run are the room's
     * <em>corner columns</em>, and so get {@link Course#corner} rather than the band block.
     *
     * <p>Which run owns a corner flips with depth, and both halves of that are
     * {@code WallSurface}'s rule seen from here. Flush in the wall plane the Z-edge runs span the
     * full width and own the four corner columns outright, so their ends are the corners and the
     * X-edge runs have none. One cell out, {@code WallSurface#emitProjected} cedes the ring's
     * corners the other way &mdash; a Z-edge run's projection of its own end column would land
     * inside the adjacent wall, so the X-edge runs supply the corners of every projected ring.</p>
     *
     * <p>Derived from {@code facing}'s axis rather than passed in because a provider is handed only
     * an extent and a facing; the two are equivalent, since exactly the Z-facing runs are the ones
     * that step in X.</p>
     */
    static boolean ownsCorners(Direction facing, int depth) {
        Direction.Axis owning = depth == 0 ? Direction.Axis.Z : Direction.Axis.X;
        return facing.getAxis() == owning;
    }

    /**
     * Applies the course's orientation for this wall run.
     *
     * <p>{@code facing} is the direction the wall's decorated face points, i.e. into the room. A
     * stair's full-height half sits on its own {@code facing} side, so a cornice &mdash; solid
     * against the wall, stepping down into the room &mdash; wants the <em>opposite</em> of the
     * surface's facing. That inversion is the single most error-prone thing here, which is why
     * {@link CourseOrient} names the intent ({@code toward_wall}) rather than making authors reason
     * about it.</p>
     */
    static BlockState oriented(BlockState state, CourseOrient orient, Direction facing) {
        Direction target = switch (orient) {
            case TOWARD_WALL -> facing.getOpposite();
            case TOWARD_ROOM -> facing;
            case NONE -> null;
        };
        return target == null
                ? state
                : BlockStateCodec.withProperties(state, Map.of("facing", target.getSerializedName()));
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
