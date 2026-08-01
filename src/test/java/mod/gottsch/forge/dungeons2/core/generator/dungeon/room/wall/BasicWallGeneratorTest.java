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
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        // Distinct perimeter cells per layer: 2*7 + 2*7 - 4 shared corners = 24. Times 3 = 72.
        //
        // This was 84 before the surface frame, because both edge loops ran at full length and
        // emitted the four corner columns twice over. WallSurface gives the corners to the Z-edge
        // runs and shortens the X-edge runs to the interior depth, so each cell is written once.
        // No visual change -- both writes were the same wall block -- but a pattern with any
        // horizontal rhythm would have had its corners decided by whichever loop ran last.
        //
        // Interior air is NOT here: it belongs to RoomVolumeGenerator (75 placements for this
        // room, asserted in RoomVolumeGeneratorTest).
        assertEquals(72, out.size(),
                "Expected 72 wall placements (24 perimeter cells x 3 rows) for a 7x7x5 room");
    }

    /**
     * The corner-ownership invariant, stated directly rather than inferred from a count. Any wall
     * pattern with horizontal rhythm depends on it: two runs writing the same column would let
     * whichever ran last silently decide what the corner looks like.
     */
    @Test
    void noCellIsEmittedTwice() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        Set<String> seen = new HashSet<>();
        for (BlockPlacement bp : out) {
            assertTrue(seen.add(bp.getX() + "," + bp.getY() + "," + bp.getZ()),
                    "cell written twice: " + bp);
        }
    }

    /** Every perimeter cell of every wall row is covered -- ownership must not leave a gap. */
    @Test
    void everyPerimeterCellIsCovered() {
        BasicWallGenerator gen = new BasicWallGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        RoomData room = smallRoom();
        int floorY = 60;
        gen.build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(42L), out);

        Set<String> seen = new HashSet<>();
        for (BlockPlacement bp : out) {
            seen.add(bp.getX() + "," + bp.getY() + "," + bp.getZ());
        }
        for (int x = 0; x < room.getWidth(); x++) {
            for (int z = 0; z < room.getDepth(); z++) {
                if (x != 0 && x != room.getWidth() - 1 && z != 0 && z != room.getDepth() - 1) {
                    continue;
                }
                for (int v = 0; v < room.getHeight() - 2; v++) {
                    String key = (room.getOriginX() + x) + "," + (floorY + 1 + v) + ","
                            + (room.getOriginZ() + z);
                    assertTrue(seen.contains(key), "perimeter cell never written: " + key);
                }
            }
        }
    }

    /**
     * End to end through the surface frame: one authored course comes out on all four walls, at the
     * same world Y, in one unbroken ring. That continuity is what makes courses the right first
     * wall pattern -- a band is at constant v, so corner ownership cannot break it.
     */
    @Test
    void aCrownCourseFormsAnUnbrokenRingOnEveryWall() {
        RoomData room = smallRoom(); // 7x7x5 -> 3 wall rows, so a top-anchored crown is at v=2
        int floorY = 60;
        BasicWallGenerator gen = new BasicWallGenerator().withWallPattern(
                new CoursesWallPatternProvider(List.of(new CoursesWallPatternProvider.Course(
                        Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                        WallPatternEntry.CourseAnchor.TOP, 0))));

        List<BlockPlacement> out = new ArrayList<>();
        gen.build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        Set<String> crown = new HashSet<>();
        for (BlockPlacement bp : out) {
            if ("minecraft:chiseled_stone_bricks".equals(bp.getBlockId())) {
                assertEquals(floorY + room.getHeight() - 2, bp.getY(),
                        "the crown must sit on the top wall row: " + bp);
                crown.add(bp.getX() + "," + bp.getZ());
            }
        }
        // Every perimeter cell, exactly once: 2*7 + 2*7 - 4 = 24.
        assertEquals(24, crown.size(), "crown should ring the whole room once");
        for (int x = 0; x < room.getWidth(); x++) {
            for (int z = 0; z < room.getDepth(); z++) {
                boolean perimeter = x == 0 || x == room.getWidth() - 1
                        || z == 0 || z == room.getDepth() - 1;
                if (perimeter) {
                    assertTrue(crown.contains((room.getOriginX() + x) + "," + (room.getOriginZ() + z)),
                            "gap in the crown ring at " + x + "," + z);
                }
            }
        }
    }

    /** A wall pattern must not reopen the lichen-on-doors bug by filling a door cell. */
    @Test
    void aWallPatternCannotFillADoorway() {
        RoomData room = smallRoom();
        room.getDoorways().add(new Coords2D(room.getOriginX() + 3, room.getOriginZ()));
        int floorY = 60;
        // A plinth on the lowest row -- exactly where the lower door half is.
        BasicWallGenerator gen = new BasicWallGenerator().withWallPattern(
                new CoursesWallPatternProvider(List.of(new CoursesWallPatternProvider.Course(
                        Blocks.POLISHED_ANDESITE.defaultBlockState(),
                        WallPatternEntry.CourseAnchor.BOTTOM, 0))));

        List<BlockPlacement> out = new ArrayList<>();
        gen.build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        for (BlockPlacement bp : out) {
            if (bp.getX() == room.getOriginX() + 3 && bp.getZ() == room.getOriginZ()
                    && (bp.getY() == floorY + 1 || bp.getY() == floorY + 2)) {
                assertEquals("minecraft:air", bp.getBlockId(),
                        "door half filled by a wall pattern: " + bp);
            }
        }
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
