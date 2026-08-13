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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reproduces the ASSEMBLED-ENTRANCE path (Phase 4b) that the synthetic-fallback
 * voxel test never exercised. The door-carrier's XZ bbox (7x7) becomes the
 * reserved floor-0 START footprint and its dungeons2:door markers become the
 * START room's candidate doorways.
 *
 * <p>The geometry below is a hand-built stub, not a read of the shipped templates.
 * It was modelled on the two-piece entrance of the time (a 3x1x5 cap over a 7x7x15
 * octagonal door-carrier); the shipped chain has been three pieces since
 * 2026-07-30 and those two files were deleted on 2026-08-13. The stub still
 * exercises the path it was written for, since what it asserts is how the planner
 * consumes an assembled entrance's markers, not which templates supplied them.</p>
 */
class AssembledEntranceStubTest {

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

    private static boolean air(Map<Long, String> vox, int x, int y, int z) {
        String b = vox.get(key(x, y, z));
        return b != null && b.equals(AIR);
    }

    @Test
    void assembledEntranceFloorHasNoStubs() {
        int stubTips = 0;
        long firstBadSeed = -1;
        String example = null;

        for (long seed = 0; seed < 120; seed++) {
            // Vary the entrance world origin the way real chunk placement does.
            int eMinX = 96 + (int) ((seed * 2) % 32);
            int eMinZ = 192 + (int) ((seed * 3) % 32);
            int floor0Y = 64;
            Rectangle2D entranceRect = new Rectangle2D(eMinX, eMinZ, 7, 7);
            // Octagon flat-side mid-edge door markers (W, N, E), like descent_1.
            List<Coords2D> doorCells = new ArrayList<>();
            doorCells.add(new Coords2D(eMinX, eMinZ + 3));       // west
            doorCells.add(new Coords2D(eMinX + 3, eMinZ));       // north
            doorCells.add(new Coords2D(eMinX + 6, eMinZ + 3));   // east

            DungeonStackPlanner planner = new DungeonStackPlanner(
                    seed, new Coords(eMinX + 3, 0, eMinZ + 3), 72, MOTIF, new TemplateCatalog())
                    .withSize(DungeonSize.LARGE).withFloorCount(1)
                    .withAssembledEntrance(entranceRect, doorCells, floor0Y);

            var opt = planner.plan();
            if (opt.isEmpty()) continue;
            DungeonLayout layout = opt.get();
            int anchorX = layout.getAnchor().getX();
            int anchorZ = layout.getAnchor().getZ();
            FloorLayout f0 = layout.getFloors().get(0);
            int floorY = f0.getFloorY();

            Map<Long, String> vox = new HashMap<>();
            for (RoomData room : f0.getRooms()) {
                if (room.getRole() != RoomRole.NORMAL) continue;
                stamp(vox, new DungeonRoomPiece(room, MOTIF, floorY, anchorX, anchorZ).renderPlacements());
            }
            for (CorridorData corridor : f0.getCorridors()) {
                stamp(vox, new DungeonCorridorPiece(corridor, MOTIF, floorY, anchorX, anchorZ).renderPlacements());
            }
            for (DoorData door : f0.getDoors()) {
                stamp(vox, new DungeonDoorPiece(door, MOTIF, floorY, anchorX, anchorZ).renderPlacements());
            }

            // Locate the START room footprint (grid-local -> world XZ).
            RoomData start = null;
            for (RoomData r : f0.getRooms()) {
                if (r.getRole() == RoomRole.START) { start = r; break; }
            }
            if (start == null) continue;
            int sMinX = anchorX + start.getOriginX();
            int sMinZ = anchorZ + start.getOriginZ();
            int sMaxX = sMinX + start.getWidth() - 1;
            int sMaxZ = sMinZ + start.getDepth() - 1;

            // Any procedural SOLID block landing inside the START footprint (its
            // interior OR perimeter) intrudes into the entrance template's space --
            // the template renders that area, so a procedural wall/floor there is a
            // stub/conflict. Count them.
            for (Map.Entry<Long, String> e : vox.entrySet()) {
                if (e.getValue().equals(AIR)) continue;
                long k = e.getKey();
                int x = (int) (k & 0x1FFFFF);
                int y = (int) ((k >> 21) & 0x1FFFFF);
                int z = (int) ((k >> 42) & 0x1FFFFF);
                if (y <= floorY) continue;
                if (x >= sMinX && x <= sMaxX && z >= sMinZ && z <= sMaxZ) {
                    stubTips++;
                    if (firstBadSeed < 0) firstBadSeed = seed;
                    if (example == null) {
                        example = "seed " + seed + " solid @ (" + x + "," + y + "," + z + ") "
                                + e.getValue() + " inside START [" + sMinX + ".." + sMaxX + "]x["
                                + sMinZ + ".." + sMaxZ + "]";
                    }
                }
            }
        }
        // Procedural pieces on the assembled-entrance floor must never render a
        // solid block inside the reserved START footprint (the entrance template
        // owns that space). If this ever fails, the procedural side IS intruding on
        // the entrance and the stub is reproducible here without the game client.
        assertEquals(0, stubTips,
                "procedural pieces intruded into the assembled START footprint; found "
                        + stubTips + ", first at " + example);
    }

    private static void stamp(Map<Long, String> vox, List<BlockPlacement> placements) {
        for (BlockPlacement p : placements) {
            vox.put(key(p.getX(), p.getY(), p.getZ()), p.getBlockId());
        }
    }
}
