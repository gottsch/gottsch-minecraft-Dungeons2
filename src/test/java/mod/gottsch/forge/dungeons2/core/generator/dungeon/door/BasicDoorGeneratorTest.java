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

import mod.gottsch.forge.dungeons2.core.config.CeilingConfig;
import mod.gottsch.forge.dungeons2.core.config.CorridorConfig;
import mod.gottsch.forge.dungeons2.core.config.DoorConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.WallConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonDoorPiece;
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

    // ---------- doorless openings ----------

    private static MotifConfig withDoorProbability(double probability) {
        DoorConfig door = new DoorConfig(DoorConfig.DEFAULT.door(), DoorConfig.DEFAULT.lintel(),
                DoorConfig.DEFAULT.floor(), probability);
        return new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT, door,
                CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(RoomScheme.PLAIN));
    }

    /**
     * How many of a grid of doorways actually got a door block, <strong>rendered through the real
     * {@link DungeonDoorPiece}</strong> rather than from a seed this test made up.
     *
     * <p>That detail is the test. The roll is the <em>first</em> draw from a freshly seeded
     * {@code RandomSource}, which is the one position where seeding quality shows: seeding
     * sequentially with 0, 1, 2, ... (the obvious thing to write here, and what this test did at
     * first) puts every first {@code nextDouble} in a narrow band around 0.73, so a probability of
     * 0.7 produced <em>zero</em> doors out of 400 rather than ~280. The production path is fine
     * because {@code DungeonPiece#deterministicRandom} splitmixes the coords first — but "fine
     * because of a mixer three classes away" is exactly the kind of thing that should be pinned by
     * a test that actually goes through it.</p>
     */
    private static int hungDoors(double probability, int side) {
        MotifConfig config = withDoorProbability(probability);
        int hung = 0;
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                DungeonDoorPiece piece = new DungeonDoorPiece(
                        new DoorData(x, z, 1, 2, Direction2D.NORTH), "classic", 60, 0, 0);
                for (BlockPlacement bp : piece.renderPlacements(config)) {
                    if (bp.getY() == 61 && !"minecraft:air".equals(bp.getBlockId())) {
                        hung++;
                    }
                }
            }
        }
        return hung;
    }

    /** The default is what the generator always did: every opening carries a door. */
    @Test
    void probabilityDefaultsToEveryOpeningDoored() {
        assertEquals(1.0, DoorConfig.DEFAULT.probability());
        assertEquals(400, hungDoors(1.0, 20));
    }

    @Test
    void aProbabilityOfZeroLeavesEveryOpeningDoorless() {
        assertEquals(0, hungDoors(0.0, 20));
    }

    /**
     * The authored proportion has to actually come out across the doors of a dungeon. A biased
     * first draw would not look like a bug in one room -- it would quietly make a whole dungeon
     * all-doors or no-doors, which is the failure worth spending a test on.
     */
    @Test
    void aPartialProbabilityHitsItsAuthoredProportion() {
        int hung = hungDoors(0.7, 32);
        double pct = 100.0 * hung / (32 * 32);
        assertTrue(pct > 60 && pct < 80,
                "0.7 should hang roughly 70% of doors, got " + pct + "%");
    }

    /**
     * A doorless opening is still a <em>framed</em> opening: the sill and lintel are placed either
     * way, so it reads as architecture rather than as a hole punched in a wall. Same four-placement
     * column as a doored one, which is also what keeps the piece's block count stable.
     */
    @Test
    void aDoorlessOpeningKeepsItsSillAndLintel() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicDoorGenerator().withMotifConfig(withDoorProbability(0.0))
                .build(new DoorData(5, 5, 1, 2, Direction2D.NORTH), 60,
                        DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        assertEquals(4, out.size());
        for (BlockPlacement bp : out) {
            int yOffset = bp.getY() - 60;
            if (yOffset == 1 || yOffset == 2) {
                assertEquals("minecraft:air", bp.getBlockId(), "the opening itself is air");
            } else {
                assertEquals("minecraft:stone_bricks", bp.getBlockId(),
                        "sill and lintel still frame the opening");
            }
        }
    }

    /**
     * The roll must come out the same every time a piece is re-rendered -- postProcess runs once per
     * overlapping chunk, and a door that appeared or vanished between those calls would be a seam.
     * The piece feeds this a stable per-door seed; this pins that the generator respects it.
     */
    @Test
    void theDoorlessRollIsStableAcrossRepeatedRenders() {
        MotifConfig config = withDoorProbability(0.5);
        for (int x = 0; x < 20; x++) {
            DungeonDoorPiece piece = new DungeonDoorPiece(
                    new DoorData(x, 3, 1, 2, Direction2D.NORTH), "classic", 60, 0, 0);
            assertEquals(piece.renderPlacements(config).toString(),
                    piece.renderPlacements(config).toString(),
                    "door at x=" + x + " changed between renders");
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
