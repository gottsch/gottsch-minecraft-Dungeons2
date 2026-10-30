package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.PartitionPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.partition.ArcadePartitionShape;
import mod.gottsch.forge.dungeons2.core.config.partition.PartitionShapeRegistry;
import mod.gottsch.forge.dungeons2.core.config.partition.StripPartitionShape.Axis;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.ArcadePartitionShapeProvider;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code arcade} partition: piers, corbelled openings and a keystone over each, then solid wall
 * to whatever ceiling the room has.
 *
 * <p>The invariants worth asserting are the ones a screenshot of one room would not show: that the
 * bays fit any span whole and symmetric, that the wall above the arches reaches the ceiling at
 * every room height and through a vault that rose above the plane, and that the run moves aside
 * for a door rather than walling it up.</p>
 */
class ArcadePartitionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Gson GSON = new Gson();
    private static final int FLOOR_Y = 60;
    private static final String WALL = "minecraft:stone_bricks";
    private static final String BASE = "minecraft:polished_andesite";
    private static final String CORBEL = "minecraft:stone_brick_stairs";
    private static final String KEYSTONE = "minecraft:chiseled_stone_bricks";
    private static final String AIR = "minecraft:air";

    /** SplitMix64's finalizer; see RisingVaultTest#mix for why seeds are mixed. */
    private static long mix(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static ArcadePartitionShape shape(Axis axis) {
        return new ArcadePartitionShape(axis, 5, 1, 3, 2, Optional.of(BASE), Optional.of(CORBEL),
                Optional.of(KEYSTONE));
    }

    private static PartitionPatternEntry arcade(Axis axis) {
        return new PartitionPatternEntry(shape(axis), WALL);
    }

    /** A room at origin (10,10); the interior starts at floor-local (11,11). */
    private static RoomData room(int width, int depth, int height) {
        return new RoomData(1, 10, 10, width, depth, height, RoomRole.NORMAL);
    }

    private static List<BlockPlacement> build(RoomData room, PartitionPatternEntry entry,
                                              List<BlockPlacement> out, long seed) {
        RoomPartitionGenerator.build(room, FLOOR_Y, entry, Set.of(),
                RandomSource.create(mix(seed)), out);
        return out;
    }

    private static List<BlockPlacement> build(RoomData room, PartitionPatternEntry entry) {
        return build(room, entry, new ArrayList<>(), 1L);
    }

    /** Last write wins, as the piece renders it. */
    private static Map<String, BlockPlacement> settle(List<BlockPlacement> out) {
        Map<String, BlockPlacement> world = new HashMap<>();
        for (BlockPlacement p : out) {
            world.put(p.getX() + "," + p.getY() + "," + p.getZ(), p);
        }
        return world;
    }

    private static BlockPlacement at(Map<String, BlockPlacement> world, int x, int y, int z) {
        return world.get(x + "," + y + "," + z);
    }

    private static String idAt(Map<String, BlockPlacement> world, int x, int y, int z) {
        BlockPlacement p = at(world, x, y, z);
        return p == null ? null : p.getBlockId();
    }

    // ---- the bays ------------------------------------------------------------------------------

    private static List<int[]> openings(int span) {
        return new ArcadePartitionShapeProvider(shape(Axis.X)).openings(span);
    }

    private static List<List<Integer>> asLists(List<int[]> openings) {
        return openings.stream().map(o -> List.of(o[0], o[1])).toList();
    }

    @Test
    void wholeBaysAreFittedToTheSpan() {
        assertEquals(List.of(List.of(1, 5)), asLists(openings(7)), "one bay, exactly");
        assertEquals(List.of(List.of(3, 7)), asLists(openings(11)), "the spare to the end piers");
        assertEquals(List.of(List.of(1, 5), List.of(7, 11)), asLists(openings(13)));
        assertEquals(List.of(List.of(1, 5), List.of(8, 12)), asLists(openings(14)),
                "an odd spare cell widens the middle pier");
        assertEquals(List.of(List.of(1, 5), List.of(7, 11), List.of(13, 17)),
                asLists(openings(19)));
        assertEquals(List.of(List.of(1, 5), List.of(7, 12), List.of(14, 18)),
                asLists(openings(20)), "an odd spare cell widens the middle opening");
    }

    @Test
    void aSpanShorterThanOneBayHasNoOpenings() {
        assertTrue(openings(6).isEmpty());
        assertTrue(openings(1).isEmpty());
    }

    /** Every span from one bay to a big hall: as many bays as fit, symmetric, piers at both ends. */
    @Test
    void everySpanIsSymmetricWithPiersAtBothEnds() {
        for (int span = 7; span <= 40; span++) {
            List<int[]> openings = openings(span);
            assertEquals((span - 1) / 6, openings.size(), "span " + span);
            assertTrue(openings.get(0)[0] >= 1, "span " + span + ": a pier at the low end");
            assertTrue(openings.get(openings.size() - 1)[1] <= span - 2,
                    "span " + span + ": a pier at the high end");
            for (int i = 0; i < openings.size(); i++) {
                int[] low = openings.get(i);
                int[] high = openings.get(openings.size() - 1 - i);
                assertEquals(low[0], span - 1 - high[1], "span " + span + ": mirrored");
                assertTrue(low[1] - low[0] + 1 >= 5, "span " + span + ": never narrower");
                if (i > 0) {
                    assertTrue(low[0] - openings.get(i - 1)[1] - 1 >= 1, "span " + span);
                }
            }
        }
    }

    // ---- the elevation, and the ceiling ------------------------------------------------------

    /**
     * An X arcade across a 9-deep room (interior 7, run at interior z 3, floor-local z 14) through
     * a 15-wide one (span 13: two openings, interior x 1..5 and 7..11).
     */
    private static void assertArcade(int height) {
        RoomData room = room(15, 9, height);
        Map<String, BlockPlacement> world = settle(build(room, arcade(Axis.X)));
        int z = 14;
        int top = FLOOR_Y + height - 2;
        Set<Integer> openingX = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            openingX.add(11 + i);
            openingX.add(11 + 6 + i);
        }
        for (int x = 11; x <= 23; x++) {
            // Rows 0..2 of an opening are the way through.
            for (int row = 0; row < 3; row++) {
                String id = idAt(world, x, FLOOR_Y + 1 + row, z);
                if (openingX.contains(x)) {
                    assertNull(id, "h" + height + " x" + x + " row " + row + " should be open");
                } else {
                    assertEquals(row == 0 ? BASE : WALL, id, "h" + height + " pier x" + x);
                }
            }
            // From the keystone course up, every cell of the run is built, to the ceiling.
            for (int y = FLOOR_Y + 1 + 5; y <= top; y++) {
                assertNotNull(idAt(world, x, y, z), "h" + height + " x" + x + " y" + y);
            }
            assertNull(idAt(world, x, top + 1, z), "nothing written into a flat ceiling plane");
        }
        // The corbels: one course in per row, facing back to the pier they spring from.
        BlockPlacement first = at(world, 12, FLOOR_Y + 4, z);
        assertEquals(CORBEL, first.getBlockId());
        assertEquals("west", first.getProperties().get("facing"));
        assertEquals("top", first.getProperties().get("half"));
        assertEquals("east", at(world, 16, FLOOR_Y + 4, z).getProperties().get("facing"));
        assertEquals(CORBEL, idAt(world, 13, FLOOR_Y + 5, z));
        assertEquals(CORBEL, idAt(world, 15, FLOOR_Y + 5, z));
        assertNull(idAt(world, 13, FLOOR_Y + 4, z), "the second course oversails open air");
        assertNull(idAt(world, 14, FLOOR_Y + 5, z), "the arch's top is open");
        // The keystone, centred, in the course above the arch; wall either side of it.
        assertEquals(KEYSTONE, idAt(world, 14, FLOOR_Y + 6, z));
        assertEquals(KEYSTONE, idAt(world, 20, FLOOR_Y + 6, z));
        assertEquals(WALL, idAt(world, 13, FLOOR_Y + 6, z));
    }

    @Test
    void theWallReachesTheCeilingAtEveryRoomHeight() {
        for (int height = 8; height <= 12; height++) {
            assertArcade(height);
        }
    }

    @Test
    void aRoomTooShortForTheArchesGetsNoArcade() {
        assertTrue(build(room(15, 9, 7), arcade(Axis.X)).isEmpty(), "five rows, six needed");
    }

    @Test
    void aRoomTooNarrowGetsNoArcade() {
        assertTrue(build(room(8, 9, 9), arcade(Axis.X)).isEmpty(), "a span of six: no bay fits");
        assertTrue(build(room(15, 4, 9), arcade(Axis.X)).isEmpty(), "nothing on one side");
    }

    /** Where a vault opened the column above the plane, the wall goes on up into it. */
    @Test
    void theWallRisesIntoAVaultAboveThePlane() {
        int height = 9;
        int plane = FLOOR_Y + height - 1;
        List<BlockPlacement> out = new ArrayList<>();
        for (int x = 11; x <= 23; x++) {
            out.add(new BlockPlacement(x, plane, 14, AIR));
            out.add(new BlockPlacement(x, plane + 1, 14, AIR));
            out.add(new BlockPlacement(x, plane + 2, 14, WALL));
        }
        Map<String, BlockPlacement> world = settle(build(room(15, 9, height), arcade(Axis.X), out, 1L));
        for (int x = 11; x <= 23; x++) {
            assertEquals(WALL, idAt(world, x, plane, 14), "x" + x);
            assertEquals(WALL, idAt(world, x, plane + 1, 14), "x" + x + ": up to the vault's roof");
        }
    }

    /** A joist hanging under the plane is built through, so the wall meets the ceiling itself. */
    @Test
    void theWallIsBuiltThroughAProjectionUnderThePlane() {
        int height = 9;
        int top = FLOOR_Y + height - 2;
        List<BlockPlacement> out = new ArrayList<>();
        out.add(new BlockPlacement(14, top, 14, "minecraft:stripped_spruce_log"));
        Map<String, BlockPlacement> world = settle(build(room(15, 9, height), arcade(Axis.X), out, 1L));
        assertEquals(WALL, idAt(world, 14, top, 14));
    }

    // ---- doors, and chunks ---------------------------------------------------------------------

    private static Set<Coords2D> runCells(List<BlockPlacement> out) {
        return out.stream().filter(p -> p.getY() > FLOOR_Y + 5)
                .map(p -> new Coords2D(p.getX(), p.getZ())).collect(Collectors.toSet());
    }

    /** A door in the middle of each end wall: the run steps aside rather than walling them up. */
    @Test
    void theRunMovesAsideForADoorInAnEndWall() {
        RoomData room = room(15, 13, 9);
        // Middle of the interior's z is 11 + 5 = 16; doors in the west and east walls there.
        room.setDoorways(new ArrayList<>(List.of(new Coords2D(10, 16), new Coords2D(24, 16))));
        Set<Coords2D> cells = runCells(build(room, arcade(Axis.X)));
        assertFalse(cells.isEmpty(), "moved, not dropped");
        assertTrue(cells.stream().allMatch(c -> c.getY() == 15), () -> "one row over: " + cells);
        for (Coords2D approach : RoomInterior.cellsInsideDoorways(room)) {
            assertFalse(cells.contains(approach), "on a doorway approach: " + approach);
        }
    }

    /** Every line blocked on its axis: an X arcade has nowhere to go, an ANY one turns. */
    @Test
    void aRunWithNowhereToGoOnItsAxisTriesTheOther() {
        RoomData room = room(15, 5, 9);
        // Interior depth 3: the only line is interior z 1, floor-local 12.
        room.setDoorways(new ArrayList<>(List.of(new Coords2D(10, 12))));
        assertTrue(build(room, arcade(Axis.X)).isEmpty());
        // Along Z the span is 3: no bay. A deeper room gives the turned run somewhere to stand.
        RoomData square = room(15, 15, 9);
        List<Coords2D> doors = new ArrayList<>();
        for (int z = 12; z <= 22; z++) {
            doors.add(new Coords2D(10, z));
        }
        square.setDoorways(doors);
        for (long seed = 0; seed < 8; seed++) {
            Set<Coords2D> cells = runCells(build(square, arcade(Axis.ANY), new ArrayList<>(), seed));
            assertFalse(cells.isEmpty(), "seed " + seed);
            assertEquals(1, cells.stream().map(Coords2D::getX).distinct().count(),
                    "seed " + seed + ": the run turned to lie along Z");
        }
    }

    /** One room, rendered once per chunk it overlaps: the same seed is the same arcade. */
    @Test
    void theArcadeIsAPureFunctionOfItsSeed() {
        RoomData room = room(17, 17, 10);
        Set<Integer> axes = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            List<BlockPlacement> a = build(room, arcade(Axis.ANY), new ArrayList<>(), seed);
            List<BlockPlacement> b = build(room, arcade(Axis.ANY), new ArrayList<>(), seed);
            assertEquals(a.toString(), b.toString(), "seed " + seed);
            axes.add(runCells(a).stream().map(Coords2D::getX).distinct().count() == 1 ? 1 : 0);
        }
        assertEquals(2, axes.size(), "ANY should roll both axes over 40 mixed seeds");
    }

    /** A definite axis draws nothing: a single-option slot draws no random value. */
    @Test
    void aDefiniteAxisDrawsNothing() {
        RandomSource random = RandomSource.create(mix(7L));
        RandomSource twin = RandomSource.create(mix(7L));
        RoomPartitionGenerator.build(room(15, 9, 9), FLOOR_Y, arcade(Axis.X), Set.of(), random,
                new ArrayList<>());
        assertEquals(twin.nextLong(), random.nextLong());
    }

    // ---- the schema --------------------------------------------------------------------------

    private static DataResult<PartitionPatternEntry> parse(String json) {
        return PartitionPatternEntry.CODEC.parse(JsonOps.INSTANCE,
                GSON.fromJson(json, JsonElement.class));
    }

    private static String error(String json) {
        DataResult<PartitionPatternEntry> result = parse(json);
        return result.error().orElseThrow(() -> new AssertionError(
                "expected this to be rejected: " + json)).message();
    }

    @Test
    void theArcadeIsRegisteredAndDecodesWithItsDefaults() {
        assertTrue(PartitionShapeRegistry.ids().contains(new ResourceLocation("dungeons2", "arcade")));
        PartitionPatternEntry entry = parse("{\"type\": \"dungeons2:arcade\", \"block\": \"" + WALL
                + "\", \"config\": {\"keystone_block\": \"$ornament\"}}").result().orElseThrow();
        ArcadePartitionShape shape = (ArcadePartitionShape) entry.shape();
        assertEquals(new ArcadePartitionShape(Axis.ANY, 5, 1, 3, 2, Optional.empty(),
                Optional.empty(), Optional.of("$ornament")), shape);
        assertEquals(KEYSTONE, ((ArcadePartitionShape) entry.withRoles(role -> KEYSTONE).shape())
                .keystoneBlock().orElseThrow());
    }

    @Test
    void anOpeningTooNarrowForItsCorbelsIsALoadError() {
        String message = error("{\"type\": \"dungeons2:arcade\", \"block\": \"" + WALL
                + "\", \"config\": {\"opening_width\": 4, \"corbel_steps\": 2}}");
        assertTrue(message.contains("opening_width"), () -> message);
    }

    @Test
    void aGapBlockOrAHeightIsALoadError() {
        assertTrue(error("{\"type\": \"dungeons2:arcade\", \"block\": \"" + WALL
                + "\", \"gap_block\": \"minecraft:oak_door\"}").contains("gap_block"));
        assertTrue(error("{\"type\": \"dungeons2:arcade\", \"block\": \"" + WALL
                + "\", \"height\": 4}").contains("height"));
    }

    @Test
    void aStrayKeyInTheArcadeConfigIsALoadError() {
        String message = error("{\"type\": \"dungeons2:arcade\", \"block\": \"" + WALL
                + "\", \"config\": {\"keystone\": \"" + KEYSTONE + "\"}}");
        assertTrue(message.contains("keystone"), () -> message);
    }
}
