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

import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfig;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #29, stage 1: {@code sinkOffset}, the derived {@code ceilingBudget}, and the invariant
 * that was holding by coincidence.
 *
 * <h2>What sinkOffset is, in one line</h2>
 * <p>The walking plane sits {@code sinkOffset} blocks UP into its own floor's slab, so the floor
 * owns {@code sinkOffset} blocks below it &mdash; the room a pit has to sink into (#3). The budget
 * is bought from the ceiling, not from the descent: {@code ceilingBudget = floorHeight -
 * sinkOffset}, while {@code pitch = floorHeight + gapBetweenFloors} does not move.</p>
 *
 * <h2>Why the pitch not moving is the whole point</h2>
 * <p>#3's original sketch bounded pit depth by {@code gapBetweenFloors}. Raising THAT to make room
 * lengthens every transition, and every shipped transition template is cut for a span of exactly 12
 * (#52). Buying the depth from the ceiling instead costs nothing in descent, so pits need no
 * template re-cut &mdash; which is what makes them separable from the floor-height raise rather
 * than blocked behind it.</p>
 *
 * <h2>The coincidence this pins</h2>
 * <p>{@code DEFAULT_FLOOR_HEIGHT} is 10 and {@code pickRoomHeight}'s roll maxes at 10. Those two
 * numbers were equal <em>by accident</em>, and nothing anywhere asserted the invariant they encode:
 * <strong>a room is never taller than the floor holding it.</strong> A room over budget puts its
 * ceiling through {@code gapBetweenFloors} and into the floor above, which nothing logs and nobody
 * sees until they walk into it.</p>
 *
 * <p>Runs the real planner headlessly &mdash; {@code surfaceY} is a plain int, so no world is
 * needed. Same harness as {@link FloorCountFitsTheWorldTest}.</p>
 */
class SinkOffsetAndCeilingBudgetTest {

    private static final int SURFACE_Y = 80;
    private static final int FLOOR_HEIGHT = 10;
    private static final int GAP = 2;

    private static Optional<DungeonLayout> plan(long seed, DungeonSize size, int sinkOffset) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), SURFACE_Y,
                "classic", new TemplateCatalog())
                .withSize(size)
                .withFloorCount(size.getMaxFloors())
                .withFloorHeight(FLOOR_HEIGHT)
                .withGapBetweenFloors(GAP)
                .withSinkOffset(sinkOffset)
                .withMinBuildY(-64)
                .plan();
    }

    private static List<FloorLayout> floors(long seed, DungeonSize size, int sinkOffset) {
        return plan(seed, size, sinkOffset).orElseThrow().getFloors();
    }

    // ---------- the arithmetic ----------

    /**
     * The load-bearing property: <strong>the drop between walking planes never moves.</strong> Pit
     * depth is free in DESCENT, so a pack may sink its floors without re-cutting a single
     * transition template &mdash; and this is the number a template has to span (#52).
     */
    @Test
    void theDropBetweenWalkingPlanesNeverMoves() {
        for (int sinkOffset = 0; sinkOffset <= 5; sinkOffset++) {
            List<FloorLayout> floors = floors(0xD2_29_0001L, DungeonSize.MEDIUM, sinkOffset);
            for (int i = 1; i < floors.size(); i++) {
                assertEquals(FLOOR_HEIGHT + GAP,
                        floors.get(i - 1).getFloorY() - floors.get(i).getFloorY(),
                        "sinkOffset " + sinkOffset + " changed the drop from floor " + (i - 1)
                                + " to floor " + i + "; it is bought from the ceiling, not from"
                                + " the descent");
            }
        }
    }

    /**
     * Floor 0 is the exception, and which way it goes depends on <strong>which end the entrance
     * anchors</strong>. There are two branches and they pin opposite ends:
     *
     * <ul>
     *   <li><strong>An assembled entrance</strong> &mdash; the real path in game &mdash; anchors
     *       floor 0's WALKING PLANE, because the entrance's bottom door marker <em>is</em> that
     *       plane. Sinking the floors leaves it exactly where it was and lowers the ceiling.</li>
     *   <li><strong>The empty-catalog fallback</strong>, which is what this headless harness gets,
     *       anchors floor 0's CEILING: the entrance chamber sits directly on top of it, so the
     *       ceiling stays put and the walking plane rises by the sink offset.</li>
     * </ul>
     *
     * <p>Both are right for their branch &mdash; each keeps the entrance meeting the thing it
     * actually joins. Worth pinning because it looks like an inconsistency until you ask which end
     * is nailed down, and because a future change to either branch should have to say so.</p>
     */
    @Test
    void floorZeroFollowsWhicheverEndTheEntranceAnchors() {
        for (int sinkOffset = 0; sinkOffset <= 5; sinkOffset++) {
            FloorLayout sunk = floors(0xD2_29_0001L, DungeonSize.MEDIUM, sinkOffset).get(0);
            FloorLayout flat = floors(0xD2_29_0001L, DungeonSize.MEDIUM, 0).get(0);

            assertEquals(flat.getCeilingY(), sunk.getCeilingY(),
                    "the fallback entrance sits on floor 0's ceiling, so the ceiling is the"
                            + " anchor and must not move");
            assertEquals(flat.getFloorY() + sinkOffset, sunk.getFloorY(),
                    "and the walking plane rises by exactly the sink offset");
        }
    }

    /** And the ceiling is what pays for it, one block per block. */
    @Test
    void theCeilingFallsByExactlyTheSinkOffset() {
        for (int sinkOffset = 0; sinkOffset <= 5; sinkOffset++) {
            List<FloorLayout> floors = floors(0xD2_29_0002L, DungeonSize.MEDIUM, sinkOffset);
            for (FloorLayout floor : floors) {
                assertEquals(FLOOR_HEIGHT - sinkOffset,
                        floor.getCeilingY() - floor.getFloorY() + 1,
                        "floor " + floor.getFloorIndex() + " at sinkOffset " + sinkOffset
                                + ": ceiling budget should be floorHeight - sinkOffset");
            }
        }
    }

    /**
     * The stone buffer is PRESERVED, not eaten. The stack measures the gap from the pit bottom
     * rather than from the walking plane, so floor {@code i-1}'s deepest possible pit still lands
     * {@code gapBetweenFloors} clear of floor {@code i}'s ceiling. Measuring from the walking plane
     * instead would open a pit straight into the room below at any {@code sinkOffset > gap}.
     */
    @Test
    void aPitBottomStaysAFullGapAboveTheCeilingBelow() {
        for (int sinkOffset = 0; sinkOffset <= 5; sinkOffset++) {
            List<FloorLayout> floors = floors(0xD2_29_0003L, DungeonSize.LARGE, sinkOffset);
            for (int i = 1; i < floors.size(); i++) {
                int pitBottom = floors.get(i - 1).getFloorY() - sinkOffset;
                int ceilingBelow = floors.get(i).getCeilingY();
                assertEquals(GAP, pitBottom - ceilingBelow - 1,
                        "sinkOffset " + sinkOffset + ", floors " + (i - 1) + "/" + i
                                + ": the buffer between the pit bottom and the ceiling below"
                                + " must stay gapBetweenFloors");
            }
        }
    }

    /** A floor owns exactly {@code floorHeight} blocks however the boundary inside it moves. */
    @Test
    void aFloorAlwaysOwnsExactlyFloorHeightBlocks() {
        for (int sinkOffset = 0; sinkOffset <= 5; sinkOffset++) {
            for (FloorLayout floor : floors(0xD2_29_0004L, DungeonSize.MEDIUM, sinkOffset)) {
                int owned = floor.getCeilingY() - (floor.getFloorY() - sinkOffset) + 1;
                assertEquals(FLOOR_HEIGHT, owned,
                        "floor " + floor.getFloorIndex() + " at sinkOffset " + sinkOffset);
            }
        }
    }

    // ---------- the invariant that was a coincidence ----------

    /**
     * <strong>No room is ever taller than its floor's ceiling budget.</strong> Swept rather than
     * spot-checked, because the failure is a ceiling punching into the floor above and there is
     * nothing to see at the seed where it does not happen.
     */
    @Test
    void noRoomIsEverTallerThanItsFloorsCeilingBudget() {
        int checked = 0;
        for (int sinkOffset = 0; sinkOffset <= 5; sinkOffset++) {
            int budget = FLOOR_HEIGHT - sinkOffset;
            for (long seed = 0; seed < 12; seed++) {
                for (DungeonSize size : DungeonSize.values()) {
                    Optional<DungeonLayout> planned = plan(0xD2_29_1000L + seed, size, sinkOffset);
                    if (planned.isEmpty()) {
                        continue;
                    }
                    for (FloorLayout floor : planned.get().getFloors()) {
                        for (RoomData room : floor.getRooms()) {
                            checked++;
                            assertTrue(room.getHeight() <= budget,
                                    "seed " + seed + " / " + size + " / floor "
                                            + floor.getFloorIndex() + ": a " + room.getHeight()
                                            + "-high room in a budget of " + budget
                                            + " puts its ceiling into the floor above");
                        }
                    }
                }
            }
        }
        assertTrue(checked > 0, "no rooms were examined, so this asserted nothing");
    }

    /**
     * The clamp is a CLAMP, not a re-roll &mdash; the same call {@code RoomHeightBand} makes, and
     * for the same reason. {@code 5 + nextInt(6)} consumes an identical amount of the stream
     * whatever budget it lands in, so sinking the floors moves heights and nothing else: the maze,
     * the footprints and the corridors of every seed come out where they were.
     */
    @Test
    void sinkingTheFloorsMovesHeightsAndNothingElse() {
        List<FloorLayout> flat = floors(0xD2_29_0005L, DungeonSize.MEDIUM, 0);
        List<FloorLayout> sunk = floors(0xD2_29_0005L, DungeonSize.MEDIUM, 4);

        assertEquals(flat.size(), sunk.size(), "the floor count moved");
        for (int i = 0; i < flat.size(); i++) {
            List<RoomData> flatRooms = flat.get(i).getRooms();
            List<RoomData> sunkRooms = sunk.get(i).getRooms();
            assertEquals(flatRooms.size(), sunkRooms.size(), "floor " + i + "'s room count moved");
            for (int r = 0; r < flatRooms.size(); r++) {
                RoomData a = flatRooms.get(r);
                RoomData b = sunkRooms.get(r);
                assertEquals(a.getWidth(), b.getWidth(), "floor " + i + " room " + r + " width");
                assertEquals(a.getDepth(), b.getDepth(), "floor " + i + " room " + r + " depth");
                assertEquals(a.getOriginX(), b.getOriginX(), "floor " + i + " room " + r + " originX");
                assertEquals(a.getOriginZ(), b.getOriginZ(), "floor " + i + " room " + r + " originZ");
            }
        }
    }

    /**
     * A planner built without a config measures the dungeon that SHIPS. Its {@code sinkOffset}
     * field defaults to {@code DEFAULT_SINK_OFFSET} for the same reason {@code floorHeight} and
     * {@code gapBetweenFloors} default to theirs &mdash; the probes, the floor-plan exporter and
     * most tests construct a planner that way, and a default of 0 would have quietly measured a
     * differently-shaped dungeon than the one a player gets.
     */
    @Test
    void aPlannerThatNamesNoSinkOffsetUsesTheShippedOne() {
        DungeonLayout untouched = new DungeonStackPlanner(0xD2_29_0006L, new Coords(0, 0, 0),
                SURFACE_Y, "classic", new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withFloorCount(DungeonSize.MEDIUM.getMaxFloors())
                .withFloorHeight(FLOOR_HEIGHT)
                .withGapBetweenFloors(GAP)
                .withMinBuildY(-64)
                .plan().orElseThrow();

        List<FloorLayout> explicit = floors(0xD2_29_0006L, DungeonSize.MEDIUM,
                DungeonGenerationConfig.DEFAULT_SINK_OFFSET);
        for (int i = 0; i < untouched.getFloors().size(); i++) {
            FloorLayout a = untouched.getFloors().get(i);
            FloorLayout b = explicit.get(i);
            assertEquals(a.getFloorY(), b.getFloorY(), "floor " + i + " walking plane");
            assertEquals(a.getCeilingY(), b.getCeilingY(), "floor " + i + " ceiling");
        }
    }
}
