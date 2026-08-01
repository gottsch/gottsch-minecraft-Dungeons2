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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link BasicRoomGenerator} orchestrates wall + floor + ceiling
 * sub-builders correctly. Also exercises {@link
 * mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.BasicFloorGenerator}
 * and {@link mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.BasicCeilingGenerator}
 * transitively.
 */
class BasicRoomGeneratorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private RoomData smallRoom() {
        return new RoomData(1, 10, 10, 7, 7, 5, RoomRole.NORMAL);
    }

    @Test
    void orchestratorEmitsWallFloorAndCeilingPlacements() {
        BasicRoomGenerator gen = new BasicRoomGenerator();
        RoomPlacements outPlacements = new RoomPlacements();
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(99L), outPlacements);
        List<BlockPlacement> out = outPlacements.getBlocks();

        // Interior air = 75 (RoomVolumeGeneratorTest) + walls = 84 (BasicWallGeneratorTest).
        // Floor: border 2x5 (x edges, depth-2) + 2x3 (z edges, width-4) + interior 3x3 = 10 + 6 + 9 = 25.
        // Ceiling: 5x5 = 25.
        // Total: 75 + 84 + 25 + 25 = 209 -- unchanged by extracting the hollow step out of the
        // wall generator, which is the point: the same cells are emitted, by a different step.
        assertEquals(209, out.size(),
                "Room orchestrator should produce wall + floor + ceiling placements");
    }

    @Test
    void floorIsAtFloorYAndCeilingIsAtFloorYPlusHeightMinusOne() {
        BasicRoomGenerator gen = new BasicRoomGenerator();
        RoomPlacements outPlacements = new RoomPlacements();
        RoomData room = smallRoom();
        int floorY = 60;
        gen.build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(99L), outPlacements);
        List<BlockPlacement> out = outPlacements.getBlocks();

        int expectedCeilingY = floorY + room.getHeight() - 1; // = 64
        boolean sawFloorY = false;
        boolean sawCeilingY = false;
        for (BlockPlacement bp : out) {
            if (bp.getY() == floorY) sawFloorY = true;
            if (bp.getY() == expectedCeilingY) sawCeilingY = true;
            // Within the room's vertical extent.
            assertTrue(bp.getY() >= floorY && bp.getY() <= expectedCeilingY,
                    "Y " + bp.getY() + " outside [" + floorY + ".." + expectedCeilingY + "]: " + bp);
        }
        assertTrue(sawFloorY, "Should see at least one placement at floorY");
        assertTrue(sawCeilingY, "Should see at least one placement at ceilingY");
    }

    @Test
    void roomOrchestrationIsDeterministic() {
        BasicRoomGenerator gen = new BasicRoomGenerator();
        RoomPlacements first = new RoomPlacements();
        RoomPlacements second = new RoomPlacements();
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(99L), first);
        gen.build(smallRoom(), 60, DungeonMotif.CLASSIC, RandomSource.create(99L), second);

        List<BlockPlacement> a = first.getBlocks();
        List<BlockPlacement> b = second.getBlocks();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).toString(), b.get(i).toString(),
                    "Mismatch at placement " + i);
        }

        // The entity channel has to be deterministic too -- more so, since the piece relies on
        // every per-chunk re-run producing the same plan to spawn each prop exactly once.
        assertEquals(first.getEntities().size(), second.getEntities().size());
        for (int i = 0; i < first.getEntities().size(); i++) {
            assertEquals(first.getEntities().get(i).toString(), second.getEntities().get(i).toString(),
                    "Mismatch at entity " + i);
        }
    }
}
