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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.door;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicDoorGeneratorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void doorWithFacingEmitsFourBlockColumn() {
        DoorData door = new DoorData(5, 5, 1, 2, Direction2D.NORTH);
        BasicDoorGenerator gen = new BasicDoorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(door, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        // Always: sill + lintel + 2 door halves = 4 placements.
        assertEquals(4, out.size(), "Door column should be 4 placements");
        // Verify Y stacking.
        boolean[] yPresent = new boolean[4];
        for (BlockPlacement bp : out) {
            int yOffset = bp.getY() - 60;
            assertTrue(yOffset >= 0 && yOffset < 4, "Y outside door column: " + bp);
            yPresent[yOffset] = true;
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(yPresent[i], "Missing placement at Y offset " + i);
        }
    }

    @Test
    void doorWithFacingSetsFacingPropertyOnDoorHalves() {
        DoorData door = new DoorData(5, 5, 1, 2, Direction2D.EAST);
        BasicDoorGenerator gen = new BasicDoorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(door, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        boolean sawFacingEast = false;
        for (BlockPlacement bp : out) {
            if (bp.getProperties().containsKey("facing")) {
                assertEquals("east", bp.getProperties().get("facing"),
                        "Door half facing should match the DoorData.facing direction");
                sawFacingEast = true;
            }
        }
        assertTrue(sawFacingEast, "Expected at least one placement to carry facing=east");
    }

    @Test
    void doorWithNoneFacingFallsBackToAirHalves() {
        DoorData door = new DoorData(5, 5, 1, 2, Direction2D.NONE);
        BasicDoorGenerator gen = new BasicDoorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(door, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        // Still 4 placements: sill + lintel + 2 air halves.
        assertEquals(4, out.size());
        // The middle two should be air.
        for (BlockPlacement bp : out) {
            int yOffset = bp.getY() - 60;
            if (yOffset == 1 || yOffset == 2) {
                assertEquals("minecraft:air", bp.getBlockId(),
                        "Door halves with NONE facing should be air");
            }
        }
    }

    @Test
    void doorBuilderIsDeterministic() {
        DoorData door = new DoorData(5, 5, 1, 2, Direction2D.SOUTH);
        BasicDoorGenerator gen = new BasicDoorGenerator();
        List<BlockPlacement> a = new ArrayList<>();
        List<BlockPlacement> b = new ArrayList<>();
        gen.build(door, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), a);
        gen.build(door, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), b);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).toString(), b.get(i).toString());
        }
    }

    @Test
    void allPlacementsHaveCoords() {
        DoorData door = new DoorData(5, 5, 1, 2, Direction2D.WEST);
        BasicDoorGenerator gen = new BasicDoorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(door, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        for (BlockPlacement bp : out) {
            assertEquals(5, bp.getX(), "X should match door coord");
            assertEquals(5, bp.getZ(), "Z should match door coord");
            assertNotNull(bp.getBlockId(), "Block id required");
        }
    }
}
