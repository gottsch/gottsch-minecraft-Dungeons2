package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
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

    // ---------- border orientation ----------

    private static BorderSurfacePatternProvider orientedRing(int inset, SurfaceOrient orient) {
        BlockState stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        return new BorderSurfacePatternProvider(inset, stairs, stairs, orient,
                CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION);
    }

    private static Direction facingAt(SurfacePlan plan, int u, int v) {
        return plan.get(u, v).getValue(StairBlock.FACING);
    }

    /**
     * Each side of the ring faces off its own edge. This is the whole point of the feature: without
     * it every cell keeps one facing and the ring reads as blocky corbelling rather than as a vault
     * springing off the room's perimeter.
     */
    @Test
    void eachSideOfAnOutwardRingFacesOffItsOwnEdge() {
        SurfacePlan plan = orientedRing(0, SurfaceOrient.OUTWARD).plan(7, 7, DOWN);
        // u advances +X (east), v advances +Z (south) -- see CeilingSurface.U_DIRECTION.
        assertEquals(Direction.WEST, facingAt(plan, 0, 3), "the u=0 side is the west edge");
        assertEquals(Direction.EAST, facingAt(plan, 6, 3), "the u=max side is the east edge");
        assertEquals(Direction.NORTH, facingAt(plan, 3, 0), "the v=0 side is the north edge");
        assertEquals(Direction.SOUTH, facingAt(plan, 3, 6), "the v=max side is the south edge");
    }

    /** {@code inward} is the same ring turned through 180 degrees, not a different ring. */
    @Test
    void inwardIsTheExactOppositeOfOutward() {
        SurfacePlan out = orientedRing(0, SurfaceOrient.OUTWARD).plan(7, 7, DOWN);
        SurfacePlan in = orientedRing(0, SurfaceOrient.INWARD).plan(7, 7, DOWN);
        assertEquals(out.markedCells(), in.markedCells(), "orientation must not change the shape");
        for (int u = 0; u < 7; u++) {
            for (int v = 0; v < 7; v++) {
                if (out.get(u, v) != null) {
                    assertEquals(facingAt(out, u, v).getOpposite(), facingAt(in, u, v),
                            "cell (" + u + "," + v + ")");
                }
            }
        }
    }

    /**
     * A corner sits on two edges and must pick one <strong>deterministically</strong>. Lowest
     * {@link Direction} ordinal wins (NORTH 2, SOUTH 3, WEST 4, EAST 5), the same tie-break the
     * corridor arch uses -- see the planner's EnumMap fix for what a run-dependent choice costs.
     */
    @Test
    void aCornerPicksItsFacingDeterministicallyByLowestOrdinal() {
        SurfacePlan plan = orientedRing(0, SurfaceOrient.OUTWARD).plan(7, 7, DOWN);
        assertEquals(Direction.NORTH, facingAt(plan, 0, 0), "north-west: NORTH(2) beats WEST(4)");
        assertEquals(Direction.NORTH, facingAt(plan, 6, 0), "north-east: NORTH(2) beats EAST(5)");
        assertEquals(Direction.SOUTH, facingAt(plan, 0, 6), "south-west: SOUTH(3) beats WEST(4)");
        assertEquals(Direction.SOUTH, facingAt(plan, 6, 6), "south-east: SOUTH(3) beats EAST(5)");

        SurfacePlan again = orientedRing(0, SurfaceOrient.OUTWARD).plan(7, 7, DOWN);
        assertEquals(facingAt(plan, 0, 0), facingAt(again, 0, 0), "same input, same answer");
    }

    /** An inset ring orients off its own edges, not the surface's -- there is no wall out there. */
    @Test
    void anInsetRingOrientsOffItsOwnEdgesNotTheSurfaces() {
        SurfacePlan plan = orientedRing(1, SurfaceOrient.OUTWARD).plan(7, 7, DOWN);
        assertEquals(Direction.WEST, facingAt(plan, 1, 3));
        assertEquals(Direction.EAST, facingAt(plan, 5, 3));
        assertNull(plan.get(0, 3), "the outermost ring is not this ring");
    }

    /**
     * {@code none} is the shipped behaviour of every ring authored before orientation existed, and
     * must stay byte-identical -- the same instance back, not an equal one.
     */
    @Test
    void anUnorientedRingIsUntouched() {
        BlockState stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        SurfacePlan plan = new BorderSurfacePatternProvider(0, stairs, stairs).plan(7, 7, DOWN);
        assertSame(stairs, plan.get(3, 0));
        assertSame(stairs, plan.get(0, 0), "corners too");
    }

    /**
     * A ring of full cubes asked to orient keeps its block rather than throwing. Same lenient rule
     * {@code BlockStateCodec.withProperties} applies everywhere else: a datapack naming a facing on
     * a block that has none is an authoring slip, not a crash in worldgen.
     */
    @Test
    void orientingABlockWithNoFacingLeavesItAlone() {
        BorderSurfacePatternProvider provider = new BorderSurfacePatternProvider(
                0, edge, corner, SurfaceOrient.OUTWARD,
                CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION);
        SurfacePlan plan = provider.plan(5, 5, DOWN);
        assertSame(edge, plan.get(2, 0));
        assertSame(corner, plan.get(0, 0));
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
