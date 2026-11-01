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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Procedural spawner placement &mdash; the {@code spawners} scheme slot. Pure data: block and mob
 * set ids stay strings until the piece resolves them, so no Minecraft bootstrap is needed.
 *
 * <p>Nearly every invariant here is one whose violation would be <strong>invisible in game</strong>,
 * which is the whole reason this file is as long as it is. A spawner in a wall cell, a spawner two
 * to a cell, a spawner whose tag lost its mob set: all of them look exactly like a room that was
 * never given one.</p>
 */
class RoomSpawnerGeneratorTest {

    private static final int FLOOR_Y = 60;
    /** The entrance floor. Depth-varying behaviour is not this class's subject. */
    private static final int FLOOR_INDEX = 0;
    private static final String VERMIN = "dungeons2:classic_vermin";

    private static SpawnerConfig config(int min, int max) {
        return new SpawnerConfig(min, max, 1, 3, 8.0D,
                List.of(new SpawnerConfig.MobSetEntry(VERMIN, 1)));
    }

    /** An 11x11 room at origin (10,10). */
    private static RoomData room() {
        return new RoomData(1, 10, 10, 11, 11, 8, RoomRole.NORMAL);
    }

    private static List<BlockPlacement> place(RoomData room, SpawnerConfig config, long seed) {
        return place(room, config, Set.of(), seed);
    }

    private static List<BlockPlacement> place(RoomData room, SpawnerConfig config,
                                              Set<Coords2D> occupied, long seed) {
        List<BlockPlacement> out = new ArrayList<>();
        RoomSpawnerGenerator.placeSpawners(room, FLOOR_Y, FLOOR_INDEX, config, occupied,
                RandomSource.create(seed), out);
        return out;
    }

    @Test
    void placesTheRequestedNumberOfSpawners() {
        assertEquals(2, place(room(), config(2, 2), 1L).size());
    }

    @Test
    void countStaysWithinTheConfiguredRange() {
        for (long seed = 0; seed < 60; seed++) {
            int size = place(room(), config(1, 3), seed).size();
            assertTrue(size >= 1 && size <= 3, "count " + size + " outside [1,3] at seed " + seed);
        }
    }

    /** {@code minCount: 0} is the incidence knob -- 0..1 is how a scheme says "half of these rooms". */
    @Test
    void aZeroCountPlacesNothing() {
        assertEquals(0, place(room(), config(0, 0), 1L).size());
    }

    @Test
    void everySpawnerStandsInAnInteriorCellOneAboveTheFloor() {
        RoomData room = room();
        for (long seed = 0; seed < 40; seed++) {
            for (BlockPlacement spawner : place(room, config(3, 3), seed)) {
                int x = spawner.getX() - room.getOriginX();
                int z = spawner.getZ() - room.getOriginZ();
                assertTrue(x > 0 && x < room.getWidth() - 1, "spawner in a wall column: " + spawner);
                assertTrue(z > 0 && z < room.getDepth() - 1, "spawner in a wall column: " + spawner);
                assertEquals(FLOOR_Y + 1, spawner.getY(),
                        "spawner off the cell resting on the floor: " + spawner);
            }
        }
    }

    /**
     * The whole interior, not the inner ring the pots keep to. A spawner is invisible, so the reason
     * a pot hugs a wall does not apply to it -- and mobs appearing out of the middle of a room is
     * the better encounter.
     */
    @Test
    void spawnersReachTheMiddleOfTheRoom() {
        RoomData room = room();
        boolean anyOffTheRing = false;
        for (long seed = 0; seed < 40 && !anyOffTheRing; seed++) {
            for (BlockPlacement spawner : place(room, config(1, 1), seed)) {
                int x = spawner.getX() - room.getOriginX();
                int z = spawner.getZ() - room.getOriginZ();
                anyOffTheRing |= x > 1 && x < room.getWidth() - 2 && z > 1 && z < room.getDepth() - 2;
            }
        }
        assertTrue(anyOffTheRing, "no spawner ever landed off the inner ring in 40 seeds -- the"
                + " eligible set has silently narrowed to the pots' one");
    }

    @Test
    void twoSpawnersNeverShareACell() {
        RoomData room = room();
        for (long seed = 0; seed < 40; seed++) {
            Set<String> cells = new HashSet<>();
            for (BlockPlacement spawner : place(room, config(8, 8), seed)) {
                assertTrue(cells.add(spawner.getX() + "," + spawner.getZ()),
                        "two spawners in one cell: " + spawner);
            }
        }
    }

    /**
     * A spawn triggered from the cell just inside a door drops mobs into the corridor the player is
     * still standing in, rather than into the room they are walking into.
     */
    @Test
    void noSpawnerSitsInFrontOfADoorway() {
        RoomData room = room();
        room.getDoorways().add(new Coords2D(10, 15));
        room.getDoorways().add(new Coords2D(15, 10));
        Set<Coords2D> forbidden = RoomInterior.cellsInsideDoorways(room);

        for (long seed = 0; seed < 40; seed++) {
            for (BlockPlacement spawner : place(room, config(8, 8), seed)) {
                assertFalse(forbidden.contains(new Coords2D(spawner.getX(), spawner.getZ())),
                        "spawner in a doorway approach: " + spawner);
            }
        }
    }

    /**
     * A column or a dais fills its cell with a solid block. Since the block written last wins, a
     * spawner emitted into one is a coin toss between vanishing and punching a hole in the
     * architecture -- and both outcomes are silent.
     */
    @Test
    void noSpawnerStandsInACellAnotherGeneratorTook() {
        RoomData room = room();
        Set<Coords2D> occupied = new HashSet<>();
        for (int x = 11; x <= 19; x++) {
            for (int z = 11; z <= 19; z++) {
                if (x != 15 || z != 15) {
                    occupied.add(new Coords2D(x, z));
                }
            }
        }
        // One cell left free, so this also pins that a crowded room places fewer rather than none.
        for (long seed = 0; seed < 20; seed++) {
            List<BlockPlacement> spawners = place(room, config(4, 4), occupied, seed);
            assertEquals(1, spawners.size(), "seed " + seed + " ignored the occupied set");
            assertEquals(15, spawners.get(0).getX());
            assertEquals(15, spawners.get(0).getZ());
        }
    }

    @Test
    void theCellsItTookAreReportedBackToTheCaller() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> used = RoomSpawnerGenerator.placeSpawners(room(), FLOOR_Y, FLOOR_INDEX, config(3, 3),
                Set.of(), RandomSource.create(7L), out);
        assertEquals(3, used.size());
        for (BlockPlacement spawner : out) {
            assertTrue(used.contains(new Coords2D(spawner.getX(), spawner.getZ())),
                    "a placed spawner's cell was not reported, so the pots can still land in it: "
                            + spawner);
        }
    }

    // ---------- the tag ----------

    @Test
    void thePlacementCarriesTheSpawnerBlockAndItsBlockEntity() {
        BlockPlacement spawner = place(room(), config(1, 1), 3L).get(0);
        assertEquals("dungeons2:mob_set_spawner", spawner.getBlockId());
        assertEquals("dungeons2:mob_set_spawner", spawner.getBlockEntityNbt().getType(),
                "the block entity type id is what DungeonPiece loads the tag against");
    }

    @Test
    void theBlockEntityDataCarriesTheSetAndTheTuning() {
        SpawnerConfig config = new SpawnerConfig(1, 1, 2, 5, 12.0D,
                List.of(new SpawnerConfig.MobSetEntry(VERMIN, 1)));
        Map<String, String> data = place(room(), config, 3L).get(0).getBlockEntityNbt().getData();
        assertEquals(VERMIN, data.get("mobSetName"));
        assertEquals("2", data.get("minMobs"));
        assertEquals("5", data.get("maxMobs"));
        assertEquals("12.0", data.get("proximity"));
    }

    /**
     * {@code proximity} must carry a decimal point. {@code DungeonPiece.putParsed} reaches for
     * {@code Integer} first, and the block entity reads the field with {@code getDouble} guarded by
     * a bare {@code contains} -- so a whole number written as {@code "8"} is a subtle
     * mistuning waiting to become a bug, and one written as text reads back as 0.
     */
    @Test
    void proximityIsWrittenAsADecimal() {
        Map<String, String> data = place(room(), config(1, 1), 3L).get(0)
                .getBlockEntityNbt().getData();
        assertTrue(data.get("proximity").contains("."),
                "proximity '" + data.get("proximity") + "' would be stored as an int tag");
    }

    /** An inverted mob range would reach GottschCore's randomInt as max &lt; min. */
    @Test
    void anInvertedMobRangeIsNormalisedBeforeItReachesTheTag() {
        SpawnerConfig config = new SpawnerConfig(1, 1, 4, 2, 8.0D,
                List.of(new SpawnerConfig.MobSetEntry(VERMIN, 1)));
        Map<String, String> data = place(room(), config, 3L).get(0).getBlockEntityNbt().getData();
        assertEquals("4", data.get("minMobs"));
        assertEquals("4", data.get("maxMobs"));
    }

    @Test
    void theMobSetIsDrawnFromTheWeightedList() {
        SpawnerConfig config = new SpawnerConfig(6, 6, 1, 3, 8.0D,
                List.of(new SpawnerConfig.MobSetEntry("dungeons2:a", 1),
                        new SpawnerConfig.MobSetEntry("dungeons2:b", 1)));
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            for (BlockPlacement spawner : place(room(), config, seed)) {
                seen.add(spawner.getBlockEntityNbt().getData().get("mobSetName"));
            }
        }
        assertEquals(Set.of("dungeons2:a", "dungeons2:b"), seen,
                "both sets should come up over 20 seeds of 6 draws");
    }

    // ---------- determinism ----------

    /**
     * A piece's {@code postProcess} runs once per chunk it overlaps and each run rebuilds the whole
     * plan, so an identical seed must give an identical plan or a spawner on a chunk seam is dropped
     * or doubled.
     */
    @Test
    void thePlanIsAPureFunctionOfItsSeed() {
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(describe(place(room(), config(3, 3), seed)),
                    describe(place(room(), config(3, 3), seed)),
                    "plan differed between two runs at seed " + seed);
        }
    }

    @Test
    void differentSeedsGiveDifferentPlans() {
        assertNotEquals(describe(place(room(), config(3, 3), 1L)),
                describe(place(room(), config(3, 3), 2L)));
    }

    private static String describe(List<BlockPlacement> placements) {
        StringBuilder out = new StringBuilder();
        for (BlockPlacement p : placements) {
            out.append(p).append('\n');
        }
        return out.toString();
    }
}
