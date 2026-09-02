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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPatternRegistry;
import mod.gottsch.forge.dungeons2.core.config.floor.WornPathFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.WornPathFloorPattern.PathRouting;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The worn path (#71): the one floor pattern generated from the room's own plan rather than authored
 * cell by cell.
 *
 * <h2>What is actually at risk here</h2>
 * <p>Not the falloff, which is the same ramp the two gradients use. It is the <strong>coordinate
 * conversion</strong>: a doorway is stored in floor-local space and the pattern draws in room-local
 * space, and an origin-sized error still produces a perfectly plausible-looking path across the
 * wrong part of the room. Nobody would ever report that as a bug. So the tests below pin the path to
 * the doors it was built from rather than checking that a path exists.</p>
 */
class WornPathFloorPatternTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int FLOOR_Y = 60;
    private static final int ORIGIN_X = 10;
    private static final int ORIGIN_Z = 20;

    private static String pathId() {
        return ForgeRegistries.BLOCKS.getKey(Blocks.PACKED_MUD).toString();
    }

    /** An 11x11 room whose doors are given in ROOM-LOCAL cells and stored floor-local, as the maze does. */
    private static RoomData roomWithDoors(int... localXZ) {
        RoomData room = new RoomData(1, ORIGIN_X, ORIGIN_Z, 11, 11, 6, RoomRole.NORMAL);
        for (int i = 0; i < localXZ.length; i += 2) {
            room.getDoorways().add(
                    new Coords2D(ORIGIN_X + localXZ[i], ORIGIN_Z + localXZ[i + 1]));
        }
        return room;
    }

    private static WornPathFloorPatternProvider provider(int width, PathRouting routing) {
        return new WornPathFloorPatternProvider(width, 1.0D, 0.35D, routing, Blocks.PACKED_MUD,
                Blocks.STONE_BRICKS.defaultBlockState());
    }

    /** Room-local cells the path was drawn on. */
    private static Set<String> pathCells(List<BlockPlacement> out) {
        Set<String> cells = new HashSet<>();
        for (BlockPlacement bp : out) {
            if (pathId().equals(bp.getBlockId())) {
                cells.add((bp.getX() - ORIGIN_X) + "," + (bp.getZ() - ORIGIN_Z));
            }
        }
        return cells;
    }

    private static List<BlockPlacement> render(RoomData room, WornPathFloorPatternProvider provider) {
        List<BlockPlacement> out = new ArrayList<>();
        provider.build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(0xD2_71L), out);
        return out;
    }

    // ---------- the doors, and the coordinate conversion ----------

    @Test
    void doorwaysAreReadIntoRoomLocalCells() {
        // The conversion this whole pattern rests on. Stored floor-local (origin 10,20), used
        // room-local -- so a door authored at local 5,0 must come back as 5,0 and not as 15,20.
        List<int[]> doors = WornPathFloorPatternProvider.doorsOf(roomWithDoors(5, 0, 0, 5));

        assertEquals(2, doors.size());
        assertArrayEqualsish(new int[] {5, 0}, doors.get(0));
        assertArrayEqualsish(new int[] {0, 5}, doors.get(1));
    }

    @Test
    void aDoorwayOutsideTheFootprintIsDroppedRatherThanClamped() {
        RoomData room = roomWithDoors(5, 0);
        // A stale door from another room: clamping it would invent a door on the nearest wall, which
        // looks exactly like a real one and would send a path to a wall with no opening in it.
        room.getDoorways().add(new Coords2D(ORIGIN_X + 40, ORIGIN_Z + 40));

        assertEquals(1, WornPathFloorPatternProvider.doorsOf(room).size());
    }

    // ---------- the geometry ----------

    @Test
    void theTrackRunsBetweenTheTwoDoorsAndNotAcrossTheRestOfTheRoom() {
        // Doors facing each other on the north and south walls: the track is the column between them.
        Set<String> cells = pathCells(render(roomWithDoors(5, 0, 5, 10), provider(1, PathRouting.PAIRS)));

        for (int z = 0; z <= 10; z++) {
            assertTrue(cells.contains("5," + z), "the track is broken at z=" + z);
        }
        assertFalse(cells.contains("1,1"), "the path reached a corner it has no business in");
        assertFalse(cells.contains("9,9"), "the path reached a corner it has no business in");
    }

    @Test
    void aPathStopsAtItsDoorsRatherThanRunningOnToTheFarWall() {
        // Both doors on the SAME wall, three cells apart. The line through them continues to both
        // corners if the distance is measured to an infinite line instead of to the segment -- which
        // is the classic version of this bug and looks like a stripe along the wall.
        Set<String> cells = pathCells(render(roomWithDoors(3, 0, 7, 0), provider(1, PathRouting.PAIRS)));

        assertTrue(cells.contains("5,0"), "the track between the two doors is missing");
        assertFalse(cells.contains("0,0"), "the track ran past its door to the corner");
        assertFalse(cells.contains("10,0"), "the track ran past its door to the corner");
    }

    @Test
    void aRoomWithOneDoorOrNoneDrawsNoPathAtAll() {
        assertTrue(pathCells(render(roomWithDoors(5, 0), provider(3, PathRouting.AUTO))).isEmpty(),
                "a dead end has no traffic to wear a path");
        assertTrue(pathCells(render(roomWithDoors(), provider(3, PathRouting.AUTO))).isEmpty());
    }

    @Test
    void theBandWidensWithWidth() {
        RoomData room = roomWithDoors(5, 0, 5, 10);
        Set<String> narrow = pathCells(render(room, provider(1, PathRouting.PAIRS)));
        Set<String> wide = pathCells(render(room, provider(3, PathRouting.PAIRS)));

        // Wider is not merely more cells, it is cells OFF the centre line -- at centre_probability
        // 1.0 the middle column is already solid in both. Counted over the whole run rather than
        // asserted on one named cell: the edge is a 0.35 roll, so any single cell off the line is a
        // coin flip and pinning one would be a flaky test dressed up as a strict one.
        assertEquals(0, offCentreColumn(narrow), "a 1-wide band must be exactly the centre column");
        assertTrue(offCentreColumn(wide) > 0, "a 3-wide band never left the centre column");
        assertTrue(wide.size() > narrow.size());
    }

    /** Path cells whose x is not the centre column of the 11-wide test room. */
    private static long offCentreColumn(Set<String> cells) {
        return cells.stream().filter(cell -> !cell.startsWith("5,")).count();
    }

    // ---------- routing ----------

    @Test
    void autoRoutesFourDoorsThroughTheMiddleRatherThanPairingThemAll() {
        // Four doors is six pairs, and six lines across one floor is a repaint. The star's tell is
        // that the two DIAGONAL runs a full pairing would draw are absent -- e.g. the cell halfway
        // between the north and east doors, which no spoke passes through.
        RoomData room = roomWithDoors(5, 0, 5, 10, 0, 5, 10, 5);
        Set<String> auto = pathCells(render(room, provider(1, PathRouting.AUTO)));
        Set<String> pairs = pathCells(render(room, provider(1, PathRouting.PAIRS)));

        assertTrue(auto.contains("5,5"), "every spoke should meet at the middle of the room");
        assertTrue(pairs.contains("7,2") || pairs.contains("8,3"),
                "an all-pairs routing draws the diagonals");
        assertFalse(auto.contains("8,3"), "the star should not draw a diagonal between two doors");
        assertTrue(auto.size() < pairs.size(), "the star should cover less floor than all pairs");
    }

    @Test
    void underTheLimitAutoIsPairs() {
        // Three doors still reads as three walked lines, so auto must not pull them through the
        // middle -- that is the case the threshold exists to leave alone.
        RoomData room = roomWithDoors(5, 0, 0, 5, 10, 5);
        assertEquals(pathCells(render(room, provider(1, PathRouting.PAIRS))),
                pathCells(render(room, provider(1, PathRouting.AUTO))));
    }

    // ---------- the falloff ----------

    @Test
    void theCentreLineIsSolidAndTheEdgeIsRagged() {
        WornPathFloorPatternProvider gradientish =
                new WornPathFloorPatternProvider(5, 0.9D, 0.1D, PathRouting.PAIRS, Blocks.PACKED_MUD);

        assertEquals(0.9D, gradientish.probabilityAt(0.0D, 2.0D), 1.0e-9);
        assertEquals(0.1D, gradientish.probabilityAt(2.0D, 2.0D), 1.0e-9);
        assertEquals(0.5D, gradientish.probabilityAt(1.0D, 2.0D), 1.0e-9);
    }

    @Test
    void aOneCellPathHasNoEdgeToFadeTo() {
        // halfWidth 0 would divide by zero, and worse, applying edge_probability to the only cells
        // the path has would make a 1-wide path mostly absent.
        WornPathFloorPatternProvider narrow =
                new WornPathFloorPatternProvider(1, 1.0D, 0.0D, PathRouting.PAIRS, Blocks.PACKED_MUD);

        assertEquals(1.0D, narrow.probabilityAt(0.0D, 0.0D), 1.0e-9);
    }

    // ---------- composing ----------

    @Test
    void asAnOverlayItTouchesOnlyItsOwnCells() {
        // What makes `composite: [gradient, path]` work: the overlay form must not emit a base-block
        // placement for the cells it does not want, or it repaints the gradient underneath it.
        List<BlockPlacement> out = new ArrayList<>();
        provider(3, PathRouting.PAIRS).overlay(roomWithDoors(5, 0, 5, 10), FLOOR_Y,
                DungeonMotif.CLASSIC, RandomSource.create(0xD2_72L), out);

        assertFalse(out.isEmpty(), "the overlay drew nothing at all");
        for (BlockPlacement bp : out) {
            assertEquals(pathId(), bp.getBlockId(),
                    "an overlay may only emit its own material: " + bp);
        }
    }

    @Test
    void theBuildFormFillsTheWholeFootprint() {
        List<BlockPlacement> out = render(roomWithDoors(5, 0, 5, 10), provider(3, PathRouting.PAIRS));
        assertEquals(11 * 11, out.size());
    }

    // ---------- the schema ----------

    @Test
    void theTypeIsRegisteredAndDecodesFromItsAuthoredForm() {
        DataResult<FloorPattern> result = FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "dungeons2:path",
                          "config": {
                            "path_block": "minecraft:packed_mud",
                            "width": 3,
                            "centre_probability": 0.95,
                            "edge_probability": 0.3,
                            "routing": "star"
                          }
                        }"""));

        WornPathFloorPattern pattern = assertInstanceOf(WornPathFloorPattern.class,
                result.result().orElseThrow(
                        () -> new AssertionError(result.error().map(Object::toString).orElse(""))));
        assertEquals(PathRouting.STAR, pattern.routing());
        assertNotNull(pattern.generator(FloorConfig.DEFAULT));
    }

    @Test
    void anUnknownRoutingIsALoadError() {
        DataResult<FloorPattern> result = FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "dungeons2:path",
                          "config": { "path_block": "minecraft:packed_mud", "routing": "wander" }
                        }"""));
        assertTrue(result.result().isEmpty(), "a routing outside the enum must not decode");
    }

    @Test
    void anUnresolvableBlockDegradesToPlainFloor() {
        WornPathFloorPattern pattern = new WornPathFloorPattern("dungeons2:no_such_block", 3, 1.0D,
                0.35D, PathRouting.AUTO);
        assertInstanceOf(BasicFloorGenerator.class, pattern.generator(FloorConfig.DEFAULT));
    }

    private static void assertArrayEqualsish(int[] expected, int[] actual) {
        assertEquals(expected[0], actual[0], "x");
        assertEquals(expected[1], actual[1], "z");
    }
}
