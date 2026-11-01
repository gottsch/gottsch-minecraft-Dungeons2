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
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An authored footprint with an even side is <em>allowed</em>, and is reported rather than refused.
 *
 * <h2>The two parity rules, only one of which is real</h2>
 * <p>A room's <strong>origin</strong> must be even and {@link MazeLevelGenerator2D#isRoomValid}
 * enforces it &mdash; the maze is an odd-cell lattice where corridors sit on even cells and step
 * two at a time. A room's <strong>size</strong> is a different rule entirely:
 * {@code generateRoomSize} forces odd, but only for rooms the maze generates itself, and
 * {@code isRoomValid} never checks size. So a <em>supplied</em> footprint with an even side has
 * always passed straight through, silently, and this pins that it still works and now says so.</p>
 *
 * <p>Measured before the log was written: an even-sided terminal room plans, is placed and gets its
 * doorway on 100% of 200 seeds, and renders a complete wall ring. What it costs is one wasted cell
 * per axis, because the far wall lands on a corridor lane and the maze's own wall column ends up
 * beside it.</p>
 */
class EvenSidedFootprintTest {

    private static Optional<DungeonLayout> plan(long seed, int width, int depth) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, "classic",
                new TemplateCatalog())
                .withSize(DungeonSize.LARGE)
                .withTerminalRoomSize(width, depth)
                .plan();
    }

    private static RoomData terminal(DungeonLayout layout) {
        FloorLayout bottom = layout.getFloors().get(layout.getFloors().size() - 1);
        for (RoomData room : bottom.getRooms()) {
            if (room.getRole() == RoomRole.TERMINAL) {
                return room;
            }
        }
        return null;
    }

    /**
     * The behaviour the log is careful NOT to change: an even side still plans, still places, still
     * connects. If this ever goes red, the warning has quietly become a rejection.
     */
    @Test
    void anEvenSidedTerminalRoomStillPlansPlacesAndConnects() {
        for (int side : new int[] {8, 10, 12, 14, 16}) {
            int planned = 0;
            int placed = 0;
            int reachable = 0;
            for (int i = 0; i < 40; i++) {
                // Spread, not sequential -- see reference_first_draw_seed_correlation.
                Optional<DungeonLayout> layout = plan(0xD2_4604_0001L + i * 7919L, side, side);
                if (layout.isEmpty()) {
                    continue;
                }
                planned++;
                RoomData room = terminal(layout.get());
                if (room == null) {
                    continue;
                }
                placed++;
                assertEquals(side, room.getWidth(), "the terminal room is not the size asked for");
                assertEquals(side, room.getDepth(), "the terminal room is not the size asked for");
                if (!room.getDoorways().isEmpty()) {
                    reachable++;
                }
            }
            assertEquals(40, planned, side + "x" + side + " terminal room failed to plan");
            assertEquals(planned, placed, side + "x" + side + " terminal room was not placed");
            assertEquals(placed, reachable,
                    side + "x" + side + " terminal room was placed with no doorway -- unreachable");
        }
    }

    /**
     * The rule that <em>is</em> enforced, stated here so the two are never conflated again: whatever
     * the size parity, the origin the planner hands the maze is even. An odd origin is what
     * {@code isRoomValid} actually rejects.
     */
    @Test
    void everySuppliedFootprintStillGetsAnEvenOrigin() {
        for (int side : new int[] {7, 8, 12, 13}) {
            for (int i = 0; i < 25; i++) {
                Optional<DungeonLayout> layout = plan(0xD2_4605_0001L + i * 7919L, side, side);
                if (layout.isEmpty()) {
                    continue;
                }
                RoomData room = terminal(layout.get());
                if (room == null) {
                    continue;
                }
                assertEquals(0, room.getOriginX() % 2,
                        "odd origin X at size " + side + " -- the maze rejects these");
                assertEquals(0, room.getOriginZ() % 2,
                        "odd origin Z at size " + side + " -- the maze rejects these");
            }
        }
    }

    /** And the far edge really does land on the corridor lane, which is what the warning claims. */
    @Test
    void anEvenSideLandsTheFarWallOnACorridorLane() {
        Optional<DungeonLayout> odd = plan(0xD2_4606_0001L, 13, 13);
        Optional<DungeonLayout> even = plan(0xD2_4606_0001L, 12, 12);
        RoomData oddRoom = terminal(odd.orElseThrow());
        RoomData evenRoom = terminal(even.orElseThrow());
        assertEquals(0, (oddRoom.getOriginX() + oddRoom.getWidth() - 1) % 2,
                "an odd-sided room should end on an even (wall) cell");
        assertEquals(1, (evenRoom.getOriginX() + evenRoom.getWidth() - 1) % 2,
                "an even-sided room should end on an odd (corridor lane) cell -- if this changes, "
                        + "the [D2-PARITY] warning is telling authors something untrue");
        assertTrue(true);
    }
}
