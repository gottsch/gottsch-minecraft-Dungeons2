/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
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

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.CorridorConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.CorridorDescent;
import mod.gottsch.forge.dungeons2.core.data.CorridorStyleWeight;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.BasicCorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.CorridorDescentField;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #108: corridors that sink into the floor's {@code sink_offset} band and come back up.
 *
 * <p>The four things that must never break: doors are met LEVEL; no rise is taller than one block;
 * every rise carries a stair whose top is the higher cell's floor; and every block lands inside the
 * piece's box, which is the failure that does not throw.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
class CorridorDescentTest {

    private static final int ANCHOR_X = 128;
    private static final int ANCHOR_Z = 256;
    private static final int SURFACE_Y = 64;
    private static final String MOTIF = "classic";
    private static final long[] SEEDS = {1L, 42L, 0xD2_4A_2026L, 987920711113878210L, 777L};

    /** Every corridor sinks, as deep as the band allows, on a staircase -- the worst case for the box. */
    private static final CorridorDescent STEEP = new CorridorDescent(1.0, 5, 5, 1, 1);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DungeonLayout plan(long seed, List<CorridorStyleWeight> styles) {
        DungeonStackPlanner planner = new DungeonStackPlanner(seed, new Coords(ANCHOR_X, 0, ANCHOR_Z),
                SURFACE_Y, MOTIF, new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withFloorCount(2);
        if (styles != null) {
            planner.withCorridorStyles(styles);
        }
        return planner.plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
    }

    private static List<CorridorStyleWeight> steep(int height) {
        return List.of(new CorridorStyleWeight("vaulted", 1, height, STEEP));
    }

    // ---------------------------------------------------------------- the depth field

    /** A 3-wide run with a door at each end: level at both doors, sinks between, steps by one. */
    @Test
    void aRunBetweenTwoDoorsSinksAndComesBackUp() {
        List<Coords2D> cells = new ArrayList<>();
        Set<Coords2D> anchors = new HashSet<>();
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 3; z++) {
                cells.add(new Coords2D(x, z));
            }
        }
        anchors.add(new Coords2D(0, 1));
        anchors.add(new Coords2D(29, 1));
        int[] depths = CorridorDescentField.compute(cells, anchors, 4, 3, 2);
        Map<Coords2D, Integer> at = index(cells, depths);

        assertEquals(0, at.get(new Coords2D(0, 1)));
        assertEquals(0, at.get(new Coords2D(29, 1)));
        assertEquals(4, at.get(new Coords2D(15, 1)), "the middle of a long run bottoms out");
        // 8-connected distance: a straight run's cross-section is one depth, so a riser is a
        // straight row of stairs, not a chevron.
        for (int x = 0; x < 30; x++) {
            assertEquals(at.get(new Coords2D(x, 0)), at.get(new Coords2D(x, 2)), "column " + x);
            assertEquals(at.get(new Coords2D(x, 0)), at.get(new Coords2D(x, 1)), "column " + x);
        }
        assertInvariants(cells, depths, anchors, 4);
    }

    /**
     * Mark: where corridors come together, or it's widened, there is no descent. A 3-wide run with a
     * 3-wide branch off its middle: the crossing square is level, and so is its landing, while both
     * arms still sink.
     */
    @Test
    void aJunctionStaysLevelAndTheArmsStillSink() {
        List<Coords2D> cells = new ArrayList<>();
        for (int x = 0; x < 41; x++) {
            for (int z = 0; z < 3; z++) cells.add(new Coords2D(x, z));
        }
        for (int x = 19; x <= 21; x++) {
            for (int z = 3; z < 25; z++) cells.add(new Coords2D(x, z));
        }
        Set<Coords2D> anchors = Set.of(new Coords2D(0, 1), new Coords2D(40, 1), new Coords2D(20, 24));
        int[] depths = CorridorDescentField.compute(cells, anchors, 4, 2, 2, 3);
        Map<Coords2D, Integer> at = index(cells, depths);

        for (int x = 19; x <= 21; x++) {
            for (int z = 0; z < 3; z++) {
                assertEquals(0, at.get(new Coords2D(x, z)), "junction cell " + x + "," + z);
            }
        }
        assertTrue(at.get(new Coords2D(10, 1)) > 0, "the west arm between door and junction sinks");
        assertTrue(at.get(new Coords2D(20, 12)) > 0, "the branch sinks");
        assertInvariants(cells, depths, anchors, 4);
    }

    /** A stretch dilation widened past the carved width is held level along its whole length. */
    @Test
    void aWidenedStretchStaysLevel() {
        List<Coords2D> cells = new ArrayList<>();
        for (int x = 0; x < 40; x++) {
            int width = (x >= 15 && x < 25) ? 5 : 3;
            for (int z = 0; z < width; z++) cells.add(new Coords2D(x, z));
        }
        Map<Coords2D, Integer> at = index(cells, CorridorDescentField.compute(cells,
                Set.of(new Coords2D(0, 1), new Coords2D(39, 1)), 4, 2, 1, 3));
        for (int x = 15; x < 25; x++) {
            assertEquals(0, at.get(new Coords2D(x, 1)), "widened cell at x=" + x);
        }
        assertTrue(at.get(new Coords2D(8, 1)) > 0, "the plain run before the widening sinks");
    }

    @Test
    void aRunTooShortToClearItsLandingsStaysLevel() {
        List<Coords2D> cells = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            cells.add(new Coords2D(x, 0));
        }
        int[] depths = CorridorDescentField.compute(cells,
                Set.of(new Coords2D(0, 0), new Coords2D(4, 0)), 4, 1, 2);
        assertArrayEquals(new int[5], depths);
    }

    // ---------------------------------------------------------------- the planner

    @Test
    void aStyleWithNoDescentSinksNothingAndWritesNoTag() {
        for (long seed : SEEDS) {
            for (CorridorData corridor : corridors(plan(seed, List.of(new CorridorStyleWeight("vaulted", 1, 7))))) {
                assertFalse(corridor.isSunk(), "seed " + seed);
                assertFalse(PieceNbt.writeCorridor(corridor).contains("Depths"));
            }
        }
    }

    /**
     * The whole contract on real planner output: doors and connectors met level, one-block rises,
     * no troughs, never deeper than the band.
     */
    @Test
    void plannedDepthsKeepEveryInvariant() {
        int sunk = 0;
        for (long seed : SEEDS) {
            for (CorridorData corridor : corridors(plan(seed, steep(7)))) {
                int[] depths = corridor.getCellDepths();
                if (!corridor.isSunk()) continue;
                sunk++;
                Set<Coords2D> anchors = new HashSet<>();
                Set<Coords2D> doors = new HashSet<>(corridor.getDoorCells());
                for (Coords2D c : corridor.getCells()) {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (doors.contains(new Coords2D(c.getX() + d.getStepX(), c.getY() + d.getStepZ()))) {
                            anchors.add(c);
                        }
                    }
                }
                assertInvariants(corridor.getCells(), depths, anchors, 5);
                // No sunk cell sits in a junction or a widened stretch: the planner carves 3 wide.
                Set<Coords2D> own = new HashSet<>(corridor.getCells());
                for (Coords2D c : corridor.getCells()) {
                    if (corridor.depthAt(c) == 0) continue;
                    int across = Math.min(extent(own, c, 1, 0), extent(own, c, 0, 1));
                    assertTrue(across <= 3, "seed " + seed + ": sunk cell " + c + " is " + across + " wide");
                }
                // Every doorway column rests on level ground on all eight sides.
                for (Coords2D door : corridor.getDoorCells()) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            assertEquals(0, corridor.depthAt(new Coords2D(door.getX() + dx, door.getY() + dz)),
                                    "seed " + seed + ": a cell beside door " + door + " is sunk");
                        }
                    }
                }
            }
        }
        assertTrue(sunk > 0, "no corridor sank at chance 1.0");
    }

    @Test
    void depthsSurviveTheNbtRoundTrip() {
        CorridorData sunk = corridors(plan(SEEDS[0], steep(7))).stream().filter(CorridorData::isSunk)
                .findFirst().orElseThrow();
        CompoundTag tag = PieceNbt.writeCorridor(sunk);
        CorridorData back = PieceNbt.readCorridor(tag);
        assertArrayEquals(sunk.getCellDepths(), back.getCellDepths());
        assertEquals(sunk.depthAt(sunk.getCells().get(0)), back.depthAt(back.getCells().get(0)));
    }

    // ---------------------------------------------------------------- rendering

    /** The clipping guard, at every legal height, with every corridor as deep as the band goes. */
    @Test
    void everyEmittedBlockFitsInsideTheSunkPiecesBox() {
        MotifConfig classic = MotifConfigs.load(MOTIF);
        for (int height = 5; height <= 8; height++) {
            DungeonLayout layout = plan(SEEDS[1], steep(height));
            for (FloorLayout floor : layout.getFloors()) {
                for (CorridorData corridor : floor.getCorridors()) {
                    DungeonCorridorPiece piece = new DungeonCorridorPiece(corridor, MOTIF, floor.getFloorY(),
                            floor.getFloorIndex(), ANCHOR_X, ANCHOR_Z);
                    BoundingBox box = piece.getBoundingBox();
                    assertEquals(floor.getFloorY() - corridor.maxDepth(), box.minY());
                    for (BlockPlacement bp : piece.renderPlacements(classic.forFloor(floor.getFloorIndex()))) {
                        BlockPos pos = new BlockPos(ANCHOR_X + bp.getX(), bp.getY(), ANCHOR_Z + bp.getZ());
                        assertTrue(box.isInside(pos), "height " + height + ": " + pos.toShortString()
                                + " falls outside " + box);
                    }
                }
            }
        }
    }

    /**
     * Every rise carries a stair in the LOWER cell, facing up, whose top is level with the higher
     * cell's floor; and the whole column moved down, ceiling included.
     */
    @Test
    void everyRiseIsAStairFacingUphill() {
        MotifConfig classic = MotifConfigs.load(MOTIF).forFloor(0);
        int stairs = 0;
        for (long seed : SEEDS) {
            FloorLayout floor = plan(seed, steep(7)).getFloors().get(0);
            int floorY = floor.getFloorY();
            for (CorridorData corridor : floor.getCorridors()) {
                if (!corridor.isSunk()) continue;
                Map<BlockPos, BlockState> world = render(corridor, floorY, classic);
                Set<Coords2D> cells = new HashSet<>(corridor.getCells());
                for (Coords2D c : corridor.getCells()) {
                    int d = corridor.depthAt(c);
                    int base = floorY - d;
                    BlockState floorBlock = world.get(new BlockPos(c.getX(), base, c.getY()));
                    assertNotNull(floorBlock);
                    // Not isSolid(): its shape cache is never built headless, so cobblestone reads false.
                    assertFalse(floorBlock.isAir(), "cell " + c + " has no floor at its depth");
                    List<Direction> higher = new ArrayList<>();
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        Coords2D n = new Coords2D(c.getX() + dir.getStepX(), c.getY() + dir.getStepZ());
                        if (cells.contains(n) && corridor.depthAt(n) < d) {
                            assertEquals(d - 1, corridor.depthAt(n), "a rise taller than one block at " + c);
                            higher.add(dir);
                        }
                    }
                    BlockState above = world.get(new BlockPos(c.getX(), base + 1, c.getY()));
                    if (higher.isEmpty()) {
                        assertFalse(above.getBlock() instanceof StairBlock && d > 0
                                        && above.getValue(StairBlock.HALF).getSerializedName().equals("bottom"),
                                "a riser stair at " + c + " with nothing to climb to");
                        continue;
                    }
                    stairs++;
                    assertTrue(above.getBlock() instanceof StairBlock, "no stair at the rise in " + c);
                    assertEquals("bottom", above.getValue(StairBlock.HALF).getSerializedName());
                    assertTrue(higher.contains(above.getValue(StairBlock.FACING)),
                            "stair at " + c + " faces " + above.getValue(StairBlock.FACING) + ", not up " + higher);
                }
            }
        }
        assertTrue(stairs > 0, "no rises were rendered");
    }

    /**
     * The descent roll draws from its own Random, so turning it on moves nothing else: same floors,
     * same rooms, same corridor cells, walls and doors. Only depths differ.
     */
    @Test
    void descentMovesNothingButDepths() {
        for (long seed : SEEDS) {
            DungeonLayout level = plan(seed, List.of(new CorridorStyleWeight("vaulted", 1, 7)));
            DungeonLayout sunk = plan(seed, steep(7));
            assertEquals(level.getFloors().size(), sunk.getFloors().size());
            for (int f = 0; f < level.getFloors().size(); f++) {
                FloorLayout a = level.getFloors().get(f);
                FloorLayout b = sunk.getFloors().get(f);
                assertEquals(a.getFloorY(), b.getFloorY());
                assertEquals(a.getRooms().size(), b.getRooms().size(), "seed " + seed);
                assertEquals(a.getCorridors().size(), b.getCorridors().size(), "seed " + seed);
                for (int i = 0; i < a.getCorridors().size(); i++) {
                    CorridorData ca = a.getCorridors().get(i);
                    CorridorData cb = b.getCorridors().get(i);
                    assertEquals(ca.getCells(), cb.getCells());
                    assertEquals(ca.getWallCells(), cb.getWallCells());
                    assertEquals(ca.getDoorCells(), cb.getDoorCells());
                }
            }
        }
    }

    /** A corridor whose depths are all zero renders exactly what one with no depths does. */
    @Test
    void anAllZeroDepthArrayRendersAsLevel() {
        MotifConfig classic = MotifConfigs.load(MOTIF).forFloor(0);
        FloorLayout floor = plan(SEEDS[2], List.of(new CorridorStyleWeight("vaulted", 1, 7))).getFloors().get(0);
        for (CorridorData corridor : floor.getCorridors()) {
            CorridorData zeroed = PieceNbt.readCorridor(PieceNbt.writeCorridor(corridor));
            zeroed.setCellDepths(new int[corridor.getCells().size()]);
            assertEquals(render(corridor, floor.getFloorY(), classic), render(zeroed, floor.getFloorY(), classic));
        }
    }

    // ---------------------------------------------------------------- config

    @Test
    void theShippedClassicDescentLoadsAndReachesThePlanner() {
        CorridorConfig corridor = MotifConfigs.load(MOTIF).corridor();
        List<CorridorStyleWeight> weights = DungeonStructure.corridorStyleWeights(corridor);
        assertTrue(weights.stream().anyMatch(w -> w.descent() != null), "classic authors no descent");
    }

    @Test
    void aDescentWithNothingToBuildItsRisersFromIsALoadError() {
        String json = """
                { "floor": "minecraft:stone", "alternate_floor": "minecraft:stone", "ceiling": "minecraft:stone",
                  "styles": [ { "name": "sunk", "descent": { "max_depth": 3 } } ] }
                """;
        assertTrue(CorridorConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
        String fixed = json.replace("\"ceiling\": \"minecraft:stone\",",
                "\"ceiling\": \"minecraft:stone\", \"step_block\": \"minecraft:stone_stairs\",");
        assertTrue(CorridorConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(fixed)).result().isPresent());
    }

    @Test
    void minDepthAboveMaxDepthIsALoadError() {
        String json = """
                { "floor": "minecraft:stone", "alternate_floor": "minecraft:stone", "ceiling": "minecraft:stone",
                  "step_block": "minecraft:stone_stairs", "descent": { "min_depth": 4, "max_depth": 2 } }
                """;
        assertTrue(CorridorConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
    }

    // ---------------------------------------------------------------- helpers

    private static List<CorridorData> corridors(DungeonLayout layout) {
        return layout.getFloors().stream().map(FloorLayout::getCorridors).flatMap(List::stream).toList();
    }

    private static int extent(Set<Coords2D> cells, Coords2D c, int dx, int dz) {
        int n = 1;
        for (int s = 1; cells.contains(new Coords2D(c.getX() + dx * s, c.getY() + dz * s)); s++) n++;
        for (int s = 1; cells.contains(new Coords2D(c.getX() - dx * s, c.getY() - dz * s)); s++) n++;
        return n;
    }

    private static Map<Coords2D, Integer> index(List<Coords2D> cells, int[] depths) {
        Map<Coords2D, Integer> at = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            at.put(cells.get(i), depths[i]);
        }
        return at;
    }

    private static Map<BlockPos, BlockState> render(CorridorData corridor, int floorY, MotifConfig motif) {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(motif)
                .build(corridor, floorY, null, RandomSource.create(corridor.getId()), out);
        Map<BlockPos, BlockState> world = new HashMap<>();
        for (BlockPlacement bp : out) {
            world.put(new BlockPos(bp.getX(), bp.getY(), bp.getZ()), BlockStateCodec.resolve(bp));
        }
        return world;
    }

    private static void assertInvariants(List<Coords2D> cells, int[] depths, Set<Coords2D> anchors, int max) {
        Map<Coords2D, Integer> at = index(cells, depths);
        for (Coords2D c : cells) {
            int d = at.get(c);
            assertTrue(d >= 0 && d <= max, "depth " + d + " at " + c);
            if (anchors.contains(c)) {
                assertEquals(0, d, "anchor " + c + " is not level");
            }
            boolean[] higher = new boolean[4];
            int k = 0;
            for (int[] step : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                Integer n = at.get(new Coords2D(c.getX() + step[0], c.getY() + step[1]));
                if (n != null) {
                    assertTrue(Math.abs(n - d) <= 1, "a rise of " + Math.abs(n - d) + " beside " + c);
                    higher[k] = n < d;
                }
                k++;
            }
            assertFalse((higher[0] && higher[1]) || (higher[2] && higher[3]), "trough at " + c);
        }
    }
}
