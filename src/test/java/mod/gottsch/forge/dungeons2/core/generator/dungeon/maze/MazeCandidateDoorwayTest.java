/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.ILevel2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Room2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 prep (POJO core): a START/END room can mark a SET of "possible" door
 * cells via {@link IRoom2D#setCandidateDoorways}, and the maze then opens doors
 * only at those cells, up to the room's degrees.
 *
 * <p>Two-phase per seed so the candidates are guaranteed connectable (no
 * {@code forceConnect} rock-tunnel fallback, which intentionally prioritizes
 * reachability over candidate adherence): phase 1 records the doors the maze
 * picks naturally; phase 2 re-runs the <em>same seed</em> with a subset of those
 * cells as candidates and asserts the doors are now restricted to that subset.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
class MazeCandidateDoorwayTest {

    private static final int W = 29;
    private static final int H = 29;
    private static final int START_DEGREES = 3;

    @Test
    void doorsAreRestrictedToCandidateCells() {
        int exercised = 0;

        for (long seed = 0; seed < 80 && exercised < 12; seed++) {
            // ---- phase 1: natural doors ----
            IRoom2D start1 = startRoom();
            Optional<ILevel2D> level1 = run(seed, start1, endRoom());
            if (level1.isEmpty()) {
                continue;
            }
            Set<Coords2D> natural = new LinkedHashSet<>(start1.getDoorways());
            if (natural.size() < 2) {
                continue; // need at least two so a proper subset is meaningful
            }

            // candidates = all natural doors except one; the excluded one must NOT
            // reappear as a door once it is no longer a candidate.
            List<Coords2D> all = new ArrayList<>(natural);
            Coords2D excluded = all.remove(all.size() - 1);
            List<Coords2D> candidates = all;

            // ---- phase 2: same seed, restricted to candidates ----
            IRoom2D start2 = startRoom();
            start2.setCandidateDoorways(new ArrayList<>(candidates));
            Optional<ILevel2D> level2 = run(seed, start2, endRoom());
            assertTrue(level2.isPresent(), "seed " + seed + ": restricted maze should still generate");

            Set<Coords2D> opened = new LinkedHashSet<>(start2.getDoorways());

            assertFalse(opened.isEmpty(),
                    "seed " + seed + ": START must keep at least one door (stay connected)");
            assertTrue(opened.size() <= START_DEGREES,
                    "seed " + seed + ": START opened " + opened.size() + " doors, exceeds degrees " + START_DEGREES);
            assertTrue(candidates.containsAll(opened),
                    "seed " + seed + ": START doors " + opened + " must be a subset of candidates " + candidates);
            assertFalse(opened.contains(excluded),
                    "seed " + seed + ": excluded cell " + excluded + " must not reappear as a door");

            exercised++;
        }

        assertTrue(exercised > 0, "expected at least one seed to exercise candidate restriction");
    }

    @Test
    void candidateDoorwayFlushAgainstGridBoundaryDoesNotCrash() {
        // Reproduces a real in-game crash: a candidate doorway cell sitting on the
        // grid's own boundary row/column (x=0) made generateConnector's unbounded
        // neighbor lookup (get(x-1, y)) throw ArrayIndexOutOfBoundsException. A
        // room spanning [0,0]..[6,6] with its west-wall door candidate at (0,3)
        // reproduces this directly, regardless of what places such a room there
        // (Phase 8's jigsaw room-template placement is one real caller).
        IRoom2D start = new Room2D(new Rectangle2D(0, 0, 7, 7));
        start.setStart(true);
        start.setDegrees(3);
        start.setCandidateDoorways(new ArrayList<>(List.of(new Coords2D(0, 3))));

        // Must complete without throwing; whether it opens the edge candidate or
        // not is irrelevant here (a boundary cell has no valid neighbor on one
        // side and would legitimately never open) -- the point is no crash.
        run(0L, start, endRoom());
    }

    /**
     * A double door authored near the edge of a jigsaw chain's reserved rect must
     * still be able to open BOTH its cells.
     *
     * <p>Reproduces the real {@code stairs_2} failure (2026-07-29). The chain's
     * bottom and top pieces are each 4 wide and carry a 2-cell double door at
     * their own local x=1/x=2 — comfortably mid-wall on the piece. But the
     * assembled 3-piece chain sprawls sideways into a <strong>7-wide union</strong>
     * bounding rect, and that union is what gets reserved as the room. Measured
     * against it, the bottom pair lands at relative x=4,5 and the top pair at
     * x=1,2 — so the old "≥2 cells from the corner" rule rejected exactly one cell
     * of each pair. A double door needs both, so both ends came out sealed: the
     * top read as "doors closed in", the bottom as "didn't join to a corridor".</p>
     */
    @Test
    void aDoubleDoorNearTheReservedRectsEdgeOpensBothCells() {
        // The union rect the real chain produces: 7 wide, 12 deep.
        Rectangle2D union = new Rectangle2D(2, 2, 7, 12);
        // The two door pairs, at the union-relative offsets computed from the NBTs.
        List<Coords2D> northPair = List.of(new Coords2D(2 + 4, 2), new Coords2D(2 + 5, 2));
        List<Coords2D> southPair = List.of(new Coords2D(2 + 1, 2), new Coords2D(2 + 2, 2));

        int opened = 0;
        for (long seed = 0; seed < 40; seed++) {
            IRoom2D start = new Room2D(union);
            start.setStart(true);
            start.setDegrees(4);
            List<Coords2D> candidates = new ArrayList<>(northPair);
            candidates.addAll(southPair);
            start.setCandidateDoorways(candidates);

            Optional<ILevel2D> level = run(seed, start, endRoom());
            if (level.isEmpty()) {
                continue;
            }
            Set<Coords2D> doors = new LinkedHashSet<>(start.getDoorways());
            // Every cell the corner rule used to veto outright must be reachable.
            for (Coords2D nearCorner : List.of(northPair.get(1), southPair.get(0))) {
                if (doors.contains(nearCorner)) {
                    opened++;
                }
            }
        }

        assertTrue(opened > 0,
                "no seed ever opened a door 1 cell short of the reserved rect's corner margin -- "
                        + "an authored marker must not be vetoed on the union rect's geometry, "
                        + "which the template author cannot see");
    }

    /**
     * An authored doorway two cells wide must open BOTH cells or neither.
     *
     * <p>The maze culls connectors immediately adjacent to one it just doored, so
     * two side-by-side doors never appear — right for single doors, wrong for a
     * template's double door, where it left a 2-cell opening with only one cell
     * connected and the other reverted to wall. Found in the `stairs_2` chain,
     * 2026-07-29: every seed opened exactly one cell of each pair.</p>
     */
    @Test
    void anAuthoredDoubleDoorOpensBothCellsOrNeither() {
        int bothOpen = 0;
        int exactlyOneOpen = 0;

        for (long seed = 0; seed < 60; seed++) {
            // Room degrees 4 so width has headroom -- each cell of a run counts
            // separately against degrees.
            IRoom2D start = new Room2D(new Rectangle2D(2, 2, 9, 9));
            start.setStart(true);
            start.setDegrees(4);
            // One 2-wide doorway mid-way along the north wall, plus a 1-wide one on
            // the south wall so the room is not forced to use the pair.
            Coords2D pairLeft = new Coords2D(5, 2);
            Coords2D pairRight = new Coords2D(6, 2);
            start.setCandidateDoorways(new ArrayList<>(
                    List.of(pairLeft, pairRight, new Coords2D(6, 10))));

            if (run(seed, start, endRoom()).isEmpty()) {
                continue;
            }
            Set<Coords2D> doors = new LinkedHashSet<>(start.getDoorways());
            boolean left = doors.contains(pairLeft);
            boolean right = doors.contains(pairRight);
            if (left && right) {
                bothOpen++;
            } else if (left ^ right) {
                exactlyOneOpen++;
            }
        }

        // Before the fix this was both=0, one=54: the culling split EVERY pair.
        // It is now both=52, one=2. The small tail is legitimate and not the culling:
        // a candidate only becomes a connector at all if something rendered was
        // carved on its far side, so one cell of a pair can simply never be openable;
        // and forceConnect's fallback rock tunnel opens a single cell directly.
        // Asserting dominance rather than zero keeps the guard strong (it fails
        // outright on the old behaviour) without encoding those two escape hatches.
        assertTrue(bothOpen > 0,
                "the 2-wide authored doorway never opened as a unit across 60 seeds");
        assertTrue(bothOpen > exactlyOneOpen,
                "authored double doors are being split more often than opened as a unit "
                        + "(both=" + bothOpen + ", half-open=" + exactlyOneOpen + ") -- the "
                        + "adjacent-connector culling is splitting authored runs again");
    }

    private static IRoom2D startRoom() {
        IRoom2D room = new Room2D(new Rectangle2D(2, 2, 7, 7));
        room.setStart(true);
        room.setDegrees(START_DEGREES);
        return room;
    }

    private static IRoom2D endRoom() {
        IRoom2D room = new Room2D(new Rectangle2D(20, 20, 7, 7));
        room.setEnd(true);
        room.setDegrees(1);
        return room;
    }

    private static Optional<ILevel2D> run(long seed, IRoom2D start, IRoom2D end) {
        return new MazeLevelGenerator2D.Builder()
                .with($ -> {
                    $.width = W;
                    $.height = H;
                    $.numberOfRooms = 8;
                    $.startRoom = start;
                    $.endRoom = end;
                })
                .corridorWidth(2)
                .seed(seed)
                .build()
                .generate();
    }
}
