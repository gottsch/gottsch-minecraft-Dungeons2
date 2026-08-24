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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same measure-before-reserve question as
 * {@link TransitionAssemblyPlacementTest}, for Phase 8 interior room prefabs.
 *
 * <p>A room is a single piece, so it can't sprawl the way a transition chain does
 * — but vanilla still rotates it, and rotation moves the bounding box's min corner
 * off the position the planner asked for. Every one of the four shipped prefabs is
 * 7x7, so three of the four rotations displace the footprint 6 blocks west and/or
 * north of the assembly point. A slot whose real footprint lands out of bounds is
 * silently dropped and covered by an ordinary procedural fill room, so the loss is
 * invisible in game — only measurable.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
class RoomAssemblyPlacementTest {

    private static final int SEEDS = 200;
    private static final int FLOORS = 3;
    /** The planner's own default, which this test drives explicitly rather than relying on. */
    private static final int ATTEMPTS_PER_FLOOR = 2;
    /** What {@code generation_config/default.json} ships (backlog #16). */
    private static final int SHIPPED_ATTEMPTS_PER_FLOOR = 4;

    @Test
    void aRotatedRoomPrefabIsAlmostAlwaysAdopted() {
        assertAdoptionHolds(ATTEMPTS_PER_FLOOR);
    }

    /**
     * Adoption has to survive the count the mod actually ships, not just the planner's default.
     * More attempts per floor compete for the same floor area &mdash; each adopted prefab reserves
     * its footprint against the next attempt &mdash; so a rate measured at 2 says nothing about 4.
     * This is the check that would catch raising the shipped number too far.
     */
    @Test
    void adoptionSurvivesTheShippedAttemptCount() {
        assertAdoptionHolds(SHIPPED_ATTEMPTS_PER_FLOOR);
    }

    private void assertAdoptionHolds(int attemptsPerFloor) {
        int adopted = 0;
        int normalRooms = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Optional<DungeonLayout> opt = new DungeonStackPlanner(
                    seed, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    .withFloorCount(FLOORS)
                    .withRoomTemplateAttempts(attemptsPerFloor)
                    .withRoomAssembler(ROTATED_7X7)
                    .plan();
            if (opt.isEmpty()) {
                continue;
            }
            for (FloorLayout floor : opt.get().getFloors()) {
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() == RoomRole.NORMAL) {
                        normalRooms++;
                    }
                    if (room.getTemplateId() == null || !room.getTemplateId().contains("rooms/assembled")) {
                        continue;
                    }
                    adopted++;
                    // The slot is reserved ROOM_EDGE_MARGIN clear of the boundary, so
                    // no adopted prefab may sit flush against it -- a door candidate
                    // on such a room's edge lands on the grid's boundary row/column.
                    assertTrue(room.getOriginX() > 0 && room.getOriginZ() > 0
                                    && room.getOriginX() + room.getWidth() < floor.getFootprint().getWidth()
                                    && room.getOriginZ() + room.getDepth() < floor.getFootprint().getHeight(),
                            "an adopted prefab must stay clear of the floor's own boundary");
                }
            }
        }

        int slots = SEEDS * FLOORS * attemptsPerFloor;
        double rate = (double) adopted / slots;
        System.out.printf("room-prefab adoption at %d attempts/floor: %d of %d (%.1f%%), "
                        + "%.1f prefabs and %.1f rooms per %d-floor dungeon -> %.1f%% prefab share%n",
                attemptsPerFloor, adopted, slots, rate * 100, (double) adopted / SEEDS,
                (double) normalRooms / SEEDS, FLOORS, 100.0 * adopted / normalRooms);
        assertTrue(rate >= 0.90, String.format(
                "only %.0f%% of room-prefab slots were filled (%d of %d) at %d attempts/floor -- a "
                        + "rotated prefab's real footprint sits up to 6 blocks west/north of the "
                        + "assembly point, so a slot picked before the footprint is known lands out "
                        + "of bounds and is dropped",
                rate * 100, adopted, slots, attemptsPerFloor));
    }

    /**
     * Where a 7x7 piece's bounding-box min corner lands relative to the position it
     * was assembled at, per rotation. Vanilla rotates about the anchor, so e.g.
     * CLOCKWISE_90 maps {@code (x,z) -> (-z,x)} and the piece's {@code x 0..6}
     * becomes {@code x -6..0}.
     */
    private static final int[][] ROTATION_OFFSET = {
            {0, 0},    // NONE
            {-6, 0},   // CW 90
            {-6, -6},  // 180
            {0, -6},   // CCW 90
    };


    /**
     * Models a real shipped room prefab: 7x7 (all four of
     * {@code rooms/classic/*.nbt} are), rotated one of four ways, with mid-wall
     * {@code dungeons2:door} markers on each side like {@code 7x7_junction_1}.
     *
     * <p>Because the piece is square with symmetric doors, the marker cells stay at
     * the same offsets from the footprint's min corner under rotation; what rotation
     * changes is where that min corner lands relative to the assembly point.</p>
     */
    private static final DungeonStackPlanner.RoomAssembler ROTATED_7X7 =
            (wx, wy, wz, floorIndex, seed, commit) -> {
                int[] o = ROTATION_OFFSET[Math.floorMod(new java.util.Random(seed).nextInt(), 4)];
                Rectangle2D fp = new Rectangle2D(wx + o[0], wz + o[1], 7, 7);
                int minX = fp.getMinX();
                int minZ = fp.getMinY();
                return Optional.of(new DungeonStackPlanner.AssembledRoom(fp,
                        List.of(new Coords2D(minX, minZ + 3), new Coords2D(minX + 3, minZ),
                                new Coords2D(minX + 3, minZ + 6), new Coords2D(minX + 6, minZ + 3)),
                        List.of()));
            };


    /**
     * #45 step 3: the planner asks for a room with the <strong>floor's own index</strong>.
     *
     * <p>This is the fault the parameter exists to prevent, and it is why {@code floorIndex} is a
     * parameter rather than a setter on the assembler ({@code #10}'s rule). The implementation
     * resolves the rooms pool through the motif's stratum for that depth, so a value stuck at 0
     * would assemble every floor of the dungeon out of the entrance floor's rooms &mdash; silently,
     * correctly-looking, and only for packs that author strata.
     *
     * <p>Asserts both directions: every index the planner asked for is a real floor of the plan,
     * and more than one distinct index was asked for at all. Without the second half the test would
     * pass on a planner that only ever asked for floor 0.
     */
    @Test
    void theAssemblerIsToldWhichFloorItIsAssemblingFor() {
        java.util.Set<Integer> asked = new java.util.HashSet<>();
        DungeonStackPlanner.RoomAssembler recording = (wx, wy, wz, floorIndex, seed, commit) -> {
            asked.add(floorIndex);
            return ROTATED_7X7.assemble(wx, wy, wz, floorIndex, seed, commit);
        };

        Optional<DungeonLayout> opt = new DungeonStackPlanner(
                7L, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withFloorCount(FLOORS)
                .withRoomTemplateAttempts(SHIPPED_ATTEMPTS_PER_FLOOR)
                .withRoomAssembler(recording)
                .plan();

        assertTrue(opt.isPresent(), "the fixture seed must produce a plan for this to mean anything");
        java.util.Set<Integer> floors = opt.get().getFloors().stream()
                .map(FloorLayout::getFloorIndex).collect(java.util.stream.Collectors.toSet());
        assertTrue(floors.containsAll(asked),
                "the planner asked for floors " + asked + " but the plan only has " + floors);
        assertTrue(asked.size() > 1,
                "only floor " + asked + " was ever asked for, so this test could not tell a real"
                        + " floorIndex from a hardcoded 0");
    }
}
