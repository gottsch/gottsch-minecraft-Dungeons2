package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Course arithmetic. The interesting cases are all about <em>height</em>: a wall is only 3 to 8 rows
 * tall, so where a course lands, and what happens when it lands nowhere, is the whole design.
 */
class CoursesWallPatternProviderTest {

    private static BlockState andesite;
    private static BlockState chiseled;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        andesite = Blocks.POLISHED_ANDESITE.defaultBlockState();
        chiseled = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    }

    private static CoursesWallPatternProvider provider(CoursesWallPatternProvider.Course... courses) {
        return new CoursesWallPatternProvider(List.of(courses));
    }

    private static CoursesWallPatternProvider.Course course(BlockState block, CourseAnchor anchor, int offset) {
        return new CoursesWallPatternProvider.Course(block, anchor, offset);
    }

    /** A flat plinth whose alternate and corner blocks differ from its base. */
    private static CoursesWallPatternProvider.Course mixedPlinth(BlockState block, BlockState alternate,
                                                                 BlockState corner) {
        return new CoursesWallPatternProvider.Course(block, alternate, corner,
                CourseAnchor.BOTTOM, 0, 0, CourseOrient.NONE);
    }

    @Test
    void aBottomAnchoredCourseSitsOnTheLowestRow() {
        SurfacePlan plan = provider(course(andesite, CourseAnchor.BOTTOM, 0))
                .plan(6, 5, Direction.SOUTH);
        for (int u = 0; u < 6; u++) {
            assertSame(andesite, plan.get(u, 0), "plinth should fill row 0");
            assertNull(plan.get(u, 1), "nothing above it");
        }
    }

    /**
     * The reason TOP exists. Room height still varies across 5..10 &mdash; which band of that a
     * given footprint reaches is #51's taper &mdash; so a crown measured from the floor would drift
     * away from the ceiling; measured from the top it stays put.
     */
    @Test
    void aTopAnchoredCourseTracksTheCeilingAcrossRoomHeights() {
        for (int vSize = 3; vSize <= 8; vSize++) {
            SurfacePlan plan = provider(course(chiseled, CourseAnchor.TOP, 0))
                    .plan(4, vSize, Direction.SOUTH);
            assertSame(chiseled, plan.get(0, vSize - 1),
                    "crown should be on the top row for a wall " + vSize + " tall");
            assertEquals(4, plan.markedCells(), "exactly one row, at vSize=" + vSize);
        }
    }

    @Test
    void offsetCountsRowsAwayFromTheAnchor() {
        SurfacePlan plan = provider(
                course(andesite, CourseAnchor.BOTTOM, 1),
                course(chiseled, CourseAnchor.TOP, 2)).plan(3, 8, Direction.SOUTH);

        assertSame(andesite, plan.get(0, 1));
        assertSame(chiseled, plan.get(0, 5)); // 8 - 1 - 2
        assertNull(plan.get(0, 0));
    }

    /** A course fills the entire run, which is what makes a band continuous around the room. */
    @Test
    void aCourseSpansTheWholeRun() {
        SurfacePlan plan = provider(course(andesite, CourseAnchor.BOTTOM, 0))
                .plan(9, 5, Direction.SOUTH);
        assertEquals(9, plan.markedCells());
    }

    /**
     * A course that resolves off the wall is dropped, not clamped. Clamping would squash a crown
     * molding onto the plinth row of a short room, which reads worse than having no crown at all --
     * keeping a room tall enough for both is the scheme's minHeight job.
     */
    @Test
    void aCourseOffTheWallIsDroppedRatherThanClamped() {
        SurfacePlan plan = provider(course(chiseled, CourseAnchor.BOTTOM, 7))
                .plan(4, 3, Direction.SOUTH);
        assertEquals(0, plan.markedCells());

        SurfacePlan fromTop = provider(course(chiseled, CourseAnchor.TOP, 7))
                .plan(4, 3, Direction.SOUTH);
        assertEquals(0, fromTop.markedCells(), "a top offset past the bottom must not wrap");
    }

    /** Two courses on the same row: later in the list wins, no error. */
    @Test
    void aLaterCourseWinsTheSameRow() {
        SurfacePlan plan = provider(
                course(andesite, CourseAnchor.BOTTOM, 0),
                course(chiseled, CourseAnchor.BOTTOM, 0)).plan(3, 5, Direction.SOUTH);
        assertSame(chiseled, plan.get(0, 0));
        assertEquals(3, plan.markedCells());
    }

    /** Bands are full cubes, so all four walls get the identical plan. */
    @Test
    void facingDoesNotChangeTheResult() {
        CoursesWallPatternProvider p = provider(course(andesite, CourseAnchor.TOP, 0));
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST}) {
            assertSame(andesite, p.plan(4, 5, facing).get(0, 4));
        }
    }

    // ---------- projection ----------

    private static CoursesWallPatternProvider.Course projecting(
            BlockState block, CourseAnchor anchor, int offset, int depth, CourseOrient orient) {
        return new CoursesWallPatternProvider.Course(block, anchor, offset, depth, orient);
    }

    /** A projecting course must not appear in the wall plane -- that is the whole point of it. */
    @Test
    void aProjectingCourseIsAbsentFromTheWallPlane() {
        CoursesWallPatternProvider p = provider(
                projecting(andesite, CourseAnchor.TOP, 0, 1, CourseOrient.NONE));
        assertEquals(0, p.plan(5, 5, Direction.SOUTH).markedCells());
        assertEquals(5, p.projectedPlans(5, 5, Direction.SOUTH).get(1).markedCells());
    }

    /** Flat and projecting courses in one pattern land in their own layers. */
    @Test
    void flatAndProjectingCoursesSeparateByDepth() {
        CoursesWallPatternProvider p = provider(
                course(andesite, CourseAnchor.BOTTOM, 0),
                projecting(chiseled, CourseAnchor.TOP, 0, 1, CourseOrient.NONE));

        SurfacePlan flat = p.plan(4, 5, Direction.SOUTH);
        assertEquals(4, flat.markedCells());
        assertSame(andesite, flat.get(0, 0));

        Map<Integer, SurfacePlan> projected = p.projectedPlans(4, 5, Direction.SOUTH);
        assertEquals(Set.of(1), projected.keySet());
        assertSame(chiseled, projected.get(1).get(0, 4));
    }

    @Test
    void nothingProjectsWhenEveryCourseIsFlat() {
        assertTrue(provider(course(andesite, CourseAnchor.TOP, 0))
                .projectedPlans(5, 5, Direction.SOUTH).isEmpty());
    }

    /**
     * The orientation inversion, stated directly because it is the easiest thing here to get
     * backwards. A stair's full-height half is on its own `facing` side (verified against the
     * 1.20.1 blockstate: facing=east renders at y=0, and the raised element spans x 8-16). A
     * cornice wants that solid half against the wall, so `toward_wall` must resolve to the OPPOSITE
     * of the surface facing, which points into the room.
     */
    @Test
    void towardWallOrientsTheStairsSolidHalfAgainstTheWall() {
        BlockState stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        for (Direction surfaceFacing : new Direction[]{Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST}) {
            BlockState oriented = CoursesWallPatternProvider.oriented(
                    stairs, CourseOrient.TOWARD_WALL, surfaceFacing);
            assertEquals(surfaceFacing.getOpposite(),
                    oriented.getValue(net.minecraft.world.level.block.StairBlock.FACING),
                    "cornice on a wall facing " + surfaceFacing + " must face back at the wall");
        }
    }

    @Test
    void towardRoomOrientsWithTheSurface() {
        BlockState stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        BlockState oriented = CoursesWallPatternProvider.oriented(
                stairs, CourseOrient.TOWARD_ROOM, Direction.SOUTH);
        assertEquals(Direction.SOUTH,
                oriented.getValue(net.minecraft.world.level.block.StairBlock.FACING));
    }

    /** A block with no facing property is left alone rather than failing. */
    @Test
    void orientingAFullCubeIsANoOp() {
        assertSame(andesite, CoursesWallPatternProvider.oriented(
                andesite, CourseOrient.TOWARD_WALL, Direction.SOUTH));
    }

    // ---- alternate / corner blocks -------------------------------------------------------------

    /**
     * The default, and the reason nothing else in this file had to change: with all three slots the
     * same block the 45/55 roll still happens but cannot alter the result.
     */
    @Test
    void aUniformCourseIsUnaffectedByTheRoll() {
        SurfacePlan plan = provider(course(andesite, CourseAnchor.BOTTOM, 0))
                .plan(9, 5, Direction.SOUTH, RandomSource.create(12345L));
        for (int u = 0; u < 9; u++) {
            assertSame(andesite, plan.get(u, 0));
        }
    }

    /** Both blocks of the pair appear along a long enough band -- it is a mix, not a swap. */
    @Test
    void anAlternateBlockIsMixedInAlongTheBand() {
        SurfacePlan plan = provider(mixedPlinth(andesite, chiseled, andesite))
                .plan(64, 5, Direction.SOUTH, RandomSource.create(9L));

        Set<BlockState> seen = new java.util.HashSet<>();
        for (int u = 1; u < 63; u++) {
            seen.add(plan.get(u, 0));
        }
        assertEquals(Set.of(andesite, chiseled), seen,
                "a 64-cell band should carry both blocks of the pair");
    }

    /**
     * A Z-facing run spans the full width and owns the corner columns, so its two end cells are the
     * room's corners. Everything between is the band.
     */
    @Test
    void aZEdgeRunPutsTheCornerBlockOnItsEnds() {
        SurfacePlan plan = provider(mixedPlinth(andesite, andesite, chiseled))
                .plan(7, 5, Direction.SOUTH, RandomSource.create(3L));

        assertSame(chiseled, plan.get(0, 0));
        assertSame(chiseled, plan.get(6, 0));
        for (int u = 1; u < 6; u++) {
            assertSame(andesite, plan.get(u, 0), "u=" + u + " is band, not corner");
        }
    }

    /**
     * The other half of {@code WallSurface}'s ownership rule: an X-facing run covers interior depth
     * only, so it has no corner columns and the corner block never appears on it. Getting this wrong
     * would put a quoin one cell in from each corner, on the wrong wall.
     */
    @Test
    void anXEdgeRunHasNoCornersInTheWallPlane() {
        SurfacePlan plan = provider(mixedPlinth(andesite, andesite, chiseled))
                .plan(7, 5, Direction.EAST, RandomSource.create(3L));

        for (int u = 0; u < 7; u++) {
            assertSame(andesite, plan.get(u, 0), "an X-edge run owns no corner at u=" + u);
        }
    }

    /**
     * Ownership flips one cell out: {@code WallSurface#emitProjected} cedes the projected ring's
     * corners to the X-edge runs, because a Z-edge run's projection of its own end column lands
     * inside the adjacent wall.
     */
    @Test
    void aProjectedRingTakesItsCornersFromTheXEdgeRuns() {
        CoursesWallPatternProvider.Course cornice = new CoursesWallPatternProvider.Course(
                andesite, andesite, chiseled, CourseAnchor.TOP, 0, 1, CourseOrient.NONE);

        SurfacePlan xRun = provider(cornice).projectedPlans(7, 5, Direction.EAST, RandomSource.create(3L)).get(1);
        assertSame(chiseled, xRun.get(0, 4));
        assertSame(chiseled, xRun.get(6, 4));

        SurfacePlan zRun = provider(cornice).projectedPlans(7, 5, Direction.SOUTH, RandomSource.create(3L)).get(1);
        for (int u = 0; u < 7; u++) {
            assertSame(andesite, zRun.get(u, 4), "a projecting Z-edge run owns no ring corner");
        }
    }

    @Test
    void ownsCornersFlipsAxisWithDepth() {
        assertTrue(CoursesWallPatternProvider.ownsCorners(Direction.SOUTH, 0));
        assertTrue(CoursesWallPatternProvider.ownsCorners(Direction.NORTH, 0));
        assertFalse(CoursesWallPatternProvider.ownsCorners(Direction.EAST, 0));

        assertFalse(CoursesWallPatternProvider.ownsCorners(Direction.SOUTH, 1));
        assertTrue(CoursesWallPatternProvider.ownsCorners(Direction.EAST, 1));
        assertTrue(CoursesWallPatternProvider.ownsCorners(Direction.WEST, 1));
    }

    @Test
    void noCoursesMarksNothing() {
        assertEquals(0, new CoursesWallPatternProvider(List.of())
                .plan(5, 5, Direction.SOUTH).markedCells());
    }

    @Test
    void rowArithmeticIsDirect() {
        assertEquals(0, CoursesWallPatternProvider.rowFor(course(andesite, CourseAnchor.BOTTOM, 0), 6));
        assertEquals(2, CoursesWallPatternProvider.rowFor(course(andesite, CourseAnchor.BOTTOM, 2), 6));
        assertEquals(5, CoursesWallPatternProvider.rowFor(course(andesite, CourseAnchor.TOP, 0), 6));
        assertEquals(3, CoursesWallPatternProvider.rowFor(course(andesite, CourseAnchor.TOP, 2), 6));
    }
}
