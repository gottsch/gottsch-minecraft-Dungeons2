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

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
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
    /** {@code ROOM_TEMPLATE_ATTEMPTS_PER_FLOOR} in the planner. */
    private static final int ATTEMPTS_PER_FLOOR = 2;

    @Test
    void aRotatedRoomPrefabIsAlmostAlwaysAdopted() {
        int adopted = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Optional<DungeonLayout> opt = new DungeonStackPlanner(
                    seed, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    .withFloorCount(FLOORS)
                    .withRoomAssembler(ROTATED_7X7)
                    .plan();
            if (opt.isEmpty()) {
                continue;
            }
            for (FloorLayout floor : opt.get().getFloors()) {
                for (RoomData room : floor.getRooms()) {
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

        int slots = SEEDS * FLOORS * ATTEMPTS_PER_FLOOR;
        double rate = (double) adopted / slots;
        assertTrue(rate >= 0.90, String.format(
                "only %.0f%% of room-prefab slots were filled (%d of %d) -- a rotated prefab's real "
                        + "footprint sits up to 6 blocks west/north of the assembly point, so a slot "
                        + "picked before the footprint is known lands out of bounds and is dropped",
                rate * 100, adopted, slots));
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
            (wx, wy, wz, seed, commit) -> {
                int[] o = ROTATION_OFFSET[Math.floorMod(new java.util.Random(seed).nextInt(), 4)];
                Rectangle2D fp = new Rectangle2D(wx + o[0], wz + o[1], 7, 7);
                int minX = fp.getMinX();
                int minZ = fp.getMinY();
                return Optional.of(new DungeonStackPlanner.AssembledRoom(fp,
                        List.of(new Coords2D(minX, minZ + 3), new Coords2D(minX + 3, minZ),
                                new Coords2D(minX + 3, minZ + 6), new Coords2D(minX + 6, minZ + 3)),
                        List.of()));
            };

}
