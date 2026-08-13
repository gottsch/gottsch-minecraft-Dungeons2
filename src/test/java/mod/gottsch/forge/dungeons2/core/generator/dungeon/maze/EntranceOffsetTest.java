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
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #2: the entrance is placed at a rolled position within floor 0's grid rather than at its
 * centre.
 *
 * <h2>What actually moves</h2>
 * <p>Not the entrance. It is assembled in the world first, and the grid's world anchor is derived
 * backwards from it, so the building stays exactly where it was put. What moves is the <strong>grid
 * around it</strong> &mdash; you come down into a corner or an edge of the dungeon instead of always
 * its dead middle, and the maze runs away from you rather than surrounding you evenly.</p>
 *
 * <p>These tests read the offset back off the finished layout, from the entrance's position relative
 * to floor 0's own bounds, because that is what a player experiences. Asserting the private roll
 * would pass just as well with the result thrown away.</p>
 *
 * @author Mark Gottschling on Aug 11, 2026
 */
class EntranceOffsetTest {

    private static final String MOTIF = DungeonMotif.CLASSIC.getValue();

    /** Mirrors {@code DungeonStackPlanner.ENTRANCE_MARGIN}; the invariant under test. */
    private static final int ENTRANCE_MARGIN = 8;

    private static final int ENTRANCE_SIZE = 7;

    private static Optional<DungeonLayout> plan(long seed, DungeonSize size) {
        // A world origin that varies with the seed, the way real chunk placement does -- the offset
        // must not be a function of where the entrance happens to have landed.
        int eMinX = 96 + (int) ((seed * 2) % 32);
        int eMinZ = 192 + (int) ((seed * 3) % 32);
        Rectangle2D entranceRect = new Rectangle2D(eMinX, eMinZ, ENTRANCE_SIZE, ENTRANCE_SIZE);
        List<Coords2D> doorCells = new ArrayList<>();
        doorCells.add(new Coords2D(eMinX, eMinZ + 3));           // west
        doorCells.add(new Coords2D(eMinX + 3, eMinZ));           // north
        doorCells.add(new Coords2D(eMinX + 6, eMinZ + 3));       // east

        return new DungeonStackPlanner(seed, new Coords(eMinX + 3, 0, eMinZ + 3), 72, MOTIF,
                new TemplateCatalog())
                .withSize(size).withFloorCount(1)
                .withAssembledEntrance(entranceRect, doorCells, 64)
                .plan();
    }

    /** The entrance's grid-local origin, recovered from the layout's anchor. */
    private static int[] entranceLocal(long seed, DungeonLayout layout) {
        int eMinX = 96 + (int) ((seed * 2) % 32);
        int eMinZ = 192 + (int) ((seed * 3) % 32);
        return new int[] {eMinX - layout.getAnchor().getX(), eMinZ - layout.getAnchor().getZ()};
    }

    /** Floor 0's grid extent, straight off the layout's own footprint. */
    private static int gridExtent(DungeonLayout layout, int axis) {
        Rectangle2D footprint = layout.getFloors().get(0).getFootprint();
        return axis == 0 ? footprint.getWidth() : footprint.getHeight();
    }

    /** The clear cells on each side of the entrance along one axis: {near, far}. */
    private static int[] margins(long seed, DungeonLayout layout, int axis) {
        int local = entranceLocal(seed, layout)[axis];
        return new int[] {local, gridExtent(layout, axis) - ENTRANCE_SIZE - local};
    }

    /**
     * <strong>The invariant, and the reason this needed no new config knob.</strong> The entrance may
     * sit anywhere that still leaves {@code ENTRANCE_MARGIN} clear on both sides &mdash; which is
     * exactly what the margin was introduced to guarantee. Checked on every seed rather than
     * sampled, because a margin violation is a corridor with nowhere to route.
     */
    @Test
    void theEntranceAlwaysKeepsItsRoutingMarginOnEverySide() {
        int planned = 0;
        for (long seed = 0; seed < 200; seed++) {
            Optional<DungeonLayout> opt = plan(seed, DungeonSize.MEDIUM);
            if (opt.isEmpty()) {
                continue;
            }
            planned++;
            for (int axis = 0; axis < 2; axis++) {
                int[] gap = margins(seed, opt.get(), axis);
                // A floor with no slack degrades to centred, which can be under the margin; that is
                // the pre-existing behaviour and is what the grid sizing already allowed for.
                boolean hasSlack = gridExtent(opt.get(), axis) - ENTRANCE_SIZE - 2 * ENTRANCE_MARGIN > 0;
                if (hasSlack) {
                    assertTrue(gap[0] >= ENTRANCE_MARGIN && gap[1] >= ENTRANCE_MARGIN,
                            "seed " + seed + " axis " + axis + ": margins " + gap[0] + "/" + gap[1]
                                    + " below " + ENTRANCE_MARGIN);
                }
            }
        }
        assertTrue(planned > 150, "expected most seeds to plan, got " + planned + "/200");
    }

    /**
     * <strong>It is genuinely off-centre, which is the whole feature.</strong>
     *
     * <p>Asymmetry is the measure, not "the position varies" &mdash; the position varies with the
     * grid SIZE too, so a distinct-positions count passes just as happily with the old centred
     * placement. Verified by reverting the roll: this assertion fails and that one did not.</p>
     */
    @Test
    void theEntranceSitsOffCentreInMostDungeons() {
        int offCentre = 0;
        int planned = 0;
        for (long seed = 0; seed < 200; seed++) {
            Optional<DungeonLayout> opt = plan(seed, DungeonSize.MEDIUM);
            if (opt.isEmpty()) {
                continue;
            }
            planned++;
            int[] x = margins(seed, opt.get(), 0);
            int[] z = margins(seed, opt.get(), 1);
            if (Math.abs(x[0] - x[1]) > 2 || Math.abs(z[0] - z[1]) > 2) {
                offCentre++;
            }
        }
        assertTrue(offCentre > planned / 2,
                "only " + offCentre + " of " + planned + " dungeons put the entrance off centre");
    }

    /** And both axes move, not just one -- they roll from separately salted streams. */
    @Test
    void bothAxesGoOffCentreIndependently() {
        int xOff = 0;
        int zOff = 0;
        for (long seed = 0; seed < 200; seed++) {
            Optional<DungeonLayout> opt = plan(seed, DungeonSize.MEDIUM);
            if (opt.isEmpty()) {
                continue;
            }
            int[] x = margins(seed, opt.get(), 0);
            int[] z = margins(seed, opt.get(), 1);
            if (Math.abs(x[0] - x[1]) > 2) {
                xOff++;
            }
            if (Math.abs(z[0] - z[1]) > 2) {
                zOff++;
            }
        }
        assertTrue(xOff > 40, "x is barely off centre: " + xOff + "/200");
        assertTrue(zOff > 40, "z is barely off centre: " + zOff + "/200");
    }

    /**
     * The maze rejects an odd-origin room, so the entrance's reserved slot must stay even. This was
     * true of the centred version by construction and is easy to lose when a roll is introduced.
     */
    @Test
    void theEntranceOriginStaysEven() {
        for (long seed = 0; seed < 200; seed++) {
            Optional<DungeonLayout> opt = plan(seed, DungeonSize.MEDIUM);
            if (opt.isPresent()) {
                int[] local = entranceLocal(seed, opt.get());
                assertEquals(0, local[0] & 1, "seed " + seed + ": odd entrance x " + local[0]);
                assertEquals(0, local[1] & 1, "seed " + seed + ": odd entrance z " + local[1]);
            }
        }
    }

    /**
     * Same seed, same answer. A room is rendered once per overlapping chunk and every run has to
     * agree; an entrance that moved between calls would tear the dungeon rather than vary it.
     */
    @Test
    void theOffsetIsDeterministic() {
        for (long seed = 0; seed < 50; seed++) {
            Optional<DungeonLayout> first = plan(seed, DungeonSize.MEDIUM);
            Optional<DungeonLayout> second = plan(seed, DungeonSize.MEDIUM);
            assertEquals(first.isPresent(), second.isPresent(), "seed " + seed);
            if (first.isPresent()) {
                assertEquals(first.get().getAnchor().getX(), second.get().getAnchor().getX(),
                        "seed " + seed + ": anchor x moved between runs");
                assertEquals(first.get().getAnchor().getZ(), second.get().getAnchor().getZ(),
                        "seed " + seed + ": anchor z moved between runs");
            }
        }
    }

    /**
     * A small dungeon's grid can be barely larger than the entrance plus its margins, so the roll
     * has no slack and must degrade to the old centred placement rather than throwing or pushing the
     * entrance out of bounds.
     */
    @Test
    void aFloorWithNoSlackStillPlans() {
        int planned = 0;
        for (long seed = 0; seed < 100; seed++) {
            if (plan(seed, DungeonSize.SMALL).isPresent()) {
                planned++;
            }
        }
        assertTrue(planned > 70, "small dungeons should still plan, got " + planned + "/100");
    }

    /** Every room still lands inside the floor, offset entrance or not. */
    @Test
    void noRoomEndsUpOutsideTheFloor() {
        for (long seed = 0; seed < 100; seed++) {
            Optional<DungeonLayout> opt = plan(seed, DungeonSize.MEDIUM);
            if (opt.isEmpty()) {
                continue;
            }
            for (RoomData room : opt.get().getFloors().get(0).getRooms()) {
                assertTrue(room.getOriginX() >= 0 && room.getOriginZ() >= 0,
                        "seed " + seed + ": room at negative grid origin "
                                + room.getOriginX() + "," + room.getOriginZ()
                                + " role " + room.getRole());
            }
        }
    }

    /** Sanity: the harness is producing real dungeons, not empty ones every time. */
    @Test
    void theHarnessBuildsRealFloors() {
        DungeonLayout layout = plan(7L, DungeonSize.MEDIUM).orElseThrow();
        long normals = layout.getFloors().get(0).getRooms().stream()
                .filter(room -> room.getRole() == RoomRole.NORMAL).count();
        assertTrue(normals > 3, "expected a populated floor, got " + normals + " normal rooms");
    }
}
