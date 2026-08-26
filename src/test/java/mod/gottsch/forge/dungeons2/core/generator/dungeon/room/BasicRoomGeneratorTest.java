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

import mod.gottsch.forge.dungeons2.core.config.CeilingConfig;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CorridorConfig;
import mod.gottsch.forge.dungeons2.core.config.DoorConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorRange;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.pillar.ColonnadePillarLayout;
import mod.gottsch.forge.dungeons2.core.config.pillar.GridPillarLayout;
import mod.gottsch.forge.dungeons2.core.config.pillar.QuartetPillarLayout;
import mod.gottsch.forge.dungeons2.core.config.MobSetBand;
import mod.gottsch.forge.dungeons2.core.config.PotConfig;
import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.WallConfig;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** These cases are about room content, not depth, so every one builds the entrance floor. */
    private static final int ENTRANCE_FLOOR = 0;

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
        gen.build(smallRoom(), 60, ENTRANCE_FLOOR, DungeonMotif.CLASSIC, RandomSource.create(99L), outPlacements);
        List<BlockPlacement> out = outPlacements.getBlocks();

        // Interior air = 75 (RoomVolumeGeneratorTest) + walls = 72 (BasicWallGeneratorTest).
        // Floor: border 2x5 (x edges, depth-2) + 2x3 (z edges, width-4) + interior 3x3 = 10 + 6 + 9 = 25.
        // Ceiling: 5x5 = 25.
        // Total: 75 + 72 + 25 + 25 = 197. Was 209 until the surface frame gave the four wall runs
        // a corner-ownership rule; the 12 lost placements were duplicate corner columns, not
        // missing geometry (everyPerimeterCellIsCovered guards that).
        assertEquals(197, out.size(),
                "Room orchestrator should produce wall + floor + ceiling placements");
    }

    @Test
    void floorIsAtFloorYAndCeilingIsAtFloorYPlusHeightMinusOne() {
        BasicRoomGenerator gen = new BasicRoomGenerator();
        RoomPlacements outPlacements = new RoomPlacements();
        RoomData room = smallRoom();
        int floorY = 60;
        gen.build(room, floorY, ENTRANCE_FLOOR, DungeonMotif.CLASSIC, RandomSource.create(99L), outPlacements);
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
        gen.build(smallRoom(), 60, ENTRANCE_FLOOR, DungeonMotif.CLASSIC, RandomSource.create(99L), first);
        gen.build(smallRoom(), 60, ENTRANCE_FLOOR, DungeonMotif.CLASSIC, RandomSource.create(99L), second);

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

    /**
     * A hanging coffer rib and a projecting cornice both want the ring of interior cells against
     * the wall, at the top of the room. <strong>The ceiling wins</strong> -- the rib runs into the
     * cornice and interrupts it, which is what coffering does where it meets one, rather than
     * stopping a block short and leaving a gap of plain ceiling around the lattice.
     *
     * <p>The mechanism is nothing but emission order: {@code build} runs the ceiling after the
     * walls, and a later placement in the list overwrites an earlier one in the same cell. That
     * makes the order load-bearing, so this pins it.</p>
     */
    @Test
    void aHangingCofferOverridesTheWallsCorniceWhereTheyMeet() {
        RoomScheme scheme = new RoomScheme("cornice_and_coffers", 1, 0, 0,
                Optional.empty(),
                Optional.of(new WallPatternEntry("courses", List.of(
                        new WallPatternEntry.CourseEntry("minecraft:stone_brick_stairs",
                                Optional.empty(), Optional.empty(), CourseAnchor.TOP, 0, 1,
                                CourseOrient.TOWARD_WALL, Map.of())))),
                Optional.of(new CeilingPatternEntry(List.of(
                        new CeilingPatternEntry.SurfacePatternEntry("coffers",
                                Optional.of("minecraft:polished_andesite"), Optional.empty(),
                                0, 3, 1, 1)))),
                Optional.empty());

        MotifConfig config = new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT,
                DoorConfig.DEFAULT, CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(scheme));

        RoomData room = new RoomData(1, 0, 0, 11, 11, 7, RoomRole.NORMAL);
        int floorY = 60;
        RoomPlacements out = new RoomPlacements();
        new BasicRoomGenerator().withMotifConfig(config)
                .build(room, floorY, ENTRANCE_FLOOR, DungeonMotif.CLASSIC, RandomSource.create(4L), out);

        // Both layers live here: the cornice hangs off the top wall row, the ribs one below the
        // ceiling, and for a 7-high room those are the same Y.
        int contestedY = floorY + room.getHeight() - 2;

        // Last writer per cell is what the world ends up with.
        Map<String, String> finalBlock = new LinkedHashMap<>();
        boolean sawCornice = false;
        for (BlockPlacement bp : out.getBlocks()) {
            if (bp.getY() != contestedY) {
                continue;
            }
            String cell = bp.getX() + "," + bp.getZ();
            if ("minecraft:stone_brick_stairs".equals(bp.getBlockId())) {
                sawCornice = true;
            }
            finalBlock.put(cell, bp.getBlockId());
        }

        assertTrue(sawCornice, "the cornice should have been emitted at all");
        assertTrue(finalBlock.containsValue("minecraft:polished_andesite"),
                "ribs should reach the contested ring and win cells in it, got " + finalBlock);
        assertTrue(finalBlock.containsValue("minecraft:stone_brick_stairs"),
                "the cornice should survive everywhere a rib does not land, got " + finalBlock);
    }

    /**
     * Pots route around free-standing columns, the same way they already route around projecting
     * wall trim. A pot inside a column is invisible until someone walks into the room, so nothing in
     * game reports it -- which is why the reservation is asserted here rather than left to look
     * right.
     */
    @Test
    void potsDoNotStandInsideAColumn() {
        // spacing 2 / inset 0 drives the lattice right into the inner ring, where the pots want to
        // stand. At the default inset the two barely compete, so a passing test would prove nothing.
        RoomScheme scheme = new RoomScheme("pillared", 1, 0, 0,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new PotConfig(12, 12, "dungeons2:pots/classic",
                        List.of(new PotConfig.PotVariant("dungeonblocks:medium_pot", 1)))),
                Optional.of(new PillarPatternEntry(List.of(
                        new PillarPatternEntry.PillarEntry(new GridPillarLayout(2, 0), "minecraft:stone_bricks", Optional.empty(), Optional.empty(), Map.of(),
                                Optional.empty(), Optional.empty(), SizeGate.UNBOUNDED)))));

        MotifConfig config = new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT,
                DoorConfig.DEFAULT, CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(scheme));

        RoomData room = new RoomData(1, 0, 0, 11, 11, 7, RoomRole.NORMAL);
        RoomPlacements out = new RoomPlacements();
        new BasicRoomGenerator().withMotifConfig(config)
                .build(room, 60, ENTRANCE_FLOOR, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        java.util.Set<String> columnCells = out.getBlocks().stream()
                .filter(bp -> bp.getY() == 61 && "minecraft:stone_bricks".equals(bp.getBlockId()))
                .map(bp -> bp.getX() + "," + bp.getZ())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(!columnCells.isEmpty(), "the lattice should have placed columns at all");
        assertTrue(!out.getEntities().isEmpty(), "and the room should have pots to place");
        for (var pot : out.getEntities()) {
            assertTrue(!columnCells.contains(pot.getX() + "," + pot.getZ()),
                    "pot at " + pot.getX() + "," + pot.getZ() + " is standing inside a column");
        }
    }

    // ---------- the depth axis ----------

    /**
     * A room's spawners draw from the band its FLOOR falls in, not from a constant. This is the
     * end-to-end version of {@code MobSetsByFloorTest}: it runs the real orchestrator, so the
     * floorIndex actually has to survive the trip into the block-entity data.
     *
     * <p>Both assertions matter. The first is the feature; the second is the guard, because a
     * floorIndex dropped anywhere along the way would leave both floors on the shallow band and
     * the feature would still look like it worked.</p>
     */
    @Test
    void aRoomsSpawnersDrawFromItsOwnFloorsBand() {
        assertEquals("dungeons2:shallow", mobSetOnFloor(0));
        assertEquals("dungeons2:deep", mobSetOnFloor(4));
    }

    /** A scheme naming its own sets ignores the depth table, at every depth. */
    @Test
    void aSchemeThatNamesItsOwnSetsIsUnaffectedByDepth() {
        Optional<SpawnerConfig> owning = Optional.of(new SpawnerConfig(1, 1, 1, 3, 8.0D,
                List.of(new SpawnerConfig.MobSetEntry("dungeons2:fixed", 1))));
        assertEquals("dungeons2:fixed", mobSetOnFloor(0, owning));
        assertEquals("dungeons2:fixed", mobSetOnFloor(4, owning));
    }

    /** The deferring form -- no mobSets at all, which is what the shipped schemes use. */
    private static String mobSetOnFloor(int floorIndex) {
        return mobSetOnFloor(floorIndex, Optional.of(new SpawnerConfig(1, 1, Optional.of(1),
                Optional.of(3), 8.0D, Optional.empty(), SizeGate.UNBOUNDED,
                SpawnerConfig.Kind.PROXIMITY)));
    }

    private static String mobSetOnFloor(int floorIndex, Optional<SpawnerConfig> spawners) {
        RoomScheme scheme = new RoomScheme("spawning", 1, 0, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                spawners, FloorRange.ANY, Optional.empty(), false);

        MotifConfig config = new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT,
                DoorConfig.DEFAULT, CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(scheme),
                List.of(new MobSetBand(0, List.of(new SpawnerConfig.MobSetEntry("dungeons2:shallow", 1))),
                        new MobSetBand(3, List.of(new SpawnerConfig.MobSetEntry("dungeons2:deep", 1)))));

        RoomPlacements out = new RoomPlacements();
        new BasicRoomGenerator().withMotifConfig(config)
                .build(new RoomData(1, 0, 0, 11, 11, 8, RoomRole.NORMAL), 60, floorIndex,
                        DungeonMotif.CLASSIC, RandomSource.create(11L), out);

        return out.getBlocks().stream()
                .filter(placement -> placement.getBlockEntityNbt() != null)
                .map(placement -> placement.getBlockEntityNbt().getData().get("mobSetName"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no spawner was placed on floor " + floorIndex));
    }
}
