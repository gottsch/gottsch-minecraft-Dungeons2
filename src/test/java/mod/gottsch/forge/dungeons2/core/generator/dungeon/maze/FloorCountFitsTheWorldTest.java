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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.maze;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #50: the floor stack stops at the world's floor.
 *
 * <p>{@code floorCount} is rolled from the size tier and nothing used to consult the world bottom,
 * so the stack simply marched down at {@code floorHeight + gapBetweenFloors} and landed wherever it
 * landed. At the shipped pitch of 12 that needed a surface around Y=9 to bite, which is why it was
 * never seen &mdash; but it is unguarded, not safe, and #29's proposed pitch of 19-22 brings the
 * threshold up into terrain that exists.</p>
 *
 * <p>Runs the real planner headlessly: it takes {@code surfaceY} as a plain int, so no world is
 * needed to put a dungeon somewhere impossible.</p>
 */
class FloorCountFitsTheWorldTest {

    private static final int OVERWORLD_FLOOR = -64;
    /** Matches {@code DungeonStackPlanner.BEDROCK_MARGIN}. */
    private static final int BEDROCK_MARGIN = 5;

    private static Optional<DungeonLayout> plan(int surfaceY, DungeonSize size, int floors) {
        return new DungeonStackPlanner(0xD2_50_0001L, new Coords(0, 0, 0), surfaceY,
                "classic", new TemplateCatalog())
                .withSize(size)
                .withFloorCount(floors)
                .withMinBuildY(OVERWORLD_FLOOR)
                .plan();
    }

    /** The invariant, over a sweep of surfaces from ordinary down to absurd. */
    @Test
    void noFloorIsEverPlacedBelowTheWorldFloor() {
        int checked = 0;
        for (int surfaceY = 100; surfaceY >= -40; surfaceY -= 7) {
            for (DungeonSize size : DungeonSize.values()) {
                Optional<DungeonLayout> planned = plan(surfaceY, size, size.getMaxFloors());
                if (planned.isEmpty()) {
                    continue;
                }
                for (FloorLayout floor : planned.get().getFloors()) {
                    checked++;
                    assertTrue(floor.getFloorY() >= OVERWORLD_FLOOR + BEDROCK_MARGIN,
                            "surfaceY " + surfaceY + " / " + size + ": floor "
                                    + floor.getFloorIndex() + " sits at Y=" + floor.getFloorY()
                                    + ", inside or below the bedrock band");
                }
            }
        }
        assertTrue(checked > 0, "no floors were examined, so this asserted nothing");
    }

    /**
     * Floor 0 is the one floor the clamp cannot shorten its way out of: its Y comes from the
     * entrance, not from the stack, so a surface low enough to put it in the bedrock band leaves no
     * valid dungeon at all. Declining beats generating one buried in it.
     */
    @Test
    void aSurfaceTooLowForFloorZeroPlacesNothing() {
        Optional<DungeonLayout> planned = plan(-40, DungeonSize.SMALL, 1);
        assertTrue(planned.isEmpty(),
                "a dungeon was planned with its first floor below the world floor");
    }

    /** ...and the boundary is not off by one: just enough room still builds. */
    @Test
    void aSurfaceJustDeepEnoughStillPlaces() {
        // Synthetic entrance: floor0Y = surfaceY - entranceHeight - (floorHeight - 1).
        // Sweep up from clearly-too-low until one plans, then assert it cleared the band.
        Optional<DungeonLayout> firstThatFits = Optional.empty();
        for (int surfaceY = -40; surfaceY <= 40 && firstThatFits.isEmpty(); surfaceY++) {
            firstThatFits = plan(surfaceY, DungeonSize.SMALL, 1);
        }
        assertTrue(firstThatFits.isPresent(),
                "nothing planned anywhere between surfaceY -40 and 40, so the guard is too eager");
        assertTrue(firstThatFits.get().getFloors().get(0).getFloorY()
                        >= OVERWORLD_FLOOR + BEDROCK_MARGIN,
                "the first surface that planned put floor 0 inside the bedrock band");
    }

    /**
     * A surface high enough for the whole stack must be untouched &mdash; the clamp is a floor, not
     * a policy. If this fails, every ordinary dungeon in the world just got shorter.
     */
    @Test
    void anOrdinarySurfaceKeepsEveryFloorItRolled() {
        for (DungeonSize size : DungeonSize.values()) {
            Optional<DungeonLayout> planned = plan(72, size, size.getMaxFloors());
            assertTrue(planned.isPresent(), size + " failed to plan at an ordinary surface");
            assertEquals(size.getMaxFloors(), planned.get().getFloors().size(),
                    size + " lost a floor at surfaceY 72, where the full stack fits easily");
        }
    }

    /** Low enough and the dungeon gets shorter rather than broken. */
    @Test
    void aLowSurfaceShortensTheDungeonRatherThanBreakingIt() {
        Optional<DungeonLayout> planned = plan(-30, DungeonSize.LARGE, 5);
        assertTrue(planned.isPresent(),
                "a low surface produced no dungeon at all; the clamp should shorten, not fail");
        assertTrue(planned.get().getFloors().size() < 5,
                "five floors still fit below Y=-30, so this case is not testing the clamp");
        assertTrue(planned.get().getFloors().size() >= 1,
                "the dungeon was clamped out of existence; one floor is the floor");
    }

    /**
     * The property that keeps existing worlds intact: the clamp takes a {@code min} on an
     * already-rolled count rather than moving the roll, so a seed that fits consumes exactly the
     * randomness it always did and lays out identically. Compared against a planner with the world
     * floor pushed far out of reach, which is the closest thing to "before this change" available.
     */
    @Test
    void aSeedThatFitsIsUnchangedByTheClampExisting() {
        for (int i = 0; i < 40; i++) {
            long seed = 0xD2_50_0100L + i * 7919L;
            Optional<DungeonLayout> clamped = new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72,
                    "classic", new TemplateCatalog())
                    .withSize(DungeonSize.LARGE).withMinBuildY(OVERWORLD_FLOOR).plan();
            Optional<DungeonLayout> unbounded = new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72,
                    "classic", new TemplateCatalog())
                    .withSize(DungeonSize.LARGE).withMinBuildY(-10_000).plan();

            assertEquals(unbounded.isPresent(), clamped.isPresent(), "seed " + seed);
            if (clamped.isEmpty()) {
                continue;
            }
            assertEquals(unbounded.get().getFloors().size(), clamped.get().getFloors().size(),
                    "seed " + seed + " lost a floor to the clamp at an ordinary surface");
            for (int f = 0; f < clamped.get().getFloors().size(); f++) {
                FloorLayout a = clamped.get().getFloors().get(f);
                FloorLayout b = unbounded.get().getFloors().get(f);
                assertEquals(b.getFloorY(), a.getFloorY(), "seed " + seed + " floor " + f + " Y");
                assertEquals(b.getRooms().size(), a.getRooms().size(),
                        "seed " + seed + " floor " + f + " room count -- the layout moved, which"
                                + " means the clamp disturbed the random stream");
            }
        }
    }

    /**
     * The world floor is a parameter, not the overworld's constant. A dimension with a shallower
     * floor has to clamp against its own.
     */
    @Test
    void theClampUsesTheSuppliedWorldFloor() {
        Optional<DungeonLayout> shallow = new DungeonStackPlanner(0xD2_50_0002L,
                new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                .withSize(DungeonSize.LARGE).withFloorCount(5).withMinBuildY(30).plan();
        assertTrue(shallow.isPresent());
        for (FloorLayout floor : shallow.get().getFloors()) {
            assertTrue(floor.getFloorY() >= 30 + BEDROCK_MARGIN,
                    "floor at Y=" + floor.getFloorY() + " is below a world floor of 30");
        }
        assertTrue(shallow.get().getFloors().size() < 5,
                "a world floor of 30 leaves room for at most a couple of floors below Y=52");
    }
}
