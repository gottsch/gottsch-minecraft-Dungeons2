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

import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.WallSurface;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evenly spaced vertical strips up a wall &mdash; pilasters when they project, panelling when they
 * do not. The vertical counterpart of {@link CoursesWallPatternProvider}, and the second wall
 * pattern type.
 *
 * <h2>Two types, one provider</h2>
 * <p>{@code pilasters} ({@link Layout#EVEN}) is the repeating rhythm; {@code end_pilasters}
 * ({@link Layout#ENDS}) puts a single strip at each end of a wall. They differ in <strong>which
 * columns they choose and nothing else</strong>, which is why they share a class: a strip's base,
 * cap, orientation and its whole-column drop at a doorway must not drift between them.</p>
 *
 * <p>Listing both in a scheme's {@code wall} slot is what produces a <strong>paired corner</strong>
 * &mdash; the wall's end strip beside the perpendicular wall's, reading as a clustered pier &mdash;
 * with an even rhythm in between. That look first appeared as a bug: {@code EVEN} used to be
 * centred over the whole run, so on room sizes where the arithmetic happened to push a strip to a
 * corner you got the pair, and on sizes where it did not you got nothing. A 15x15 room had it and a
 * 13x13 room did not. Mark liked the look (2026-08-06), so it became a type an author asks for
 * rather than a property of the room's dimensions.</p>
 *
 * <h2>Why layout is centred rather than counted from u = 0</h2>
 * <p>A room's four runs are not the same length: the Z-edge runs span the full {@code width} and the
 * X-edge runs only {@code depth - 2} (see {@code WallSurface}'s corner-ownership rule). Striding from
 * {@code u = 0} would therefore give each wall a different phase, and a room whose pilasters march
 * out of step from wall to wall reads as an accident rather than as architecture.</p>
 *
 * <p>So the whole set is centred on the run: the strips are laid out at {@code spacing} intervals and
 * the block of them is then offset so it sits symmetric about the run's midpoint. {@code WallSurface}
 * documents that runs are symmetric about their own centre precisely so a centred pattern comes out
 * right on all four walls, and names "evenly spaced pilasters" as the case.</p>
 *
 * <p>A consequence worth knowing: an {@link Layout#EVEN} pattern's strips do <strong>not</strong>
 * land on the room's corners, deliberately. Only one of the four runs can reach a given corner
 * column and which one flips with depth, so a run that placed there would sit two cells from the
 * perpendicular wall's first strip and break an otherwise even rhythm &mdash; at some room sizes and
 * not others. {@link Layout#ENDS} is how an author asks for that corner strip on purpose.</p>
 *
 * <h2>base and cap</h2>
 * <p>{@code baseBlock} and {@code capBlock} take the strip's bottom and top rows, which is what turns
 * a plain stripe into a column with a plinth and a capital. Both default to the strip block, so a
 * pilaster authored with {@code block} alone is a uniform strip. {@code dungeonblocks} ships
 * {@code *_pillar_block} / {@code *_pillar_base_block} families for exactly this.</p>
 *
 * <p>On a wall only {@code height - 2} rows tall &mdash; 3 at the low end &mdash; a base and a cap
 * leave a single shaft row between them. That is the scheme's {@code minHeight} to judge, not this
 * class's; the strip still draws.</p>
 *
 * @author Mark Gottschling on Aug 5, 2026
 */
public class PilastersWallPatternProvider implements ISurfacePatternProvider, IProjectingPatternProvider {

    /**
     * Cells between one strip and the next when a datapack does not say. Four gives a bay wide
     * enough to read as a bay on the shortest walls a room actually has, without the strips closing
     * up into panelling on a long one.
     */
    public static final int DEFAULT_SPACING = 4;

    /**
     * How a pattern chooses which columns to stand in. The two shipped pilaster types differ in
     * <strong>this and nothing else</strong> &mdash; a strip is rendered identically either way, so
     * base/cap, orientation and the doorway rule cannot drift between them.
     */
    public enum Layout {
        /** Evenly spaced at {@code spacing}, centred, never in a corner column. {@code pilasters}. */
        EVEN,
        /** One at each end of the wall, {@code inset} cells in. {@code end_pilasters}. */
        ENDS
    }

    /** How far an {@link Layout#ENDS} strip sits from the end of its wall. */
    public static final int DEFAULT_INSET = 0;

    private final BlockState shaft;
    private final BlockState base;
    private final BlockState cap;
    private final int spacing;
    private final int projection;
    private final CourseOrient orient;
    private final Layout layout;
    private final int inset;

    public PilastersWallPatternProvider(BlockState shaft, BlockState base, BlockState cap,
                                        int spacing, int projection, CourseOrient orient,
                                        Layout layout, int inset) {
        this.shaft = Objects.requireNonNull(shaft, "shaft");
        this.base = Objects.requireNonNull(base, "base");
        this.cap = Objects.requireNonNull(cap, "cap");
        this.spacing = Math.max(1, spacing);
        this.projection = projection;
        this.orient = Objects.requireNonNull(orient, "orient");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.inset = inset;
    }

    /** The evenly spaced layout, which is what {@code pilasters} means. */
    public PilastersWallPatternProvider(BlockState shaft, BlockState base, BlockState cap,
                                        int spacing, int projection, CourseOrient orient) {
        this(shaft, base, cap, spacing, projection, orient, Layout.EVEN, DEFAULT_INSET);
    }

    /** A uniform strip: base and cap both fall back to the shaft block. */
    public PilastersWallPatternProvider(BlockState shaft, int spacing, int projection,
                                        CourseOrient orient) {
        this(shaft, shaft, shaft, spacing, projection, orient);
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        return projection == 0 ? planStrips(uSize, vSize, facing) : SurfacePlan.of(uSize, vSize);
    }

    @Override
    public Map<Integer, SurfacePlan> projectedPlans(int uSize, int vSize, Direction facing,
                                                    RandomSource random) {
        return projection == 0
                ? Map.of()
                : Map.of(projection, planStrips(uSize, vSize, facing));
    }

    /**
     * The strips, full height, with the bottom and top rows taking the base and cap blocks. A wall
     * one row tall collapses the two onto each other; the cap wins, being written second, which is
     * the same later-wins rule the pattern list itself follows.
     */
    private SurfacePlan planStrips(int uSize, int vSize, Direction facing) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        BlockState shaftState = CoursesWallPatternProvider.oriented(shaft, orient, facing);
        BlockState baseState = CoursesWallPatternProvider.oriented(base, orient, facing);
        BlockState capState = CoursesWallPatternProvider.oriented(cap, orient, facing);
        for (int u : columns(uSize, spacing, projection, facing, layout, inset)) {
            for (int v = 0; v < vSize; v++) {
                plan.set(u, v, shaftState);
            }
            plan.set(u, 0, baseState);
            plan.set(u, vSize - 1, capState);
        }
        return plan;
    }

    /**
     * The {@code u} positions of the strips on a run: as many as fit at {@code spacing} intervals,
     * centred within the span this pattern may actually occupy.
     *
     * <p><strong>The span is not always the whole run.</strong> A projecting layer cedes the corner
     * columns between runs, so its usable window is narrower than the wall (see
     * {@code WallSurface#projectableFrom}). Centring over the run and letting the emitter discard
     * what falls outside would silently delete exactly the outermost strips &mdash; on a 9-wide wall
     * at spacing 4 that is two of three &mdash; and the wall would come out with a lone pilaster in
     * the middle. Centring over the window keeps them all.</p>
     *
     * <p>Package-visible and pure so the centring can be unit-tested directly: it is the one piece
     * of arithmetic here a reader cannot check by eye, and the one that makes all four walls agree.
     * The remainder is split with integer division, so where the leftover is odd the set sits half a
     * cell toward {@code u = 0} &mdash; unavoidable on a discrete grid, at most one cell, and every
     * other case is exactly symmetric.</p>
     */
    /** The {@link Layout#EVEN} columns -- the repeating rhythm, and the common case. */
    static List<Integer> columns(int uSize, int spacing, int projection, Direction facing) {
        return columns(uSize, spacing, projection, facing, Layout.EVEN, DEFAULT_INSET);
    }

    static List<Integer> columns(int uSize, int spacing, int projection, Direction facing,
                                 Layout layout, int inset) {
        boolean spansCorners = CoursesWallPatternProvider.ownsCorners(facing, 0);
        int lo = Math.max(0, WallSurface.projectableFrom(spansCorners && projection > 0, projection));
        int hi = Math.min(uSize - 1,
                projection > 0
                        ? WallSurface.projectableTo(spansCorners, uSize, projection)
                        : uSize - 1);
        if (lo > hi) {
            return List.of();
        }
        if (layout == Layout.ENDS) {
            return ends(lo, hi, inset);
        }
        // EVEN never stands in a corner column, so the rhythm stays even all the way round the room.
        // Only ONE of the four runs owns a given corner at a given depth, and which one flips with
        // depth (flush, the long walls own them; projecting, the short walls do), so the question is
        // only ever asked of that run -- the other cannot reach the cell at all.
        boolean ownsCorners = CoursesWallPatternProvider.ownsCorners(facing, projection);
        return centred(ownsCorners ? lo + 1 : lo, ownsCorners ? hi - 1 : hi, Math.max(1, spacing));
    }

    /** One strip at each end of the run's usable span, {@code inset} cells in. */
    private static List<Integer> ends(int lo, int hi, int inset) {
        int first = lo + Math.max(0, inset);
        int last = hi - Math.max(0, inset);
        if (first > last) {
            return List.of();
        }
        return first == last ? List.of(first) : List.of(first, last);
    }

    /** As many strips as fit at an exact stride, centred in the span. */
    private static List<Integer> centred(int lo, int hi, int stride) {
        if (lo > hi) {
            return List.of();
        }
        int span = hi - lo;
        int count = span / stride + 1;
        int used = (count - 1) * stride;
        int start = lo + (span - used) / 2;
        List<Integer> columns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            columns.add(start + i * stride);
        }
        return columns;
    }
}
