package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
     * The reason TOP exists. Room height varies 5..10, so a crown measured from the floor would
     * drift away from the ceiling; measured from the top it stays put.
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
