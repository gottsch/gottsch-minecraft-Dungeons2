/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link BasicWallGenerator} emits a deterministic, well-bounded
 * list of {@link BlockPlacement}s for a single {@link RoomData}.
 *
 * <p>Requires Minecraft's {@link Bootstrap} init so {@code Blocks.STONE_BRICKS}
 * and friends resolve. The bootstrap is fast in test (~1 sec) and is needed
 * by any builder that touches a {@code BlockState}.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
class BasicWallGeneratorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // Minecraft's static block registries are populated by Bootstrap.bootStrap().
        // Without this, Blocks.STONE_BRICKS et al. are null and the codec NPEs.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private RoomData smallRoom() {
        // A 7x7 room, 5 blocks tall, anchored at (10, 10) in floor-local coords.
        return new RoomData(1, 10, 10, 7, 7, 5, RoomRole.NORMAL);
    }

    @Test
    void emitsExpectedCountForSmallRoom() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        // 7x7 room, height=5, walls at y=1..3 (3 layers).
        // 2 x-edges * depth=7 * 3 = 42, plus 2 z-edges * width=7 * 3 = 42 = 84.
        // The four corner columns are emitted TWICE -- once by each loop -- so 12 of those 84 are
        // duplicates that the renderer resolves last-write-wins. Harmless for a uniform wall;
        // it is a real constraint for any wall pattern with horizontal rhythm, which will need a
        // corner-ownership convention rather than relying on the second write matching the first.
        //
        // Interior air is NOT here: it belongs to RoomVolumeGenerator (75 placements for this
        // room, asserted in RoomVolumeGeneratorTest).
        assertEquals(84, out.size(),
                "Expected 84 wall placements (corners double-emitted) for a 7x7x5 room");
    }

    @Test
    void coordsStayWithinRoomFootprint() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        RoomData room = smallRoom();
        int floorY = 60;
        gen.build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        int minX = room.getOriginX();
        int maxX = room.getOriginX() + room.getWidth() - 1;
        int minZ = room.getOriginZ();
        int maxZ = room.getOriginZ() + room.getDepth() - 1;
        int minY = floorY + 1; // walls start above the floor
        int maxY = floorY + room.getHeight() - 2; // and below the ceiling

        for (BlockPlacement bp : out) {
            assertTrue(bp.getX() >= minX && bp.getX() <= maxX,
                    "X out of room bounds: " + bp);
            assertTrue(bp.getZ() >= minZ && bp.getZ() <= maxZ,
                    "Z out of room bounds: " + bp);
            assertTrue(bp.getY() >= minY && bp.getY() <= maxY,
                    "Y out of wall-layer bounds: " + bp);
        }
    }

    @Test
    void sameSeedProducesIdenticalOutput() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> a = new ArrayList<>();
        List<BlockPlacement> b = new ArrayList<>();
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(42L), a);
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(42L), b);

        assertEquals(a.size(), b.size(), "Same seed: same count");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).toString(), b.get(i).toString(),
                    "Placement " + i + " differs across runs with same seed");
        }
    }

    /**
     * Every cell this generator emits is on the perimeter ring -- it no longer hollows the room
     * out, so an interior cell here would be a wall block standing in open floor space.
     */
    @Test
    void emitsOnlyPerimeterCells() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        RoomData room = smallRoom();
        gen.build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        for (BlockPlacement bp : out) {
            int x = bp.getX() - room.getOriginX();
            int z = bp.getZ() - room.getOriginZ();
            assertTrue(x == 0 || x == room.getWidth() - 1 || z == 0 || z == room.getDepth() - 1,
                    "wall generator emitted an interior cell: " + bp);
        }
    }

    /**
     * With the interior fill gone, the only air this generator emits is a door half -- and this
     * room has no doorways. Anything else being air would be a hole punched in a wall.
     */
    @Test
    void aDoorlessRoomsWallsAreEntirelySolid() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        for (BlockPlacement bp : out) {
            assertFalse("minecraft:air".equals(bp.getBlockId()),
                    "a wall cell in a doorless room must be solid: " + bp);
        }
    }

    /**
     * The lichen-on-doors fix. A solid block in the door cell is what the room's
     * decoration pass anchors glow lichen to; the door piece then replaces it and
     * the lichen renders plastered onto the door. Removing the anchor is the only
     * fix reachable from here — the door belongs to a different piece.
     */
    @Test
    void aDoorwayCellIsAirAtTheTwoDoorHalfLevels() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        RoomData room = smallRoom();
        int floorY = 60;
        // Mid-point of the room's north edge (z = originZ), a real doorway position.
        Coords2D doorway = new Coords2D(room.getOriginX() + 3, room.getOriginZ());
        room.getDoorways().add(doorway);
        gen.build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        for (BlockPlacement bp : out) {
            if (bp.getX() != doorway.getX() || bp.getZ() != doorway.getY()) continue;
            int offset = bp.getY() - floorY;
            if (offset == 1 || offset == 2) {
                assertEquals("minecraft:air", bp.getBlockId(),
                        "door-half level " + offset + " must be air: " + bp);
            } else {
                assertFalse("minecraft:air".equals(bp.getBlockId()),
                        "sill/lintel level " + offset + " must stay solid: " + bp);
            }
        }
    }

    /** Without the doorway the same column is solid all the way up. */
    @Test
    void thatSameColumnIsSolidWithoutTheDoorway() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        RoomData room = smallRoom();
        int floorY = 60;
        int doorX = room.getOriginX() + 3;
        int doorZ = room.getOriginZ();
        gen.build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        for (BlockPlacement bp : out) {
            if (bp.getX() != doorX || bp.getZ() != doorZ) continue;
            assertFalse("minecraft:air".equals(bp.getBlockId()),
                    "no doorway declared, so the whole column stays wall: " + bp);
        }
    }

    /** Piercing swaps a block state, it never adds or drops a placement. */
    @Test
    void aDoorwayDoesNotChangeThePlacementCount() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> withDoor = new ArrayList<>();
        List<BlockPlacement> without = new ArrayList<>();
        RoomData room = smallRoom();
        room.getDoorways().add(new Coords2D(room.getOriginX() + 3, room.getOriginZ()));
        gen.build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(42L), withDoor);
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(42L), without);

        assertEquals(without.size(), withDoor.size());
    }

    @Test
    void allPlacementsHaveNonNullBlockId() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        for (BlockPlacement bp : out) {
            assertFalse(bp.getBlockId() == null || bp.getBlockId().isEmpty(),
                    "Every placement must have a block id: " + bp);
        }
    }
}
