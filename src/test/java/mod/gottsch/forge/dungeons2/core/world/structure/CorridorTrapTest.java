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
import mod.gottsch.forge.dungeons2.core.config.CorridorTrapCodec;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.CorridorDescent;
import mod.gottsch.forge.dungeons2.core.data.CorridorStyleWeight;
import mod.gottsch.forge.dungeons2.core.data.CorridorTrap;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.BasicCorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.CorridorTrapPlacer;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #108 stage 3: a trench trap across a corridor's straight, level run.
 *
 * <p>What must hold: the trench spans the full width (it cannot be walked round); it is never near
 * a door, a junction or a stair; it is at least two deep and never below the floor's band; its every
 * face is lined; and every block lands inside the piece's box.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
class CorridorTrapTest {

    private static final String MOTIF = "classic";
    private static final int SINK = 5;
    private static final long[] SEEDS = {1L, 42L, 0xD2_4A_2026L, 777L, 31337L};

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The shipped classic options at chance 1, with and without descent. */
    private static List<CorridorStyleWeight> styles(boolean descent) {
        List<CorridorTrap> options = MotifConfigs.load(MOTIF).corridor().traps().orElseThrow().options();
        return List.of(new CorridorStyleWeight("vaulted", 1, 7,
                descent ? new CorridorDescent(1.0, 2, 4, 2, 1) : null,
                new CorridorTrap.Options(1.0, options)));
    }

    private static DungeonLayout plan(long seed, List<CorridorStyleWeight> styles) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, MOTIF, new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM).withFloorCount(2).withCorridorStyles(styles)
                .plan().orElseThrow();
    }

    private static List<Coords2D> run(int length, int width) {
        List<Coords2D> cells = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < width; z++) cells.add(new Coords2D(x, z));
        }
        return cells;
    }

    // ---------------------------------------------------------------- placement

    @Test
    void aTrenchSpansTheWholeWidthOfAStraightRun() {
        List<Coords2D> cells = run(20, 3);
        Set<Coords2D> blocked = new HashSet<>();
        for (Coords2D c : cells) if (c.getX() < 2 || c.getX() > 17) blocked.add(c);
        CorridorTrapPlacer.Trench trench = CorridorTrapPlacer.place(cells, c -> 0, blocked, 3, 2, 4, SINK,
                new Random(1));
        assertNotNull(trench);
        assertEquals(6, trench.cells().size(), "two full cross-sections of a 3-wide run");
        Set<Integer> xs = new HashSet<>();
        for (Coords2D c : trench.cells()) xs.add(c.getX());
        assertEquals(2, xs.size());
        for (int x : xs) {
            for (int z = 0; z < 3; z++) assertTrue(trench.cells().contains(new Coords2D(x, z)));
        }
        assertEquals(4, trench.floorDepth());
    }

    @Test
    void nowhereToPutOneMeansNoTrap() {
        // Too short for a trench plus an approach either side.
        assertNull(CorridorTrapPlacer.place(run(3, 3), c -> 0, Set.of(), 3, 2, 4, SINK, new Random(1)));
        // Wider than the carved width: a widened stretch or a plaza.
        assertNull(CorridorTrapPlacer.place(run(20, 5), c -> 0, Set.of(), 3, 2, 4, SINK, new Random(1)));
        // Sunk so deep the band leaves less than a two-block fall.
        assertNull(CorridorTrapPlacer.place(run(20, 3), c -> 4, Set.of(), 3, 2, 4, SINK, new Random(1)));
    }

    @Test
    void aTrenchNeverStraddlesAStep() {
        List<Coords2D> cells = run(12, 1);
        // One step at x = 6: every candidate here either spans it or has an approach across it,
        // except x 1..4 (approach 0 and 5 on the level) -- so the trench lands left of the step.
        CorridorTrapPlacer.Trench trench = CorridorTrapPlacer.place(cells, c -> c.getX() >= 6 ? 1 : 0,
                Set.of(), 3, 2, 4, SINK, new Random(3));
        assertNotNull(trench);
        int depth = trench.cells().get(0).getX() >= 6 ? 1 : 0;
        for (Coords2D c : trench.cells()) {
            assertEquals(depth, c.getX() >= 6 ? 1 : 0, "trench straddles the step");
        }
    }

    /** On real planner output: level, plain, clear of doors, deep enough, inside the band. */
    @Test
    void plannedTrapsKeepEveryRule() {
        int traps = 0;
        for (long seed : SEEDS) {
            for (boolean descent : new boolean[]{false, true}) {
                for (FloorLayout floor : plan(seed, styles(descent)).getFloors()) {
                    for (CorridorData c : floor.getCorridors()) {
                        if (!c.hasTrap()) continue;
                        traps++;
                        Set<Coords2D> own = new HashSet<>(c.getCells());
                        int depth = c.depthAt(c.getTrapCells().get(0));
                        assertTrue(c.getTrapFloorDepth() <= SINK, "below the band");
                        assertTrue(c.getTrapFloorDepth() - depth >= CorridorTrapPlacer.MIN_FALL, "too shallow");
                        for (Coords2D t : c.getTrapCells()) {
                            assertTrue(own.contains(t));
                            assertEquals(depth, c.depthAt(t), "trench on a step");
                            for (Coords2D door : c.getDoorCells()) {
                                assertFalse(Math.abs(door.getX() - t.getX()) <= 1
                                        && Math.abs(door.getY() - t.getY()) <= 1, "trench beside door " + door);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(traps > 0, "no trap was planned at chance 1.0");
    }

    @Test
    void aTrapMovesNothingElse() {
        for (long seed : SEEDS) {
            DungeonLayout without = plan(seed, List.of(new CorridorStyleWeight("vaulted", 1, 7,
                    new CorridorDescent(1.0, 2, 4, 2, 1))));
            DungeonLayout with = plan(seed, styles(true));
            for (int f = 0; f < without.getFloors().size(); f++) {
                List<CorridorData> a = without.getFloors().get(f).getCorridors();
                List<CorridorData> b = with.getFloors().get(f).getCorridors();
                assertEquals(a.size(), b.size());
                for (int i = 0; i < a.size(); i++) {
                    assertEquals(a.get(i).getCells(), b.get(i).getCells());
                    assertEquals(java.util.Arrays.toString(a.get(i).getCellDepths()),
                            java.util.Arrays.toString(b.get(i).getCellDepths()));
                }
            }
        }
    }

    @Test
    void aTrapSurvivesTheNbtRoundTrip() {
        CorridorData trapped = trapped(SEEDS[0], true);
        CorridorData back = PieceNbt.readCorridor(PieceNbt.writeCorridor(trapped));
        assertEquals(trapped.getTrapCells(), back.getTrapCells());
        assertEquals(trapped.getTrapFloorDepth(), back.getTrapFloorDepth());
        assertEquals(trapped.getTrapKind(), back.getTrapKind());
        assertEquals(trapped.getTrapOption(), back.getTrapOption());
        CompoundTag none = PieceNbt.writeCorridor(new CorridorData(1, run(3, 1)));
        assertFalse(none.contains("Trap"));
    }

    // ---------------------------------------------------------------- rendering

    @Test
    void everyBlockOfATrappedCorridorIsInsideItsBox() {
        MotifConfig classic = MotifConfigs.load(MOTIF);
        for (long seed : SEEDS) {
            for (FloorLayout floor : plan(seed, styles(true)).getFloors()) {
                for (CorridorData c : floor.getCorridors()) {
                    if (!c.hasTrap()) continue;
                    DungeonCorridorPiece piece = new DungeonCorridorPiece(c, MOTIF, floor.getFloorY(),
                            floor.getFloorIndex(), 0, 0);
                    BoundingBox box = piece.getBoundingBox();
                    assertEquals(floor.getFloorY() - c.reachBelow(), box.minY());
                    for (BlockPlacement bp : piece.renderPlacements(classic.forFloor(floor.getFloorIndex()))) {
                        assertTrue(box.isInside(new BlockPos(bp.getX(), bp.getY(), bp.getZ())),
                                "seed " + seed + ": " + bp.getX() + "," + bp.getY() + "," + bp.getZ() + " outside " + box);
                    }
                }
            }
        }
    }

    /**
     * The trench is open down to its floor, closed on every side, lidded (false floor) or rimmed
     * (hazard) as authored.
     */
    @Test
    void theTrenchIsDugLinedAndLidded() {
        MotifConfig classic = MotifConfigs.load(MOTIF).forFloor(0);
        int falseFloors = 0;
        int hazards = 0;
        for (long seed : SEEDS) {
            FloorLayout floor = plan(seed, styles(true)).getFloors().get(0);
            int floorY = floor.getFloorY();
            for (CorridorData c : floor.getCorridors()) {
                if (!c.hasTrap()) continue;
                List<BlockPlacement> out = new ArrayList<>();
                new BasicCorridorGenerator().withMotifConfig(classic)
                        .build(c, floorY, null, RandomSource.create(c.getId()), out);
                Map<BlockPos, String> world = new HashMap<>();
                for (BlockPlacement bp : out) world.put(new BlockPos(bp.getX(), bp.getY(), bp.getZ()), bp.getBlockId());

                Set<Coords2D> trench = new HashSet<>(c.getTrapCells());
                Set<Coords2D> own = new HashSet<>(c.getCells());
                int bottom = floorY - c.getTrapFloorDepth();
                boolean falseFloor = c.getTrapKind().equals(CorridorTrap.FALSE_FLOOR);
                if (falseFloor) falseFloors++; else hazards++;
                for (Coords2D t : trench) {
                    int base = floorY - c.depthAt(t);
                    assertNotEquals("minecraft:air", world.get(new BlockPos(t.getX(), bottom, t.getY())),
                            "no trench floor under " + t);
                    for (int y = bottom + 2; y < base; y++) {
                        assertEquals("minecraft:air", world.get(new BlockPos(t.getX(), y, t.getY())),
                                "trench not open at " + t + " y " + y);
                    }
                    String top = world.get(new BlockPos(t.getX(), base, t.getY()));
                    if (falseFloor) {
                        assertTrue(top.startsWith("dungeonblocks:crumbling_"), "lid at " + t + " is " + top);
                    } else {
                        assertEquals("minecraft:air", top, "a hazard is open");
                    }
                    // Lined: every horizontal neighbour that is not trench is solid from the trench
                    // floor up to under its own floor.
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Coords2D n = new Coords2D(t.getX() + dx, t.getY() + dz);
                            if ((dx == 0 && dz == 0) || trench.contains(n)) continue;
                            int nBase = own.contains(n) ? floorY - c.depthAt(n) : floorY;
                            for (int y = bottom; y < Math.min(nBase, base); y++) {
                                String id = world.get(new BlockPos(n.getX(), y, n.getY()));
                                assertNotNull(id, "seed " + seed + ": unlined face beside " + t + " at " + n + " y " + y);
                                assertNotEquals("minecraft:air", id, "open face beside " + t + " at " + n + " y " + y);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(falseFloors + hazards > 0);
    }

    // ---------------------------------------------------------------- config

    @Test
    void theShippedClassicTrapsReachThePlanner() {
        for (CorridorStyleWeight w : DungeonStructure.corridorStyleWeights(MotifConfigs.load(MOTIF).corridor())) {
            assertNotNull(w.traps(), "style " + w.name() + " has no traps");
        }
    }

    @Test
    void aRimOnAFalseFloorOrAnUnknownKindIsALoadError() {
        String rimmed = "{ \"options\": [ { \"kind\": \"false_floor\", \"rim_block\": \"minecraft:stone\" } ] }";
        String unknown = "{ \"options\": [ { \"kind\": \"pitfall\" } ] }";
        String ok = "{ \"options\": [ { \"kind\": \"hazard\", \"rim_block\": \"minecraft:stone\" } ] }";
        assertTrue(CorridorTrapCodec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(rimmed)).error().isPresent());
        assertTrue(CorridorTrapCodec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(unknown)).error().isPresent());
        assertTrue(CorridorTrapCodec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(ok)).result().isPresent());
    }

    private static CorridorData trapped(long seed, boolean descent) {
        for (FloorLayout floor : plan(seed, styles(descent)).getFloors()) {
            for (CorridorData c : floor.getCorridors()) {
                if (c.hasTrap()) return c;
            }
        }
        throw new AssertionError("no trap for seed " + seed);
    }
}
