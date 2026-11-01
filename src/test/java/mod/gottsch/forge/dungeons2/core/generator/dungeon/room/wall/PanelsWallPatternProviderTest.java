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
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code panels} pattern: rectangular fields inset from the top and bottom of a wall.
 *
 * <p>The rectangle is the whole reason this type exists &mdash; a course fills a row and a strip a
 * column, so nothing else in the schema can stop short of the wall vertically. Most of what is
 * asserted here is therefore about the field's <em>edges</em>.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
class PanelsWallPatternProviderTest {

    private static final Direction LONG_WALL = Direction.SOUTH;
    private static final Direction SHORT_WALL = Direction.EAST;

    /** Assigned in bootstrap, not at declaration: a BlockState needs the registries to exist first. */
    private static BlockState field;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        field = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    }

    private static PanelsWallPatternProvider panels(int width, int spacing, int inset, int projection) {
        return new PanelsWallPatternProvider(field, width, spacing, inset, projection, CourseOrient.NONE);
    }

    /** The field stops {@code inset} rows short of the wall's top and bottom -- the point of the type. */
    @Test
    void aFieldIsInsetFromTheTopAndBottomOfTheWall() {
        SurfacePlan plan = panels(3, 5, 1, 0).plan(15, 6, LONG_WALL);
        int start = PanelsWallPatternProvider.starts(15, 3, 5, 0, LONG_WALL).get(0);

        assertNull(plan.get(start, 0), "the bottom row stays plain wall");
        assertNull(plan.get(start, 5), "and so does the top");
        for (int v = 1; v <= 4; v++) {
            assertNotNull(plan.get(start, v), "row " + v + " is field");
        }
    }

    /** The field is {@code width} cells wide and the wall between fields is left alone. */
    @Test
    void aFieldIsWidthCellsWideAndNoMore() {
        SurfacePlan plan = panels(3, 6, 1, 0).plan(19, 6, LONG_WALL);
        int start = PanelsWallPatternProvider.starts(19, 3, 6, 0, LONG_WALL).get(0);

        for (int u = start; u < start + 3; u++) {
            assertNotNull(plan.get(u, 2), "column " + u + " is field");
        }
        assertNull(plan.get(start - 1, 2), "the cell before the field is plain");
        assertNull(plan.get(start + 3, 2), "and the cell after it");
    }

    /**
     * A wall too short to carry the field plus its margins draws nothing, rather than collapsing the
     * margins and running the panel into the floor. Same "drop it rather than squash it" rule a
     * course out of range follows.
     */
    @Test
    void aWallTooShortForTheMarginsDrawsNothing() {
        assertEquals(0, panels(3, 5, 2, 0).plan(15, 4, LONG_WALL).markedCells(),
                "4 rows cannot carry a field with 2 rows of margin each side");
        assertTrue(panels(3, 5, 1, 0).plan(15, 3, LONG_WALL).markedCells() > 0,
                "but 3 rows with a 1-row margin leaves a single field row, which is fine");
    }

    /** Fields never straddle a corner column, the same rule the even pilaster layout follows. */
    @Test
    void fieldsNeverStraddleACornerColumn() {
        for (int length = 8; length <= 24; length++) {
            for (int projection : new int[] {0, 1}) {
                for (Direction facing : new Direction[] {LONG_WALL, SHORT_WALL}) {
                    List<Integer> starts =
                            PanelsWallPatternProvider.starts(length, 3, 5, projection, facing);
                    boolean ownsCorners =
                            CoursesWallPatternProvider.ownsCorners(facing, projection);
                    for (int start : starts) {
                        assertTrue(start >= 0 && start + 2 <= length - 1,
                                "field " + start + ".." + (start + 2) + " runs off a run of " + length);
                        if (ownsCorners) {
                            assertFalse(start == 0, "field starts in a corner column");
                            assertFalse(start + 2 == length - 1, "field ends in a corner column");
                        }
                    }
                }
            }
        }
    }

    /** A field never runs off the end of its wall, at any width/spacing combination. */
    @Test
    void aFieldAlwaysFitsWithinTheWall() {
        for (int length = 4; length <= 24; length++) {
            for (int width = 1; width <= 5; width++) {
                for (int spacing = 1; spacing <= 6; spacing++) {
                    for (int start : PanelsWallPatternProvider.starts(length, width, spacing, 0, LONG_WALL)) {
                        assertTrue(start + width - 1 <= length - 1,
                                "field of " + width + " at " + start + " overruns a run of " + length);
                    }
                }
            }
        }
    }

    /** A projecting field is absent from the wall plane and lives entirely in its own layer. */
    @Test
    void aProjectingFieldLeavesTheWallPlaneAlone() {
        PanelsWallPatternProvider provider = panels(3, 6, 1, 1);
        assertEquals(0, provider.plan(19, 6, LONG_WALL).markedCells());
        assertEquals(1, provider.projectedPlans(19, 6, LONG_WALL).size());
        assertTrue(provider.projectedPlans(19, 6, LONG_WALL).get(1).markedCells() > 0);
    }

    /** A degenerate run draws nothing rather than throwing. */
    @Test
    void aRunWithNoLengthDrawsNothing() {
        assertTrue(PanelsWallPatternProvider.starts(0, 3, 5, 0, LONG_WALL).isEmpty());
        assertEquals(0, panels(3, 5, 1, 0).plan(0, 6, LONG_WALL).markedCells());
    }

    /** A wall narrower than one field carries none, rather than a clipped one. */
    @Test
    void aWallNarrowerThanOneFieldCarriesNone() {
        assertTrue(PanelsWallPatternProvider.starts(4, 6, 5, 0, LONG_WALL).isEmpty());
    }
}
