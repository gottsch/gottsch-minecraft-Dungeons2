package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four surface-generic pattern shapes. Written against {@code (u, v)} rather than against a
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

    // ---------- joists ----------

    /** A log: the case where {@code axis} has to be derived. */
    private static JoistSurfacePatternProvider joists(int spacing) {
        return JoistSurfacePatternProvider.beams(spacing, Blocks.SPRUCE_LOG.defaultBlockState());
    }

    /** Vanilla stairs stand in for a corbel -- what matters is that it carries {@code facing}. */
    private static JoistSurfacePatternProvider brackets(int spacing, SurfaceOrient orient) {
        return JoistSurfacePatternProvider.brackets(spacing,
                Blocks.STONE_BRICK_STAIRS.defaultBlockState(), orient,
                CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION);
    }

    /**
     * The rule that separates this from a colonnade: a beam <strong>spans</strong>, so it crosses
     * the shorter extent and the rhythm steps along the longer one. A colonnade runs the other way,
     * along the length -- reusing its elongation test here would put the beams down the room.
     */
    @Test
    void beamsRunAcrossTheShorterAxis() {
        // u is the short axis (5 against 9), so each beam is a full row of constant v.
        SurfacePlan plan = joists(3).plan(5, 9, DOWN);
        for (int u = 0; u < 5; u++) {
            assertTrue(plan.get(u, 4) != null, "the centre beam should cross the whole 5-cell span");
        }
        assertNull(plan.get(2, 3), "and the cells between beams stay base");
    }

    @Test
    void andTheOtherWayRoundWhenTheOtherAxisIsShorter() {
        SurfacePlan plan = joists(3).plan(9, 5, DOWN);
        for (int v = 0; v < 5; v++) {
            assertTrue(plan.get(4, v) != null, "the centre beam should cross the whole 5-cell span");
        }
        assertNull(plan.get(3, 2));
    }

    /**
     * A square surface has no shorter axis, so the tie-break must be fixed rather than rolled: a
     * room renders once per overlapping chunk and every run has to agree, or the ceiling tears at
     * the seam.
     */
    @Test
    void aSquareSurfaceAlwaysRunsAlongUAndNeverDeclines() {
        SurfacePlan plan = joists(3).plan(7, 7, DOWN);
        for (int u = 0; u < 7; u++) {
            assertTrue(plan.get(u, 3) != null, "u runs, so the centre row is a beam");
        }
        assertNull(plan.get(3, 4), "and the column is not");
        assertEquals(plan.markedCells(), joists(3).plan(7, 7, DOWN).markedCells(),
                "same input, same answer");
    }

    /** The same centred rhythm the coffer lattice uses, shared rather than restated. */
    @Test
    void theBeamRhythmIsCentred() {
        SurfacePlan plan = joists(3).plan(5, 7, DOWN);
        assertTrue(plan.get(0, 3) != null, "a beam lands on the centre line");
        assertTrue(plan.get(0, 0) != null);
        assertTrue(plan.get(0, 6) != null);
        assertNull(plan.get(0, 1));
        assertNull(plan.get(0, 2));
    }

    /** Every line a beam is a solid ceiling, not a run of joists -- same degrade as the grid's. */
    @Test
    void aSpacingOfOneOrLessMarksNoBeams() {
        assertEquals(0, joists(1).plan(7, 7, DOWN).markedCells());
        assertEquals(0, joists(0).plan(7, 7, DOWN).markedCells());
    }

    /**
     * A beam block that has an {@code axis} is laid <em>along</em> the run. This cannot be authored:
     * the run direction comes from the room's proportions, so a hardcoded value is wrong in every
     * room shaped the other way.
     */
    @Test
    void aBeamWithAnAxisIsLaidAlongItsRun() {
        assertEquals(Direction.Axis.X, joists(3).plan(5, 9, DOWN).get(2, 4).getValue(RotatedPillarBlock.AXIS),
                "u is short, so the beams run east-west");
        assertEquals(Direction.Axis.Z, joists(3).plan(9, 5, DOWN).get(4, 2).getValue(RotatedPillarBlock.AXIS),
                "v is short, so they run north-south");
    }

    /** A stone beam is a plain cube with no axis at all, and must be placed rather than rejected. */
    @Test
    void aBeamWithNoAxisIsPlacedUnchanged() {
        SurfacePlan plan = JoistSurfacePatternProvider.beams(3, edge).plan(5, 9, DOWN);
        assertSame(edge, plan.get(2, 4));
    }

    /**
     * The beams are never interrupted: a bracket carries its beam from the row <em>below</em>, so
     * the beam's own plan runs unbroken wall to wall whether or not one is authored.
     *
     * <p>The first cut had the bracket replace the end cell, which Mark rejected on sight in game
     * &mdash; a corbel sitting in the beam's row is not supporting it, it is interrupting it.</p>
     */
    @Test
    void theBeamRunsUnbrokenToBothWalls() {
        SurfacePlan plan = joists(3).plan(5, 9, DOWN);
        for (int u = 0; u < 5; u++) {
            assertEquals(Blocks.SPRUCE_LOG, plan.get(u, 4).getBlock(), "cell (" + u + ",4)");
        }
    }

    /** And the brackets are a plan of their own, marking only the ends the beams run between. */
    @Test
    void theBracketPlanMarksOnlyTheEndsOfEachRun() {
        SurfacePlan plan = brackets(3, SurfaceOrient.INWARD).plan(5, 9, DOWN);
        assertEquals(Blocks.STONE_BRICK_STAIRS, plan.get(0, 4).getBlock());
        assertEquals(Blocks.STONE_BRICK_STAIRS, plan.get(4, 4).getBlock());
        for (int u = 1; u <= 3; u++) {
            assertNull(plan.get(u, 4), "the span between the brackets is open air at (" + u + ",4)");
        }
    }

    /**
     * <strong>Bracket lines and beam lines must be the same lines.</strong> Both parts derive the
     * run axis and the rhythm from the same extents, so this holds by construction rather than by
     * agreement -- which is the reason the two are one class with a {@code Part} and not two.
     */
    @Test
    void everyBracketSitsUnderABeam() {
        SurfacePlan beams = joists(3).plan(9, 5, DOWN);
        SurfacePlan brackets = brackets(3, SurfaceOrient.INWARD).plan(9, 5, DOWN);
        for (int u = 0; u < 9; u++) {
            for (int v = 0; v < 5; v++) {
                if (brackets.get(u, v) != null) {
                    assertNotNull(beams.get(u, v),
                            "a bracket with no beam over it at (" + u + "," + v + ")");
                }
            }
        }
    }

    /**
     * One authored {@code orient} turns both brackets relative to <em>their own</em> wall, which is
     * the same thing a wall course's orient buys across four runs. {@code outward} points each
     * bracket at the wall its end rests on.
     */
    @Test
    void eachBracketFacesItsOwnEndOfTheRun() {
        SurfacePlan plan = brackets(3, SurfaceOrient.OUTWARD).plan(5, 9, DOWN);
        // u advances east, so the run's two ends are west (u=0) and east (u=4).
        assertEquals(Direction.WEST, plan.get(0, 4).getValue(StairBlock.FACING));
        assertEquals(Direction.EAST, plan.get(4, 4).getValue(StairBlock.FACING));
    }

    /**
     * {@code inward} is the value a {@code dungeonblocks} corbel wants: its model puts the post on
     * the far face and cantilevers the arm away from it, so the block faces off its wall into the
     * room. Kept as an authored choice rather than assumed, because #25's family of inverted trim
     * models is exactly where reasoning about this has been wrong before.
     */
    @Test
    void inwardIsTheExactOppositeAtBothEnds() {
        SurfacePlan out = brackets(3, SurfaceOrient.OUTWARD).plan(5, 9, DOWN);
        SurfacePlan in = brackets(3, SurfaceOrient.INWARD).plan(5, 9, DOWN);
        assertEquals(out.markedCells(), in.markedCells(), "orientation must not change the shape");
        assertEquals(Direction.EAST, in.get(0, 4).getValue(StairBlock.FACING));
        assertEquals(Direction.WEST, in.get(4, 4).getValue(StairBlock.FACING));
    }

    /**
     * A one-cell run has one end, not two. Without the guard the far-end write would land on the
     * same cell facing the other way, so the "outward" bracket in a 1-wide room would silently be
     * the inward one.
     */
    @Test
    void aOneCellRunGetsASingleBracket() {
        // A 1x9 surface is three one-cell runs (stride 1, 4, 7), each with one end rather than two.
        SurfacePlan plan = brackets(3, SurfaceOrient.OUTWARD).plan(1, 9, DOWN);
        assertEquals(3, plan.markedCells(), "one bracket per run");
        assertEquals(Direction.WEST, plan.get(0, 4).getValue(StairBlock.FACING),
                "without the guard the far-end write lands on the same cell and turns it east");
    }

    /** Orienting a bracket with no facing is an authoring slip, not a crash in worldgen. */
    @Test
    void orientingABracketWithNoFacingLeavesItAlone() {
        JoistSurfacePatternProvider provider = JoistSurfacePatternProvider.brackets(
                3, corner, SurfaceOrient.OUTWARD,
                CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION);
        assertSame(corner, provider.plan(5, 9, DOWN).get(0, 4));
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
