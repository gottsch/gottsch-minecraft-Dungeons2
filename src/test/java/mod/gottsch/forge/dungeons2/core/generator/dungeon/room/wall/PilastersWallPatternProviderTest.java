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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.WallSurface;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout and rendering of the {@code pilasters} wall pattern.
 *
 * <p>The centring arithmetic gets most of the attention here because it is the part that decides
 * whether a room's four walls look deliberate, and it is not checkable by eye from the code.</p>
 *
 * @author Mark Gottschling on Aug 5, 2026
 */
class PilastersWallPatternProviderTest {

    /** A Z-facing run spans the room's full width and owns the corner columns. */
    private static final Direction LONG_WALL = Direction.SOUTH;
    /** An X-facing run covers the interior depth only. */
    private static final Direction SHORT_WALL = Direction.EAST;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PilastersWallPatternProvider flush(int spacing) {
        return new PilastersWallPatternProvider(
                Blocks.STONE_BRICKS.defaultBlockState(), spacing, 0, CourseOrient.NONE);
    }

    private static PilastersWallPatternProvider projecting(int spacing) {
        return new PilastersWallPatternProvider(
                Blocks.STONE_BRICKS.defaultBlockState(), spacing, 1, CourseOrient.NONE);
    }

    // ---------- centring ----------

    /**
     * The set of strips sits symmetric about the run's centre. This is what makes one authored
     * pattern read the same on a wall of any length, and it is why the layout is not simply counted
     * from {@code u = 0}.
     */
    @Test
    void stripsAreCentredOnTheRun() {
        // A flush long wall gives up its two corner columns, so the usable span is u 1..9.
        // Stride 4 fits three strips spanning 8, with nothing over: 1, 5, 9.
        assertEquals(List.of(1, 5, 9),
                PilastersWallPatternProvider.columns(11, 4, 0, LONG_WALL));
        // Same rule on a shorter wall: span 1..7, two strips spanning 4, one cell over each side.
        assertEquals(List.of(2, 6),
                PilastersWallPatternProvider.columns(9, 4, 0, LONG_WALL));
    }

    /** Symmetric means the gaps at the two ends differ by at most the one cell integer division costs. */
    @Test
    void theTwoEndGapsAreEqualOrOneApart() {
        for (int uSize = 1; uSize <= 40; uSize++) {
            for (int spacing = 1; spacing <= 6; spacing++) {
                List<Integer> columns = PilastersWallPatternProvider.columns(uSize, spacing, 0, LONG_WALL);
                if (columns.isEmpty()) {
                    continue;
                }
                int head = columns.get(0);
                int tail = uSize - 1 - columns.get(columns.size() - 1);
                assertTrue(Math.abs(head - tail) <= 1,
                        "uSize=" + uSize + " spacing=" + spacing + " -> " + columns
                                + " leaves " + head + " and " + tail);
            }
        }
    }

    /**
     * A projecting pattern is centred on the window it may actually occupy, not on the wall.
     *
     * <p>The long walls cede their corner columns to the short ones one cell out, so a projecting
     * layer on a 9-wide wall may only use {@code u} 2..6. Centred over the wall the strips would
     * land on 0, 4 and 8 and <em>two of the three</em> would be silently discarded by the emitter,
     * leaving a lone pilaster in the middle of the wall. This is the case the shared range on
     * {@link WallSurface} exists for.</p>
     */
    @Test
    void aProjectingPatternIsCentredOnItsUsableWindow() {
        for (int length = 7; length <= 24; length++) {
            List<Integer> projected = PilastersWallPatternProvider.columns(length, 4, 1, LONG_WALL);
            int lo = WallSurface.projectableFrom(true, 1);
            int hi = WallSurface.projectableTo(true, length, 1);
            for (int u : projected) {
                assertTrue(u >= lo && u <= hi, "projecting strip at " + u + " on a run of "
                        + length + " is outside the usable window " + lo + ".." + hi
                        + " and would be silently discarded");
            }
            assertFalse(projected.isEmpty(), "run of " + length + " has room for a strip");
        }
    }

    // ---------- end_pilasters ----------

    private static List<Integer> endColumns(int uSize, int projection, Direction facing, int inset) {
        return PilastersWallPatternProvider.columns(uSize, 4, projection, facing,
                PilastersWallPatternProvider.Layout.ENDS, inset);
    }

    /** The ends layout is exactly two strips, one at each end of the wall's usable span. */
    @Test
    void theEndsLayoutPlacesOneStripAtEachEnd() {
        for (int length = 6; length <= 24; length++) {
            List<Integer> columns = endColumns(length, 0, LONG_WALL, 0);
            assertEquals(2, columns.size(), "run length " + length + " gave " + columns);
            assertEquals(0, columns.get(0), "flush, a long wall reaches its own corner column");
            assertEquals(length - 1, columns.get(1));
        }
    }

    /**
     * Unlike the even layout, the ends layout DOES stand in the corner column -- that is the whole
     * point of it. The paired corner is this run's end strip beside the perpendicular wall's.
     */
    @Test
    void theEndsLayoutDoesStandInTheCornerColumn() {
        // Projecting: the short walls own the ring's corners, so theirs are at u = 0 / length - 1.
        List<Integer> shortWall = endColumns(11, 1, SHORT_WALL, 0);
        assertTrue(shortWall.contains(0), "the corner strip is the feature, not a defect");
        assertTrue(shortWall.contains(10));

        // The even layout in the same run refuses those cells, which is what keeps the two distinct.
        List<Integer> even = PilastersWallPatternProvider.columns(11, 4, 1, SHORT_WALL);
        assertFalse(even.contains(0));
        assertFalse(even.contains(10));
    }

    /** {@code inset} moves both end strips in from the wall's ends, symmetrically. */
    @Test
    void insetMovesBothEndStripsInSymmetrically() {
        assertEquals(List.of(2, 12), endColumns(15, 0, LONG_WALL, 2));
        assertEquals(List.of(1, 13), endColumns(15, 0, LONG_WALL, 1));
    }

    /** A wall too short for two inset strips collapses to one rather than crossing them over. */
    @Test
    void aWallTooShortForTwoEndStripsGetsOne() {
        assertEquals(1, endColumns(7, 0, LONG_WALL, 3).size());
        assertTrue(endColumns(6, 0, LONG_WALL, 4).isEmpty(), "and to none when even that will not fit");
    }

    /**
     * The run that owns the corner columns does not put a strip on one, so the rhythm stays even
     * all the way round the room.
     *
     * <p>This was wrong in the first cut and is only visible at some room sizes. Each run centres
     * its own strips in its own window; a projecting layer's corners belong to the SHORT walls, so
     * those would place a strip right in the corner while the long wall's own first strip sat two
     * cells away &mdash; a stutter at both ends of every long wall, against an otherwise even
     * spacing. It showed up on 11x11 and 15x15 and not on 9, 13 or 17, purely by how the division
     * fell, which is exactly the kind of thing that reads as "sometimes the generator does something
     * odd" rather than as a bug.</p>
     */
    @Test
    void theRunThatOwnsTheCornersDoesNotPutAStripOnOne() {
        // Projecting: the short walls own the ring's corners, at u = 0 and u = length - 1.
        for (int length = 5; length <= 20; length++) {
            List<Integer> shortWall = PilastersWallPatternProvider.columns(length, 4, 1, SHORT_WALL);
            assertFalse(shortWall.contains(0), "corner strip at u=0, run length " + length);
            assertFalse(shortWall.contains(length - 1),
                    "corner strip at u=" + (length - 1) + ", run length " + length);
        }
        // Flush: ownership flips, so it is the LONG walls that must give the corners up.
        for (int length = 5; length <= 20; length++) {
            List<Integer> longWall = PilastersWallPatternProvider.columns(length, 4, 0, LONG_WALL);
            assertFalse(longWall.contains(0), "corner strip at u=0, run length " + length);
            assertFalse(longWall.contains(length - 1),
                    "corner strip at u=" + (length - 1) + ", run length " + length);
        }
    }

    /**
     * The gaps between strips are even along a wall -- no pair sitting closer together than the
     * rest. Stated on the spacing rather than on corner indices because that is what the eye
     * actually reads, and it is what the corner rule above exists to protect.
     */
    @Test
    void theGapsBetweenStripsAreEven() {
        for (int length = 6; length <= 24; length++) {
            for (int projection : new int[] {0, 1}) {
                for (Direction facing : new Direction[] {LONG_WALL, SHORT_WALL}) {
                    List<Integer> columns =
                            PilastersWallPatternProvider.columns(length, 4, projection, facing);
                    for (int i = 2; i < columns.size(); i++) {
                        assertEquals(columns.get(1) - columns.get(0),
                                columns.get(i) - columns.get(i - 1),
                                "uneven gap in " + columns + " (length " + length
                                        + ", projection " + projection + ", " + facing + ")");
                    }
                }
            }
        }
    }

    /** A degenerate run (a room too thin to have this wall at all) draws nothing rather than throwing. */
    @Test
    void aRunWithNoLengthDrawsNothing() {
        assertTrue(PilastersWallPatternProvider.columns(0, 4, 0, LONG_WALL).isEmpty());
        assertEquals(0, flush(4).plan(0, 5, LONG_WALL).markedCells());
    }

    // ---------- rendering ----------

    /** A flush pattern draws in the wall plane and projects nothing. */
    @Test
    void aFlushPatternDrawsInTheWallPlane() {
        PilastersWallPatternProvider provider = flush(4);
        SurfacePlan plan = provider.plan(9, 5, LONG_WALL);

        assertTrue(provider.projectedPlans(9, 5, LONG_WALL).isEmpty(), "nothing stands out");
        for (int u : PilastersWallPatternProvider.columns(9, 4, 0, LONG_WALL)) {
            for (int v = 0; v < 5; v++) {
                assertNotNull(plan.get(u, v), "strip cell (" + u + "," + v + ")");
            }
        }
        assertNull(plan.get(1, 0), "the bay between strips is left to the wall block");
    }

    /** A projecting pattern is absent from the wall plane and lives entirely in its own layer. */
    @Test
    void aProjectingPatternLeavesTheWallPlaneAlone() {
        PilastersWallPatternProvider provider = projecting(4);

        assertEquals(0, provider.plan(11, 5, LONG_WALL).markedCells());
        Map<Integer, SurfacePlan> layers = provider.projectedPlans(11, 5, LONG_WALL);
        assertEquals(1, layers.size());
        assertTrue(layers.containsKey(1), "one cell out from the wall");
        assertTrue(layers.get(1).markedCells() > 0);
    }

    /** Strips run the full height of the wall -- a pilaster is floor to cornice or it is not one. */
    @Test
    void aStripRunsTheFullHeight() {
        SurfacePlan plan = flush(4).plan(11, 6, LONG_WALL);
        int u = PilastersWallPatternProvider.columns(11, 4, 0, LONG_WALL).get(0);
        for (int v = 0; v < 6; v++) {
            assertNotNull(plan.get(u, v), "row " + v + " of the strip");
        }
    }

    /** base and cap take the end rows; the shaft block fills between them. */
    @Test
    void baseAndCapTakeTheEndRows() {
        BlockState shaft = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState base = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState cap = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        SurfacePlan plan = new PilastersWallPatternProvider(shaft, base, cap, 4, 0, CourseOrient.NONE)
                .plan(11, 5, LONG_WALL);

        int u = PilastersWallPatternProvider.columns(11, 4, 0, LONG_WALL).get(0);
        assertEquals(base, plan.get(u, 0), "the plinth row");
        assertEquals(cap, plan.get(u, 4), "the capital row");
        assertEquals(shaft, plan.get(u, 2), "the shaft between them");
    }

    /**
     * With base and cap unauthored they fall back to the shaft block, so a strip written with
     * {@code block} alone is uniform -- the same defaulting a course's alternate/corner blocks have.
     */
    @Test
    void anUnauthoredBaseAndCapLeaveTheStripUniform() {
        SurfacePlan plan = flush(4).plan(11, 5, LONG_WALL);
        int u = PilastersWallPatternProvider.columns(11, 4, 0, LONG_WALL).get(0);
        assertEquals(plan.get(u, 2), plan.get(u, 0));
        assertEquals(plan.get(u, 2), plan.get(u, 4));
    }
}
