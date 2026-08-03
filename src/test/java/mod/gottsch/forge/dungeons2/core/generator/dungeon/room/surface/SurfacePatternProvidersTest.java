package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three surface-generic pattern shapes. Written against {@code (u, v)} rather than against a
 * ceiling, so these tests are pure geometry -- no room, no world.
 */
class SurfacePatternProvidersTest {

    private static final Direction DOWN = Direction.DOWN;
    private static BlockState edge;
    private static BlockState corner;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        edge = Blocks.POLISHED_ANDESITE.defaultBlockState();
        corner = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    }

    // ---------- border ----------

    @Test
    void aFlushBorderRingsTheEdgeAndLeavesTheMiddleAlone() {
        SurfacePlan plan = new BorderSurfacePatternProvider(0, edge, corner).plan(5, 5, DOWN);
        // 5x5 perimeter = 16 cells.
        assertEquals(16, plan.markedCells());
        assertNull(plan.get(2, 2), "the middle must stay base");
        assertSame(corner, plan.get(0, 0));
        assertSame(corner, plan.get(4, 4));
        assertSame(edge, plan.get(2, 0));
    }

    @Test
    void insetMovesTheRingInward() {
        SurfacePlan plan = new BorderSurfacePatternProvider(1, edge, corner).plan(7, 7, DOWN);
        assertNull(plan.get(0, 0), "the outermost ring is skipped at inset 1");
        assertSame(corner, plan.get(1, 1));
        assertEquals(16, plan.markedCells(), "a 5x5 ring inside a 7x7 surface");
    }

    /** A ring needs room; an inset that collapses it yields nothing rather than a blob. */
    @Test
    void anInsetThatCollapsesTheRingMarksNothing() {
        assertEquals(0, new BorderSurfacePatternProvider(3, edge, corner).plan(5, 5, DOWN).markedCells());
    }

    /** A 1-wide surface degenerates to a line, not an exception. */
    @Test
    void aDegenerateExtentStillProducesAValidPlan() {
        SurfacePlan plan = new BorderSurfacePatternProvider(0, edge, corner).plan(1, 4, DOWN);
        assertEquals(4, plan.markedCells());
    }

    // ---------- coffers ----------

    /**
     * The lattice is centred, not corner-anchored: a rib lands on the middle cell so the panels are
     * symmetric about the room's axes. Anchoring at u=0 would leave a ragged partial panel whose
     * width depended on the room's size.
     */
    @Test
    void theCofferLatticeIsCentred() {
        SurfacePlan plan = new GridSurfacePatternProvider(3, edge).plan(7, 7, DOWN);
        assertSame(edge, plan.get(3, 3), "a rib crosses the centre");
        assertSame(edge, plan.get(0, 3));
        assertSame(edge, plan.get(6, 3));
        assertNull(plan.get(1, 1), "panels between the ribs stay base");
        assertNull(plan.get(2, 2));
    }

    @Test
    void ribsRunInBothAxes() {
        SurfacePlan plan = new GridSurfacePatternProvider(3, edge).plan(7, 7, DOWN);
        for (int u = 0; u < 7; u++) {
            assertSame(edge, plan.get(u, 3), "the centre row should be a continuous rib");
        }
        for (int v = 0; v < 7; v++) {
            assertSame(edge, plan.get(3, v), "the centre column should be a continuous rib");
        }
    }

    /** Coffers must mark a minority of cells, or it reads as a fill rather than as structure. */
    @Test
    void cofferPanelsOutnumberRibsAtSaneSpacings() {
        SurfacePlan plan = new GridSurfacePatternProvider(4, edge).plan(11, 11, DOWN);
        assertTrue(plan.markedCells() < 11 * 11 / 2,
                "ribs should be a minority, got " + plan.markedCells() + "/121");
    }

    /** Spacing 1 would make every cell a rib -- that is a solid fill, not a lattice. */
    @Test
    void aSpacingOfOneOrLessMarksNothing() {
        assertEquals(0, new GridSurfacePatternProvider(1, edge).plan(7, 7, DOWN).markedCells());
        assertEquals(0, new GridSurfacePatternProvider(0, edge).plan(7, 7, DOWN).markedCells());
    }

    // ---------- centre ----------

    @Test
    void aSingleCellBossLandsDeadCentreOnAnOddExtent() {
        SurfacePlan plan = new CentreSurfacePatternProvider(1, corner).plan(7, 9, DOWN);
        assertEquals(1, plan.markedCells());
        assertSame(corner, plan.get(3, 4));
    }

    @Test
    void aLargerBossIsASquare() {
        SurfacePlan plan = new CentreSurfacePatternProvider(3, corner).plan(9, 9, DOWN);
        assertEquals(9, plan.markedCells());
        assertSame(corner, plan.get(3, 3));
        assertSame(corner, plan.get(5, 5));
        assertNull(plan.get(2, 2));
    }

    /** An oversized boss clips to the surface rather than throwing. */
    @Test
    void aBossLargerThanTheSurfaceClips() {
        SurfacePlan plan = new CentreSurfacePatternProvider(99, corner).plan(4, 4, DOWN);
        assertEquals(16, plan.markedCells());
    }

    @Test
    void aNonPositiveBossMarksNothing() {
        assertEquals(0, new CentreSurfacePatternProvider(0, corner).plan(5, 5, DOWN).markedCells());
    }

    // ---------- composition ----------

    /**
     * The reason there is no "composite" type: sparse plans layer for free, later non-null winning.
     */
    @Test
    void aBossLaidOverCoffersReplacesTheCentreRibCell() {
        SurfacePlan combined = new GridSurfacePatternProvider(3, edge).plan(7, 7, DOWN);
        assertSame(edge, combined.get(3, 3));

        combined.overlay(new CentreSurfacePatternProvider(1, corner).plan(7, 7, DOWN));
        assertSame(corner, combined.get(3, 3), "the boss should win the centre");
        assertSame(edge, combined.get(0, 3), "and change nothing else");
    }
}
