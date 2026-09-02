package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.PartitionPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.partition.CornerPartitionShape;
import mod.gottsch.forge.dungeons2.core.config.partition.CornerPartitionShape.Corner;
import mod.gottsch.forge.dungeons2.core.config.partition.StripPartitionShape;
import mod.gottsch.forge.dungeons2.core.config.partition.StripPartitionShape.Axis;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code partition} slot (#74). Pure data &mdash; a block id stays a string until the piece
 * resolves it, so no Minecraft bootstrap is needed.
 *
 * <p>A partition is the first thing that changes the SHAPE of the room, so the invariants worth
 * asserting are the ones that would make it a bad shape rather than a missing one: a run that
 * crosses a doorway (the room would seal or not depending on where the maze put a door), a run
 * taller than the room (it would overwrite the ceiling), a run with no way through it, and a
 * doorway that opens straight into the cage.</p>
 */
class RoomPartitionGeneratorTest {

    private static final int FLOOR_Y = 60;
    private static final String BARS = "minecraft:iron_bars";

    /** A 13x13 room at origin (10,10), 8 high, with one door in the middle of the north wall. */
    private static RoomData room() {
        RoomData room = new RoomData(1, 10, 10, 13, 13, 8, RoomRole.NORMAL);
        room.setDoorways(new ArrayList<>(List.of(new Coords2D(16, 10))));
        return room;
    }

    private static PartitionPatternEntry corner(int width, int depth, Corner where) {
        return new PartitionPatternEntry(new CornerPartitionShape(width, depth, where), BARS);
    }

    private static PartitionPatternEntry strip(Axis axis, Integer offset) {
        return new PartitionPatternEntry(
                new StripPartitionShape(axis, Optional.ofNullable(offset)), BARS);
    }

    private static List<BlockPlacement> place(RoomData room, PartitionPatternEntry config,
                                              Set<Coords2D> occupied, long seed) {
        List<BlockPlacement> out = new ArrayList<>();
        RoomPartitionGenerator.build(room, FLOOR_Y, config, occupied, RandomSource.create(seed), out);
        return out;
    }

    private static List<BlockPlacement> place(RoomData room, PartitionPatternEntry config, long seed) {
        return place(room, config, Set.of(), seed);
    }

    private static Set<Coords2D> cellsOf(List<BlockPlacement> placements) {
        return placements.stream().map(p -> new Coords2D(p.getX(), p.getZ()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // ---- the corner cell ------------------------------------------------------------------------

    /**
     * A 3x3 cell in the north-west corner puts an L of seven cells around it: four along each leg,
     * sharing the cell where they meet. One of the seven is the way through, so six are bars.
     */
    @Test
    void aCornerCellIsAnLOfBarsWithOneWayThrough() {
        List<BlockPlacement> placed = place(room(), corner(3, 3, Corner.NORTH_WEST), 1L);
        Set<Coords2D> cells = cellsOf(placed);
        // Interior starts at floor-local (11,11). The L sits at x=14 and z=14.
        Set<Coords2D> expected = new HashSet<>(List.of(
                new Coords2D(14, 11), new Coords2D(14, 12), new Coords2D(14, 13),
                new Coords2D(14, 14),
                new Coords2D(11, 14), new Coords2D(12, 14), new Coords2D(13, 14)));
        expected.remove(new Coords2D(14, 12)); // the gap, which carries no block
        assertEquals(expected, cells);
    }

    /** Full height by default, standing on the walking plane. Three is over a player's jump. */
    @Test
    void barsStandThreeHighFromTheWalkingPlane() {
        List<BlockPlacement> placed = place(room(), corner(3, 3, Corner.NORTH_WEST), 1L);
        Coords2D any = cellsOf(placed).iterator().next();
        List<Integer> rows = placed.stream()
                .filter(p -> p.getX() == any.getX() && p.getZ() == any.getY())
                .map(BlockPlacement::getY).sorted().toList();
        assertEquals(List.of(FLOOR_Y + 1, FLOOR_Y + 2, FLOOR_Y + 3), rows);
    }

    /**
     * The corner is a roll, not a constant, and all four come up. A shape that always chose one
     * corner would look correct in any single screenshot.
     *
     * <p><strong>The seeds are scattered on purpose, and it is worth knowing why.</strong>
     * {@code RandomSource.create(n).nextInt(4)} returns the SAME value for every small sequential
     * {@code n} -- Java's LCG barely moves the high bits of its first output, and {@code nextInt}
     * reads the high bits. Seeding 0, 1, 2, 3 here produced one corner sixty times over, which
     * looks exactly like a constant. It is not a fault in the shape and it is not a fault in
     * generation either: by the time this runs, the room's source has already been through the
     * scheme roll and four generators, so the first-draw weakness does not apply. It is a fault in
     * any TEST that seeds sequentially and then draws immediately.</p>
     */
    @Test
    void anyRollsAllFourCorners() {
        Set<Coords2D> firstCells = new HashSet<>();
        for (long seed = 0; seed < 60; seed++) {
            List<BlockPlacement> placed = place(room(), corner(3, 3, Corner.ANY), seed * 7919L + 13L);
            firstCells.add(cellsOf(placed).iterator().next());
        }
        assertEquals(4, firstCells.size(),
                "expected all four corners over 60 seeds, saw " + firstCells);
    }

    /** The same seed gives the same partition; a piece renders once per overlapping chunk. */
    @Test
    void thePlanIsAPureFunctionOfItsSeed() {
        RoomData room = room();
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(place(room, corner(3, 3, Corner.ANY), seed).toString(),
                    place(room, corner(3, 3, Corner.ANY), seed).toString());
        }
    }

    /**
     * A cell too big for the room draws NOTHING rather than being squeezed. The room has to hold the
     * cell, the line around it, and something on the far side -- a partition with nothing beyond it
     * is just a smaller room.
     */
    @Test
    void aCellTooBigForTheRoomDrawsNothing() {
        // 13x13 room: an 11x11 interior, so a cell of 10 needs 12 and cannot fit.
        assertTrue(place(room(), corner(10, 10, Corner.NORTH_WEST), 1L).isEmpty());
    }

    // ---- the strip -----------------------------------------------------------------------------

    /** A strip runs the full interior span, less its one gap. */
    @Test
    void aStripRunsWallToWallWithOneWayThrough() {
        Set<Coords2D> cells = cellsOf(place(room(), strip(Axis.X, 5), 1L));
        assertEquals(10, cells.size(), "an 11-wide interior, less the gap");
        for (Coords2D cell : cells) {
            assertEquals(16, cell.getY(), "the whole run sits on one z: " + cell);
        }
        assertFalse(cells.contains(new Coords2D(16, 16)), "the middle cell is the way through");
    }

    /** An offset that would put the run against a wall is clamped, not dropped. */
    @Test
    void anOffsetHardAgainstAWallIsClampedInsideTheRoom() {
        Set<Coords2D> cells = cellsOf(place(room(), strip(Axis.X, 0), 1L));
        assertFalse(cells.isEmpty(), "clamped, not dropped");
        assertEquals(12, cells.iterator().next().getY(), "clamped to the second interior row");
    }

    /** A room too narrow to have a cell on each side of the run gets no partition. */
    @Test
    void aRoomWithNoRoomOnBothSidesGetsNoStrip() {
        // A 4-deep room has a 2-deep interior: a run in it leaves nothing on one side.
        RoomData narrow = new RoomData(2, 0, 0, 9, 4, 8, RoomRole.NORMAL);
        assertTrue(place(narrow, strip(Axis.X, null), 1L).isEmpty());
    }

    // ---- the rules it refuses to break -----------------------------------------------------------

    /**
     * A run that would cross a doorway approach builds NOTHING &mdash; it does not skip the cell.
     * A partition with a hole in it wherever the maze happened to put a door is a broken partition,
     * and it would look fine in ninety-nine rooms and absurd in the hundredth.
     */
    @Test
    void aRunCrossingADoorwayApproachIsRefusedEntirely() {
        RoomData room = room();
        // The north door at (16,10) approaches (16,11). A strip along X at that row crosses it.
        assertTrue(place(room, strip(Axis.X, 0), 1L).stream()
                        .noneMatch(p -> p.getZ() == 11),
                "nothing should be written on the approach row");
        RoomData crossing = new RoomData(3, 10, 10, 13, 13, 8, RoomRole.NORMAL);
        crossing.setDoorways(new ArrayList<>(List.of(new Coords2D(16, 10))));
        // offset 5 puts the run at z = 16; move the door so its approach lands there instead.
        crossing.setDoorways(new ArrayList<>(List.of(new Coords2D(16, 15))));
        assertTrue(place(crossing, strip(Axis.X, 5), 1L).isEmpty(),
                "a run through a doorway approach is refused, not holed");
    }

    /**
     * A doorway that opens straight into the cage is refused too. Content inside an enclosure is
     * fine &mdash; the shape always cuts a gap, so it is content behind a door &mdash; but a player
     * arriving inside one reads as a generation fault.
     */
    @Test
    void aDoorwayOpeningIntoTheCageIsRefused() {
        RoomData room = new RoomData(4, 10, 10, 13, 13, 8, RoomRole.NORMAL);
        // A north-wall door at x=12 approaches (12,11), which is inside a 3x3 north-west cell.
        room.setDoorways(new ArrayList<>(List.of(new Coords2D(12, 10))));
        assertTrue(place(room, corner(3, 3, Corner.NORTH_WEST), 1L).isEmpty());
        // The same room, cage in the far corner: builds.
        assertFalse(place(room, corner(3, 3, Corner.SOUTH_EAST), 1L).isEmpty());
    }

    /** A cell an earlier slot already claimed refuses the whole partition, for the same reason. */
    @Test
    void aRunThroughAnOccupiedCellIsRefusedEntirely() {
        RoomData room = room();
        Set<Coords2D> occupied = Set.of(new Coords2D(14, 13));
        assertTrue(place(room, corner(3, 3, Corner.NORTH_WEST), occupied, 1L).isEmpty());
    }

    /**
     * Height is clamped to the room, not to the config. A partition as tall as the room would
     * overwrite the ceiling; one that reached it would read as a structural wall rather than as a
     * screen inside a room.
     */
    @Test
    void heightIsClampedToTheRoomsInteriorRows() {
        PartitionPatternEntry tall = new PartitionPatternEntry(
                new CornerPartitionShape(3, 3, Corner.NORTH_WEST), BARS, Optional.empty(), 8,
                SizeGate.UNBOUNDED);
        // A 6-high room has 4 interior rows.
        RoomData short6 = new RoomData(5, 10, 10, 13, 13, 6, RoomRole.NORMAL);
        int highest = place(short6, tall, 1L).stream().mapToInt(BlockPlacement::getY).max()
                .orElseThrow();
        assertEquals(FLOOR_Y + 4, highest, "the ceiling sits at floorY + 5 and must stay clear");
        assertEquals(4, tall.heightWithin(6));
        assertEquals(0, tall.heightWithin(2), "a room with no interior rows gets no partition");
    }

    // ---- the way through -------------------------------------------------------------------------

    /** With no {@code gap_block} the way through is open air, and nothing is written there. */
    @Test
    void anUnauthoredGapIsLeftOpen() {
        Set<Coords2D> cells = cellsOf(place(room(), corner(3, 3, Corner.NORTH_WEST), 1L));
        assertFalse(cells.contains(new Coords2D(14, 12)), "the gap carries no block");
    }

    /**
     * An authored one is written on the two rows a player walks through, with {@code half} set so a
     * vanilla door hangs, and facing out of the cell rather than into it.
     */
    @Test
    void anAuthoredGapBlockHangsAsATwoHighDoor() {
        PartitionPatternEntry withDoor = new PartitionPatternEntry(
                new CornerPartitionShape(3, 3, Corner.NORTH_WEST), BARS,
                Optional.of("minecraft:iron_door"), PartitionPatternEntry.DEFAULT_HEIGHT,
                SizeGate.UNBOUNDED);
        List<BlockPlacement> door = place(room(), withDoor, 1L).stream()
                .filter(p -> "minecraft:iron_door".equals(p.getBlockId())).toList();
        assertEquals(2, door.size(), "exactly two halves, however tall the bars are");
        assertEquals(FLOOR_Y + 1, door.get(0).getY());
        assertEquals("lower", door.get(0).getProperties().get("half"));
        assertEquals(FLOOR_Y + 2, door.get(1).getY());
        assertEquals("upper", door.get(1).getProperties().get("half"));
        // A north-west cell is at low X, so the way out is toward +X.
        assertEquals("east", door.get(0).getProperties().get("facing"));
        assertEquals("east", door.get(1).getProperties().get("facing"));
    }

    /** Every shape leaves a way through. A sealed enclosure is content nobody can reach. */
    @Test
    void everyShapeLeavesAWayThrough() {
        RoomData room = new RoomData(6, 0, 0, 15, 15, 8, RoomRole.NORMAL);
        for (Corner where : Corner.values()) {
            Set<Coords2D> cells = cellsOf(place(room, corner(3, 3, where), 3L));
            // 3x3 cell -> an L of 7 cells, one of which is the gap and carries nothing.
            assertEquals(6, cells.size(), where + " left no way through");
        }
        for (Axis axis : Axis.values()) {
            Set<Coords2D> cells = cellsOf(place(room, strip(axis, null), 3L));
            assertEquals(12, cells.size(), axis + ": a 13-wide interior, less the gap");
        }
    }
}
