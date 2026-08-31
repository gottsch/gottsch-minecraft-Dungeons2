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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry of {@code dungeons2:diamond}. Pure {@code (u, v)}, no room and no world, which is the
 * whole point of the {@code ISurfacePatternProvider} boundary.
 *
 * @author Mark Gottschling on Aug 30, 2026
 */
class DiamondWallPatternProviderTest {

    private static BlockState accent;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        accent = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    }

    private static DiamondWallPatternProvider outline(int size, int spacing) {
        return new DiamondWallPatternProvider(accent, size, spacing, false);
    }

    /**
     * The shape itself: every marked cell is at Manhattan distance exactly {@code size} from some
     * centre, and every cell at that distance is marked. Asserted over the whole plan rather than
     * by counting, so a diamond that is the right SIZE but the wrong SHAPE cannot pass.
     */
    @Test
    void theOutlineIsExactlyTheManhattanRing() {
        SurfacePlan plan = outline(2, 6).plan(11, 5, Direction.NORTH);

        // One diamond fits at spacing 6 in 11 cells? 1 + (11-5)/6 = 2. Centres 5 apart from the
        // ends: used = 5 + 6 = 11, so uStart = 0 + 2 = 2, and the second centre is 8.
        int[] centres = {2, 8};
        int vCentre = 2;
        for (int u = 0; u < 11; u++) {
            for (int v = 0; v < 5; v++) {
                boolean expected = false;
                for (int uc : centres) {
                    if (Math.abs(u - uc) + Math.abs(v - vCentre) == 2) {
                        expected = true;
                    }
                }
                if (expected) {
                    assertNotNull(plan.get(u, v), "expected a mark at " + u + "," + v);
                } else {
                    assertNull(plan.get(u, v), "unexpected mark at " + u + "," + v);
                }
            }
        }
    }

    /** A size-2 outline is 8 cells: four tips and four edge cells. Filled, it is 13. */
    @Test
    void theOutlineIsHollowAndFilledIsNot() {
        assertEquals(8, outline(2, 99).plan(5, 5, Direction.NORTH).markedCells());
        assertEquals(13, new DiamondWallPatternProvider(accent, 2, 99, true)
                .plan(5, 5, Direction.NORTH).markedCells());

        // And the hollow one really is hollow at its own centre.
        assertNull(outline(2, 99).plan(5, 5, Direction.NORTH).get(2, 2));
        assertNotNull(new DiamondWallPatternProvider(accent, 2, 99, true)
                .plan(5, 5, Direction.NORTH).get(2, 2));
    }

    /**
     * A wall too short draws NOTHING rather than a clipped diamond, which would be a triangle. The
     * binding constraint in practice: a wall is only roomHeight - 2 rows, so 3 to 8.
     */
    @Test
    void aWallTooShortDrawsNothing() {
        for (int vSize = 1; vSize < 5; vSize++) {
            assertEquals(0, outline(2, 6).plan(21, vSize, Direction.NORTH).markedCells(),
                    "vSize " + vSize + " cannot carry a size-2 diamond and must draw nothing");
        }
        assertTrue(outline(2, 6).plan(21, 5, Direction.NORTH).markedCells() > 0,
                "5 rows is exactly enough for a size-2 diamond");

        // The same rule on the other axis.
        assertEquals(0, outline(2, 6).plan(4, 7, Direction.NORTH).markedCells());
    }

    /**
     * Size 1 is the one that fits a 3-row wall, which is the shortest the mod builds. Its outline is
     * the four tips and nothing else -- a plus sign, which is why 2 is the default.
     */
    @Test
    void sizeOneFitsTheShortestWall() {
        SurfacePlan plan = outline(1, 4).plan(9, 3, Direction.NORTH);
        assertTrue(plan.markedCells() > 0, "a size-1 diamond must fit 3 rows");
        assertEquals(0, plan.markedCells() % 4,
                "every size-1 outline is 4 cells, so the total must be a multiple of 4");
    }

    /**
     * The run is centred: the space left over is split between the two ends rather than trailing off
     * one of them.
     *
     * <p><strong>Not asserted as mirror symmetry</strong>, which is what this test did first and why
     * it failed at {@code uSize} 6. An odd leftover cannot be split evenly on a discrete grid, so
     * the run is off-centre by half a cell in half of all widths -- the same parity the centre pillar
     * and the cross floor pattern both have. The real property is that the two end gaps differ by at
     * most one, and that the extra cell consistently goes to the far end (the run leans LOW, matching
     * {@code (n - 1) / 2} everywhere else). Asserting exact symmetry would have forced the code to
     * lean a different way in different widths to pass, which is the actual bug.</p>
     */
    @Test
    void theRunIsCentredAlongTheWall() {
        for (int uSize = 5; uSize <= 40; uSize++) {
            SurfacePlan plan = outline(2, 6).plan(uSize, 5, Direction.NORTH);

            int first = -1;
            int last = -1;
            for (int u = 0; u < uSize; u++) {
                for (int v = 0; v < 5; v++) {
                    if (plan.get(u, v) != null) {
                        if (first < 0) {
                            first = u;
                        }
                        last = u;
                    }
                }
            }
            assertTrue(first >= 0, "uSize " + uSize + " drew nothing");

            int leftGap = first;
            int rightGap = uSize - 1 - last;
            assertTrue(rightGap - leftGap == 0 || rightGap - leftGap == 1,
                    "uSize " + uSize + ": gaps " + leftGap + " and " + rightGap
                            + " -- the run must be centred, leaning low by at most one cell");
        }
    }

    /** Vertically it takes the lower middle row, matching every other centred thing in the mod. */
    @Test
    void anEvenHeightTakesTheLowerMiddleRow() {
        // vSize 6, size 2 -> centre row 2, so the diamond spans rows 0..4 and row 5 is clear.
        SurfacePlan plan = outline(2, 99).plan(5, 6, Direction.NORTH);
        assertNotNull(plan.get(2, 0), "the bottom tip should be on row 0");
        for (int u = 0; u < 5; u++) {
            assertNull(plan.get(u, 5), "the top row should be clear at " + u);
        }
    }

    /**
     * Spacing below the diamond's own width is a LATTICE, not a bug: the diamonds overlap, their
     * edges cross, and the marks stay inside the plan. The provider must not clamp it away.
     */
    @Test
    void aTightSpacingMakesALatticeRatherThanFailing() {
        SurfacePlan tight = outline(2, 2).plan(21, 5, Direction.NORTH);
        SurfacePlan loose = outline(2, 6).plan(21, 5, Direction.NORTH);
        assertTrue(tight.markedCells() > loose.markedCells(),
                "overlapping diamonds should mark more cells, not fewer");
    }

    /** Nothing may be written outside the plan, which overlapping diamonds could otherwise reach. */
    @Test
    void aDiamondNeverMarksOutsideThePlan() {
        // Exercised through the widest overlap this can produce: spacing 1 puts a centre in every
        // column, including the first and last, whose diamonds reach past both ends.
        SurfacePlan plan = outline(2, 1).plan(7, 5, Direction.NORTH);
        assertEquals(7, plan.uSize());
        assertEquals(5, plan.vSize());
        assertTrue(plan.markedCells() > 0);
    }
}
