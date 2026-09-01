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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit;

import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.PitPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.pit.CentrePitShape;
import mod.gottsch.forge.dungeons2.core.config.pit.HazardPitShape;
import mod.gottsch.forge.dungeons2.core.config.pit.InsetPitShape;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #3: sunken pits, and the rule that bounds every one of them.
 *
 * <h2>The rule</h2>
 * <p><strong>A pit is dug out of the floor's own budget and never out of the gap between
 * floors.</strong> {@code sink_offset} (#29) is how much of {@code floor_height} sits below the
 * walking plane, and a pit lives entirely in that; {@code gap_between_floors} is not available to it
 * at any authored depth. The clamp lives on {@link PitPatternEntry#depthWithin} rather than in a
 * codec because the two numbers are in different datapack registries &mdash; a pit is authored on a
 * {@code motif_config} scheme and {@code sink_offset} on the {@code generation_config} &mdash; so no
 * codec can see both.</p>
 *
 * <p>The consequence worth stating: <strong>at the shipped {@code sink_offset} of 0 there are no
 * pits</strong>, whatever a scheme says. That is a degrade, not an error, so a pack tuned for a
 * taller pitch still loads on one that is not.</p>
 */
class RoomPitTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int FLOOR_Y = 40;
    private static final FloorConfig PAVING =
            new FloorConfig("minecraft:stone_bricks", "minecraft:stone_bricks");

    /** A room at the origin, {@code side} on a side including its wall ring. */
    private static RoomData room(int side) {
        return new RoomData(1, 0, 0, side, side, 7, RoomRole.NORMAL);
    }

    private static Map<Coords2D, Map<Integer, BlockState>> stamp(List<BlockPlacement> placements) {
        Map<Coords2D, Map<Integer, BlockState>> world = new HashMap<>();
        for (BlockPlacement placement : placements) {
            world.computeIfAbsent(new Coords2D(placement.getX(), placement.getZ()),
                            key -> new HashMap<>())
                    .put(placement.getY(), BlockStateCodec.resolve(placement));
        }
        return world;
    }

    private static Set<Coords2D> excavate(RoomData room, PitPatternEntry entry, int sinkOffset,
                                          List<BlockPlacement> out) {
        return RoomPitGenerator.excavate(room, FLOOR_Y, entry, sinkOffset, PAVING,
                RandomSource.create(0xD2_03L), out);
    }

    // ---------- the budget rule ----------

    /** The shipped configuration. Nothing is written, and the scheme is not an error. */
    @Test
    void aFloorThatWasNeverSunkGetsNoPitHoweverTheSchemeIsAuthored() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(11), new PitPatternEntry(new CentrePitShape(3, 5)), 0, out);

        assertTrue(dug.isEmpty(), "sink_offset 0 leaves nowhere to dig");
        assertTrue(out.isEmpty(), "and nothing should have been written");
    }

    /**
     * A pit deeper than the floor was sunk is CLAMPED, not refused. The pit is still the feature
     * the author asked for, just as deep as the floor can hold &mdash; and crucially it stops at
     * the budget rather than reaching into {@code gap_between_floors}, which belongs to no floor.
     */
    @Test
    void aPitDeeperThanTheFloorWasSunkIsClampedToTheBudget() {
        for (int sinkOffset = 1; sinkOffset <= 5; sinkOffset++) {
            List<BlockPlacement> out = new ArrayList<>();
            // A SHEER provider, so every cell asks for the full depth and nothing else could be
            // masking the clamp -- a terraced one reaches its maximum only in the middle.
            Set<Coords2D> dug = excavate(room(13),
                    new PitPatternEntry(new HazardPitShape(5, 9, 0, 0, Optional.empty(), Map.of(), 0)),
                    sinkOffset, out);
            Map<Coords2D, Integer> depthOf = depths(FLOOR_Y, dug, stamp(out));

            assertEquals(sinkOffset,
                    depthOf.values().stream().mapToInt(Integer::intValue).max().orElseThrow(),
                    "a 9-deep shaft on a floor that sank " + sinkOffset + " must stop at "
                            + sinkOffset + " -- the clamp is on the WRITE, not on a field, so no"
                            + " provider can get past it");
        }
    }

    /** The deepest block written is never below {@code floorY - sinkOffset}. Swept, not spot-checked. */
    @Test
    void nothingIsEverWrittenBelowTheFloorsOwnBudget() {
        for (int sinkOffset = 1; sinkOffset <= 5; sinkOffset++) {
            for (int authored = 1; authored <= 9; authored++) {
                List<BlockPlacement> out = new ArrayList<>();
                excavate(room(13), new PitPatternEntry(new InsetPitShape(1, authored)),
                        sinkOffset, out);

                int lowest = out.stream().mapToInt(BlockPlacement::getY).min().orElse(FLOOR_Y);
                assertTrue(lowest >= FLOOR_Y - sinkOffset,
                        "sink_offset " + sinkOffset + ", authored depth " + authored
                                + ": wrote at Y=" + lowest + ", below the floor's budget of "
                                + (FLOOR_Y - sinkOffset) + " -- that is the gap between floors");
            }
        }
    }

    // ---------- what a pit looks like ----------

    /** The depth of each excavated cell, read back out of what was written. */
    private static Map<Coords2D, Integer> depths(int floorY, Set<Coords2D> dug,
                                                 Map<Coords2D, Map<Integer, BlockState>> world) {
        Map<Coords2D, Integer> out = new HashMap<>();
        dug.forEach((cell) -> world.get(cell).forEach((y, state) -> {
            if (!state.isAir()) {
                out.merge(cell, floorY - y, Math::min);
            }
        }));
        return out;
    }

    /**
     * <strong>The property that makes a court walkable: every step is one block.</strong> A player
     * can only jump onto a block one high, so a step of two anywhere is a place they fall in and
     * cannot climb out of &mdash; which is exactly what the first, sheer-sided version was
     * everywhere.
     *
     * <p>Swept over both shapes and a range of depths, because the failure is local: one bad step
     * in one corner strands a player just as thoroughly as a sheer wall all the way round.</p>
     */
    @Test
    void everyStepIsExactlyOneBlockSoTheCourtCanBeWalkedOutOf() {
        for (int depth = 1; depth <= 5; depth++) {
            for (PitPatternEntry entry : List.of(
                    new PitPatternEntry(new CentrePitShape(9, depth)),
                    new PitPatternEntry(new InsetPitShape(1, depth)))) {
                List<BlockPlacement> out = new ArrayList<>();
                Set<Coords2D> dug = excavate(room(15), entry, 5, out);
                Map<Coords2D, Integer> depthOf = depths(FLOOR_Y, dug, stamp(out));

                for (Coords2D cell : dug) {
                    for (Coords2D neighbour : List.of(
                            new Coords2D(cell.getX() + 1, cell.getY()),
                            new Coords2D(cell.getX(), cell.getY() + 1))) {
                        // Against the room's own floor (depth 0) when the neighbour is not dug.
                        int here = depthOf.get(cell);
                        int there = dug.contains(neighbour) ? depthOf.get(neighbour) : 0;
                        assertTrue(Math.abs(here - there) <= 1,
                                "depth " + depth + ": " + cell + " at " + here + " and "
                                        + neighbour + " at " + there + " are a "
                                        + Math.abs(here - there) + "-block step");
                    }
                }
            }
        }
    }

    /** The rim is one step down, and the middle is the deepest -- it descends, it does not drop. */
    @Test
    void aCourtTerracesInwardFromItsRim() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(15), new PitPatternEntry(new CentrePitShape(5, 3)), 5, out);
        Map<Coords2D, Integer> depthOf = depths(FLOOR_Y, dug, stamp(out));

        assertEquals(25, dug.size(), "a 5x5 court");
        int minX = dug.stream().mapToInt(Coords2D::getX).min().orElseThrow();
        int minZ = dug.stream().mapToInt(Coords2D::getY).min().orElseThrow();

        assertEquals(1, depthOf.get(new Coords2D(minX, minZ)), "the rim is one step down");
        assertEquals(2, depthOf.get(new Coords2D(minX + 1, minZ + 1)), "then two");
        assertEquals(3, depthOf.get(new Coords2D(minX + 2, minZ + 2)), "and the middle is three");
    }

    /**
     * {@code depth} is a MAXIMUM, not a promise: a footprint too narrow to hold that many terraces
     * reaches its own middle and stops. The alternative would be a stepped rim around a sheer drop,
     * which is the trap this design exists to avoid.
     */
    @Test
    void aCourtTooNarrowForItsDepthStopsAtItsOwnMiddle() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(11), new PitPatternEntry(new CentrePitShape(3, 5)), 5, out);
        Map<Coords2D, Integer> depthOf = depths(FLOOR_Y, dug, stamp(out));

        assertEquals(2, depthOf.values().stream().mapToInt(Integer::intValue).max().orElseThrow(),
                "a 3x3 court is two deep however deep it was authored");
    }

    /** Above every terrace is air, right up through the plane the room was paved at. */
    @Test
    void aCourtIsClearedRightUpToTheWalkingPlane() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(11), new PitPatternEntry(new CentrePitShape(3, 2)), 4, out);
        Map<Coords2D, Map<Integer, BlockState>> world = stamp(out);
        Map<Coords2D, Integer> depthOf = depths(FLOOR_Y, dug, world);

        for (Coords2D cell : dug) {
            int floor = FLOOR_Y - depthOf.get(cell);
            assertEquals(Blocks.STONE_BRICKS, world.get(cell).get(floor).getBlock(),
                    "the terrace's own slab, paved like the floor above it");
            for (int y = floor + 1; y <= FLOOR_Y; y++) {
                assertEquals(Blocks.AIR, world.get(cell).get(y).getBlock(),
                        "cell " + cell + " should be clear at Y=" + y);
            }
        }
    }

    /** The authored block paves every terrace -- that is how a court reads as its own structure. */
    @Test
    void authoredBlocksBeatTheFloorsOwnPaving() {
        List<BlockPlacement> out = new ArrayList<>();
        PitPatternEntry entry = new PitPatternEntry(new CentrePitShape(3, 2),
                Optional.of("minecraft:gravel"), SizeGate.UNBOUNDED);
        Set<Coords2D> dug = excavate(room(11), entry, 4, out);
        Map<Coords2D, Map<Integer, BlockState>> world = stamp(out);
        Map<Coords2D, Integer> depthOf = depths(FLOOR_Y, dug, world);

        for (Coords2D cell : dug) {
            assertEquals(Blocks.GRAVEL, world.get(cell).get(FLOOR_Y - depthOf.get(cell)).getBlock(),
                    "every terrace is paved with the authored block, not just the bottom");
        }
    }

    // ---------- the shapes ----------

    /**
     * A centre pit keeps a walkable ring: the widest it can ever be is two cells short of the
     * interior, so a room never becomes a hole you cannot walk around.
     */
    @Test
    void aCentrePitNeverReachesTheWallRing() {
        for (int side = 7; side <= 19; side += 2) {
            List<BlockPlacement> out = new ArrayList<>();
            Set<Coords2D> dug = excavate(room(side), new PitPatternEntry(new CentrePitShape(99, 2)),
                    4, out);

            assertFalse(dug.isEmpty(), "no pit at all in a " + side + "-wide room");
            for (Coords2D cell : dug) {
                assertTrue(cell.getX() >= 2 && cell.getX() <= side - 3
                                && cell.getY() >= 2 && cell.getY() <= side - 3,
                        "room " + side + ": cell " + cell + " is against the wall ring");
            }
        }
    }

    /** An inset pit sinks the interior but for its walkway, so the doorways stay reachable. */
    @Test
    void anInsetPitLeavesItsWalkway() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(11), new PitPatternEntry(new InsetPitShape(1, 2)), 4, out);

        // interior is 9x9 at floor-local 1..9; inset 1 sinks 7x7 at 2..8
        assertEquals(49, dug.size());
        for (Coords2D cell : dug) {
            assertTrue(cell.getX() >= 2 && cell.getX() <= 8
                            && cell.getY() >= 2 && cell.getY() <= 8,
                    "cell " + cell + " is in the walkway");
        }
    }

    /** A shape too big for its room yields no pit rather than throwing or digging a sliver. */
    @Test
    void aShapeThatCannotFitDigsNothing() {
        List<BlockPlacement> out = new ArrayList<>();
        assertTrue(excavate(room(5), new PitPatternEntry(new InsetPitShape(3, 2)), 4, out).isEmpty());
        assertTrue(out.isEmpty());
    }

    // ---------- nothing stands on a hole ----------

    /**
     * The excavated cells are handed back so the caller can CLAIM them, and
     * {@code BasicRoomGenerator} must do so. Nothing may stand on a pit cell: it is not floor, so a
     * chest or spawner placed there hangs in mid-air and a pot drops in and shatters as soon as the
     * chunk ticks &mdash; the same gravity trap the chest-before-pots ordering already exists for.
     *
     * <p>Asserted on the RETURN VALUE rather than through the generator, because the return value is
     * the contract: a caller that ignores it gets exactly the bug this pins, and that is what the
     * first version of the wiring did.</p>
     */
    @Test
    void theExcavatedCellsAreReportedSoTheyCanBeClaimed() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(11), new PitPatternEntry(new CentrePitShape(3, 2)), 4, out);

        assertEquals(9, dug.size());
        // Floor-local, matching what the generators' occupiedFloorCells() speak, so the caller can
        // union them without translating -- interior-local would silently be off by one.
        for (Coords2D cell : dug) {
            assertTrue(cell.getX() >= 1 && cell.getX() <= 9 && cell.getY() >= 1 && cell.getY() <= 9,
                    "cell " + cell + " is not floor-local within the room");
        }
        assertTrue(dug.contains(new Coords2D(5, 5)), "the centre of an 11-wide room");
    }

    // ---------- the hazard provider ----------

    /**
     * A hazard shaft is SHEER: every cell at the full depth, walls straight down. That is the
     * feature, not a defect &mdash; a player can jump onto a block one high, so anything two or
     * more deep is one they fall into and cannot climb out of. It is a separate provider precisely
     * so an author has to name it rather than reaching it by tuning a court.
     */
    @Test
    void aHazardShaftIsSheerRatherThanTerraced() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(13),
                new PitPatternEntry(new HazardPitShape(3, 4, 0, 0, Optional.empty(), Map.of(), 0)),
                5, out);
        Map<Coords2D, Integer> depthOf = depths(FLOOR_Y, dug, stamp(out));

        assertEquals(9, dug.size(), "a 3x3 shaft");
        for (Coords2D cell : dug) {
            assertEquals(4, depthOf.get(cell), "cell " + cell + " should be at the full depth");
        }
    }

    /** Spikes stand ON the shaft floor, one block up, and only where the roll put them. */
    @Test
    void spikesStandOnTheShaftFloor() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(13),
                new PitPatternEntry(new HazardPitShape(3, 4, 0, 0,
                        Optional.of("minecraft:pointed_dripstone"),
                        Map.of("vertical_direction", "up"), 1.0D)),
                5, out);
        Map<Coords2D, Map<Integer, BlockState>> world = stamp(out);

        for (Coords2D cell : dug) {
            BlockState spike = world.get(cell).get(FLOOR_Y - 4 + 1);
            assertEquals(Blocks.POINTED_DRIPSTONE, spike.getBlock(), "no spike at " + cell);
            assertEquals(Direction.UP, spike.getValue(BlockStateProperties.VERTICAL_DIRECTION),
                    "a spike must point UP -- only the upward tip multiplies fall damage, so a"
                            + " downward one is decoration");
        }
    }

    /** Probability 0 is a plain oubliette, and the default (no block) places nothing either. */
    @Test
    void aHazardWithoutSpikesIsJustAShaft() {
        for (PitPatternEntry entry : List.of(
                new PitPatternEntry(new HazardPitShape(3, 4, 0, 0,
                        Optional.of("minecraft:pointed_dripstone"), Map.of(), 0.0D)),
                new PitPatternEntry(new HazardPitShape()))) {
            List<BlockPlacement> out = new ArrayList<>();
            excavate(room(13), entry, 5, out);
            assertTrue(out.stream().noneMatch(p -> p.getBlockId().contains("dripstone")),
                    "a shaft with no spike roll placed one anyway");
        }
    }

    /**
     * The hazard's rim is a CLOSED ring at the room's own walking plane: every cell touching the
     * mouth, corners included. The court's stair rim leaves corners plain because a stair there
     * would face two ways at once; this ring exists to be READ, and one with four gaps in it reads
     * as four strips rather than as an edge.
     */
    @Test
    void aHazardRimClosesAroundTheMouthAtTheWalkingPlane() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(13),
                new PitPatternEntry(new HazardPitShape(3, 4, 0, 0, Optional.empty(), Map.of(), 0,
                        Optional.of("minecraft:packed_mud"))),
                5, out);
        Map<Coords2D, Map<Integer, BlockState>> world = stamp(out);

        assertEquals(9, dug.size(), "a 3x3 shaft");
        int minX = dug.stream().mapToInt(Coords2D::getX).min().orElseThrow();
        int minZ = dug.stream().mapToInt(Coords2D::getY).min().orElseThrow();
        for (int x = minX - 1; x <= minX + 3; x++) {
            for (int z = minZ - 1; z <= minZ + 3; z++) {
                Coords2D cell = new Coords2D(x, z);
                if (dug.contains(cell)) {
                    continue;
                }
                assertEquals(Blocks.PACKED_MUD, world.get(cell).get(FLOOR_Y).getBlock(),
                        "the rim should close at " + cell);
                assertFalse(dug.contains(cell), "a rim cell is a tell, not part of the pit");
            }
        }
    }

    /** Unauthored, the mouth is flush: nothing is laid around it and the trap is unmarked. */
    @Test
    void aHazardWithoutARimIsUnmarked() {
        List<BlockPlacement> out = new ArrayList<>();
        excavate(room(13),
                new PitPatternEntry(new HazardPitShape(3, 4, 0, 0, Optional.empty(), Map.of(), 0)),
                5, out);

        assertTrue(out.stream().noneMatch(p -> p.getY() == FLOOR_Y
                        && !p.getBlockId().contains("air")),
                "an unauthored rim wrote a block at the walking plane");
    }

    /**
     * A sheer shaft is a CLOSED BOX. Every column touching it is backed with the pit's own floor
     * block from the shaft floor up to the room's walking plane, because below that plane there is
     * nothing but the terrain the dungeon was carved into &mdash; and terrain includes caves and
     * aquifers. This is the in-game failure that put the lining back (Gottsch, 2026-08-29): a shaft
     * opened into a cavern and poured a waterfall down its own side.
     */
    @Test
    void aSheerShaftIsLinedOnEverySideIncludingTheDiagonals() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(13),
                new PitPatternEntry(new HazardPitShape(3, 4, 0, 0, Optional.empty(), Map.of(), 0)),
                5, out);
        Map<Coords2D, Map<Integer, BlockState>> world = stamp(out);

        int minX = dug.stream().mapToInt(Coords2D::getX).min().orElseThrow();
        int minZ = dug.stream().mapToInt(Coords2D::getY).min().orElseThrow();
        for (int x = minX - 1; x <= minX + 3; x++) {
            for (int z = minZ - 1; z <= minZ + 3; z++) {
                Coords2D cell = new Coords2D(x, z);
                if (dug.contains(cell)) {
                    continue;
                }
                // The face runs from the shaft's own floor to the block under the walking plane:
                // a player standing in the shaft can see all of it.
                for (int y = FLOOR_Y - 4; y <= FLOOR_Y - 1; y++) {
                    assertEquals(Blocks.STONE_BRICKS, world.get(cell).get(y).getBlock(),
                            "unlined face at " + cell + " y=" + y + " -- terrain would show here");
                }
            }
        }
    }

    /** The lining is the pit's own floor block, so an authored pit is lined in what it is made of. */
    @Test
    void theLiningIsThePitsOwnFloorBlock() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(13),
                new PitPatternEntry(new HazardPitShape(3, 3, 0, 0, Optional.empty(), Map.of(), 0),
                        Optional.of("minecraft:deepslate_bricks"), SizeGate.UNBOUNDED),
                5, out);
        Map<Coords2D, Map<Integer, BlockState>> world = stamp(out);

        int minX = dug.stream().mapToInt(Coords2D::getX).min().orElseThrow();
        int minZ = dug.stream().mapToInt(Coords2D::getY).min().orElseThrow();
        assertEquals(Blocks.DEEPSLATE_BRICKS,
                world.get(new Coords2D(minX - 1, minZ)).get(FLOOR_Y - 1).getBlock(),
                "the face should be lined in the block the pit is floored with");
    }

    /**
     * A terraced court is lined too, and it costs one cell per face &mdash; the sliver UNDER the
     * next terrace's slab, which is the only terrain a court could ever show. The court's own faces
     * are still the slabs themselves, so nothing about how it reads changes.
     */
    @Test
    void aTerracedCourtIsLinedUnderItsSlabsAndNowhereElse() {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> dug = excavate(room(13), new PitPatternEntry(new CentrePitShape(5, 2)), 5, out);
        Map<Coords2D, Map<Integer, BlockState>> world = stamp(out);

        int minX = dug.stream().mapToInt(Coords2D::getX).min().orElseThrow();
        int minZ = dug.stream().mapToInt(Coords2D::getY).min().orElseThrow();
        Coords2D outside = new Coords2D(minX - 1, minZ + 1);
        assertEquals(Blocks.STONE_BRICKS, world.get(outside).get(FLOOR_Y - 1).getBlock(),
                "the cell under an undug neighbour's floor is the court's one exposed sliver");
        assertFalse(world.get(outside).containsKey(FLOOR_Y - 2),
                "a one-deep face needs one cell of lining, not the whole column");
    }

    /** An offset shaft stays inside the interior's walkable ring, so a trap never blocks its room. */
    @Test
    void anOffsetShaftStaysOffTheWalls() {
        for (int offset = -9; offset <= 9; offset += 3) {
            List<BlockPlacement> out = new ArrayList<>();
            Set<Coords2D> dug = excavate(room(13),
                    new PitPatternEntry(new HazardPitShape(3, 3, offset, offset,
                            Optional.empty(), Map.of(), 0)),
                    5, out);

            for (Coords2D cell : dug) {
                assertTrue(cell.getX() >= 2 && cell.getX() <= 10
                                && cell.getY() >= 2 && cell.getY() <= 10,
                        "offset " + offset + ": cell " + cell + " is against the wall ring");
            }
        }
    }
}
