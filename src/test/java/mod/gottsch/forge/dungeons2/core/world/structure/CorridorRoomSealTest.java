/*
 * Diagnostic (Jul 22): prove — via the REAL render pieces (real CorridorData
 * wallCells, not a grid reconstruction) — that on every floor of a multi-floor
 * dungeon, no corridor is reachable from a room interior except through a door.
 * If this holds, the in-game "corridor through a room" is NOT geometric.
 */
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorridorRoomSealTest {

    /** Every piece here is built on the entrance floor; depth is not what these cases are about. */
    private static final int TEST_FLOOR_INDEX = 0;

    private static final String MOTIF = "classic";
    private static final String AIR = "minecraft:air";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF)) | ((long) (y & 0x1FFFFF) << 21) | ((long) (z & 0x1FFFFF) << 42);
    }

    @Test
    void noCorridorReachesRoomInteriorExceptThroughDoors() {
        assertNoBreaches(1);
    }

    /**
     * Re-tests the Jul 21 "dilation merges parallel corridors into a room-touching
     * blob" theory through the REAL render pieces now that the Jul 23 Z-mirror
     * render bug (which corrupted every in-game observation about corridor width)
     * is fixed. Still exercises {@code renderPlacements()} rather than
     * {@code DungeonPiece.placeAll}'s world-coordinate translation, so it cannot
     * see the Z-mirror class of bug itself &mdash; only whether dilation is
     * geometrically safe on its own terms.
     */
    @Test
    void dilatedCorridorsStillDoNotReachRoomInteriors() {
        assertNoBreaches(2);
        assertNoBreaches(3);
    }

    private void assertNoBreaches(int corridorWidth) {
        int totalBreaches = 0;
        String firstExample = null;
        DungeonSize[] sizes = {DungeonSize.SMALL, DungeonSize.MEDIUM, DungeonSize.LARGE};

        for (long seed = 0; seed < 120; seed++) {
            DungeonSize size = sizes[(int) (seed % sizes.length)];
            DungeonLayout layout = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 96, MOTIF, new TemplateCatalog())
                    .withSize(size).withFloorCount(3)
                    .withCorridorWidth(corridorWidth)
                    .plan().orElseThrow();

            for (FloorLayout floor : layout.getFloors()) {
                int floorY = floor.getFloorY();

                // Composite the real render, last-writer-wins in emitter order.
                Map<Long, String> vox = new HashMap<>();
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() != RoomRole.NORMAL) continue;
                    stamp(vox, new DungeonRoomPiece(room, MOTIF, floorY, TEST_FLOOR_INDEX, 0, 0).renderPlacements());
                }
                for (CorridorData corridor : floor.getCorridors()) {
                    stamp(vox, new DungeonCorridorPiece(corridor, MOTIF, floorY, TEST_FLOOR_INDEX, 0, 0).renderPlacements());
                }
                for (DoorData door : floor.getDoors()) {
                    stamp(vox, new DungeonDoorPiece(door, MOTIF, floorY, TEST_FLOOR_INDEX, 0, 0).renderPlacements());
                }

                // Seal every door column solid so only NON-door openings can leak.
                for (DoorData door : floor.getDoors()) {
                    for (int y = floorY; y <= floorY + 4; y++) {
                        vox.put(key(door.getX(), y, door.getZ()), "sealed");
                    }
                }

                // Room-interior-air voxels (strict interior XZ, air).
                Set<Long> roomAir = new HashSet<>();
                Map<Long, Integer> roomOf = new HashMap<>();
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() != RoomRole.NORMAL) continue;
                    for (int x = room.getOriginX() + 1; x < room.getOriginX() + room.getWidth() - 1; x++) {
                        for (int z = room.getOriginZ() + 1; z < room.getOriginZ() + room.getDepth() - 1; z++) {
                            for (int y = floorY + 1; y < floorY + room.getHeight() - 1; y++) {
                                long k = key(x, y, z);
                                if (AIR.equals(vox.get(k))) {
                                    roomAir.add(k);
                                    roomOf.put(k, room.getId());
                                }
                            }
                        }
                    }
                }

                // Flood air (6-connected) from every corridor interior-air voxel.
                Deque<long[]> stack = new ArrayDeque<>();
                Set<Long> seen = new HashSet<>();
                for (CorridorData corridor : floor.getCorridors()) {
                    for (Coords2D c : corridor.getCells()) {
                        for (int y = floorY + 1; y <= floorY + 3; y++) {
                            long k = key(c.getX(), y, c.getY());
                            if (AIR.equals(vox.get(k)) && seen.add(k)) {
                                stack.push(new long[]{c.getX(), y, c.getY()});
                            }
                        }
                    }
                }
                int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                List<Long> breaches = new ArrayList<>();
                while (!stack.isEmpty()) {
                    long[] p = stack.pop();
                    for (int[] d : dirs) {
                        int nx = (int) p[0] + d[0], ny = (int) p[1] + d[1], nz = (int) p[2] + d[2];
                        long nk = key(nx, ny, nz);
                        if (seen.contains(nk)) continue;
                        if (AIR.equals(vox.get(nk))) {
                            seen.add(nk);
                            stack.push(new long[]{nx, ny, nz});
                            if (roomAir.contains(nk)) breaches.add(nk);
                        }
                    }
                }

                if (!breaches.isEmpty()) {
                    totalBreaches += breaches.size();
                    if (firstExample == null) {
                        long k = breaches.get(0);
                        int x = (int) (k & 0x1FFFFF);
                        int y = (int) ((k >> 21) & 0x1FFFFF);
                        int z = (int) ((k >> 42) & 0x1FFFFF);
                        firstExample = "seed " + seed + " (" + size + ") floor " + floor.getFloorIndex()
                                + " room " + roomOf.get(k) + " breached at (" + x + "," + y + "," + z + ")";
                    }
                }
            }
        }

        assertEquals(0, totalBreaches,
                "corridorWidth=" + corridorWidth + ": with doors sealed, corridors must not reach any "
                        + "room interior; found " + totalBreaches + " breach voxel(s), first: " + firstExample);
    }

    private static void stamp(Map<Long, String> vox, List<BlockPlacement> placements) {
        for (BlockPlacement p : placements) {
            vox.put(key(p.getX(), p.getY(), p.getZ()), p.getBlockId());
        }
    }
}
