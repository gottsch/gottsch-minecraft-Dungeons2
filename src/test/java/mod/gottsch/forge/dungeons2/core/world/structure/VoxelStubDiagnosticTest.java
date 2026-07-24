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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 3D regression guard: renders the full procedural block map of floor 0
 * (NORMAL rooms + corridors + doors, in {@link DungeonPieceEmitter} order,
 * last-writer-wins) and asserts there are no free-standing "wall stub" blocks
 * &mdash; a solid block standing in open, walkable air inside the dungeon.
 *
 * <p>The stub the user reported ("a ~3-block-tall wall segment with floor
 * around it, not reaching the ceiling") is detected here as a solid block that
 * has AIR directly above it and AIR on 3+ of its 4 horizontal sides: the end of
 * a wall run protruding into open space. Its 2D companion is
 * {@code RoomCorridorAdjacencyTest} (grid-level); this one catches artifacts
 * that only appear once the independent piece renders are composited in 3D.</p>
 *
 * <p>Finding: across all sizes and many seeds the procedural render is clean
 * (0 stubs). Any wall stub seen in-game therefore originates in the authored
 * entrance template / jigsaw seam, not the procedural maze.</p>
 */
class VoxelStubDiagnosticTest {

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
    void proceduralRenderHasNoFreestandingWallStubs() {
        int stubTips = 0;
        long firstBadSeed = -1;
        String example = null;
        DungeonSize[] sizes = {DungeonSize.SMALL, DungeonSize.MEDIUM, DungeonSize.LARGE};
        for (long seed = 0; seed < 90; seed++) {
            DungeonSize size = sizes[(int) (seed % sizes.length)];
            DungeonLayout layout = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 72, MOTIF, new TemplateCatalog())
                    .withSize(size).withFloorCount(1)
                    .plan().orElseThrow();
            FloorLayout f0 = layout.getFloors().get(0);
            int floorY = f0.getFloorY();

            Map<Long, String> vox = new HashMap<>();
            // Emit order mirrors DungeonPieceEmitter: rooms, corridors, doors.
            for (RoomData room : f0.getRooms()) {
                if (room.getRole() != RoomRole.NORMAL) continue;
                stamp(vox, new DungeonRoomPiece(room, MOTIF, floorY, 0, 0).renderPlacements());
            }
            for (CorridorData corridor : f0.getCorridors()) {
                stamp(vox, new DungeonCorridorPiece(corridor, MOTIF, floorY, 0, 0).renderPlacements());
            }
            for (DoorData door : f0.getDoors()) {
                stamp(vox, new DungeonDoorPiece(door, MOTIF, floorY, 0, 0).renderPlacements());
            }

            for (Map.Entry<Long, String> e : vox.entrySet()) {
                if (e.getValue().equals(AIR)) continue;
                long k = e.getKey();
                int x = (int) (k & 0x1FFFFF);
                int y = (int) ((k >> 21) & 0x1FFFFF);
                int z = (int) ((k >> 42) & 0x1FFFFF);
                if (y <= floorY) continue; // floor plane is legitimately solid
                // Wall stub: solid block with AIR directly above (not reaching a
                // ceiling) and 3+ horizontal AIR sides (end of a wall run poking
                // into open, walkable space).
                if (!air(vox, x, y + 1, z)) continue;
                int airSides = 0;
                if (air(vox, x + 1, y, z)) airSides++;
                if (air(vox, x - 1, y, z)) airSides++;
                if (air(vox, x, y, z + 1)) airSides++;
                if (air(vox, x, y, z - 1)) airSides++;
                if (airSides >= 3) {
                    stubTips++;
                    if (firstBadSeed < 0) firstBadSeed = seed;
                    if (example == null) {
                        example = "seed " + seed + " (" + size + ") @ ("
                                + x + "," + y + "," + z + ") airSides=" + airSides;
                    }
                }
            }
        }
        assertEquals(0, stubTips,
                "procedural render must have no free-standing wall stubs; found "
                        + stubTips + ", first at " + example);
    }

    private static void stamp(Map<Long, String> vox, List<BlockPlacement> placements) {
        for (BlockPlacement p : placements) {
            vox.put(key(p.getX(), p.getY(), p.getZ()), p.getBlockId());
        }
    }
}
