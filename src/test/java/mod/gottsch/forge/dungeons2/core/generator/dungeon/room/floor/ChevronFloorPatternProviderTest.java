package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.floor.ChevronFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPatternRegistry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor `chevron` type (#82): repeating V's up the depth axis.
 *
 * <p>Two things are worth pinning down, because both are decisions rather than arithmetic: an arm
 * that walks off the edge of the floor is <strong>dropped, not clipped to the wall</strong> (a
 * clipped arm is a vertical line nobody authored), and the plan is <strong>anchored to the room</strong>
 * &mdash; the first V sits at the room's own near edge and its own centre column, whatever the room's
 * world position.</p>
 */
class ChevronFloorPatternProviderTest {

    private static final int FLOOR_Y = 60;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static int marked(boolean[][] grid) {
        int count = 0;
        for (boolean[] column : grid) {
            for (boolean cell : column) {
                if (cell) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---- geometry -------------------------------------------------------------------------------

    /**
     * A 9-wide floor has its centre at column 4; each row's arms sit that far out plus the row's
     * distance from the last apex, and the apex row marks one cell because both arms land on it.
     */
    @Test
    void theArmsWalkOutwardOneCellPerRowFromTheCentre() {
        boolean[][] grid = ChevronFloorPatternProvider.plan(9, 4, 4, 1, false);
        assertTrue(grid[4][0], "the apex is the centre column");
        assertEquals(1, columnCount(grid, 0), "and is a single cell, not two");
        assertTrue(grid[3][1] && grid[5][1]);
        assertTrue(grid[2][2] && grid[6][2]);
        assertTrue(grid[1][3] && grid[7][3]);
        assertFalse(grid[4][1], "the outline is two arms, not a filled triangle");
        assertEquals(7, marked(grid));
    }

    /** And it restarts every {@code spacing} rows, which is what makes it a run rather than one V. */
    @Test
    void theVRestartsEverySpacingRows() {
        boolean[][] grid = ChevronFloorPatternProvider.plan(9, 8, 4, 1, false);
        assertTrue(grid[4][4], "row 4 is the next apex");
        assertEquals(1, columnCount(grid, 4));
        assertTrue(grid[1][7], "and the arms are as far out again by row 7");
        assertEquals(14, marked(grid));
    }

    /** A steeper slope reaches the same width in fewer rows. */
    @Test
    void slopeIsCellsOutwardPerRow() {
        boolean[][] grid = ChevronFloorPatternProvider.plan(9, 3, 3, 2, false);
        assertTrue(grid[2][1] && grid[6][1], "one row out at slope 2 is two cells out");
        assertTrue(grid[0][2] && grid[8][2]);
    }

    /**
     * The arm is DROPPED where it leaves the floor, not clipped back to the wall &mdash; a clipped
     * arm draws a vertical line at the edge, which is a pattern nobody asked for. So a chevron too
     * steep or too widely spaced for the room loses the outer part of each V and keeps its shape.
     */
    @Test
    void anArmThatWalksOffTheFloorIsDroppedRatherThanClipped() {
        // 5 wide, centre column 2: by row 3 both arms are outside the floor entirely.
        boolean[][] grid = ChevronFloorPatternProvider.plan(5, 6, 6, 1, false);
        assertTrue(grid[0][2] && grid[4][2], "row 2 still fits");
        assertEquals(0, columnCount(grid, 3), "row 3 draws nothing at all");
        assertEquals(0, columnCount(grid, 4));
        assertEquals(5, marked(grid));
    }

    /** Filled marks everything between the arms, which turns the run into solid triangles. */
    @Test
    void filledMarksTheTriangleBetweenTheArms() {
        boolean[][] grid = ChevronFloorPatternProvider.plan(9, 4, 4, 1, true);
        assertEquals(1, columnCount(grid, 0));
        assertEquals(3, columnCount(grid, 1));
        assertEquals(5, columnCount(grid, 2));
        assertEquals(7, columnCount(grid, 3));
        assertEquals(16, marked(grid));
    }

    /**
     * A filled triangle DOES stop at the wall, unlike an outline's arm: it is a solid area, and the
     * part of it that is on the floor is still the shape the author asked for.
     */
    @Test
    void aFilledTriangleWiderThanTheRoomStillFillsTheRow() {
        boolean[][] grid = ChevronFloorPatternProvider.plan(5, 5, 5, 1, true);
        assertEquals(5, columnCount(grid, 4), "row 4 wants 9 cells and the floor has 5");
    }

    /** A slope of 0 is not degenerate: it is the centre line, and it is drawn. */
    @Test
    void slopeZeroDrawsTheCentreLine() {
        boolean[][] grid = ChevronFloorPatternProvider.plan(9, 5, 4, 0, false);
        assertEquals(5, marked(grid));
        for (int z = 0; z < 5; z++) {
            assertTrue(grid[4][z]);
        }
    }

    /** A non-positive spacing marks nothing, the same graceful degradation every pattern has. */
    @Test
    void aNonPositiveSpacingMarksNothing() {
        assertEquals(0, marked(ChevronFloorPatternProvider.plan(9, 9, 0, 1, false)));
        assertEquals(0, marked(ChevronFloorPatternProvider.plan(9, 9, -3, 1, true)));
    }

    private static int columnCount(boolean[][] grid, int z) {
        int count = 0;
        for (boolean[] column : grid) {
            if (column[z]) {
                count++;
            }
        }
        return count;
    }

    // ---- emitting -------------------------------------------------------------------------------

    private static RoomData room() {
        return new RoomData(1, 10, 10, 9, 9, 7, RoomRole.NORMAL);
    }

    @Test
    void buildFillsEveryCellAndOverlayEmitsOnlyTheChevrons() {
        ChevronFloorPatternProvider provider = new ChevronFloorPatternProvider(4, 1, false,
                Blocks.GOLD_BLOCK, Blocks.STONE_BRICKS.defaultBlockState());

        List<BlockPlacement> built = new ArrayList<>();
        provider.build(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), built);
        assertEquals(81, built.size(), "build fills the whole floor");
        assertEquals(15, built.stream()
                .filter(p -> "minecraft:gold_block".equals(p.getBlockId())).count());

        List<BlockPlacement> layered = new ArrayList<>();
        provider.overlay(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), layered);
        assertEquals(15, layered.size(), "overlay emits the chevrons and nothing else");
        assertTrue(layered.stream().allMatch(p -> "minecraft:gold_block".equals(p.getBlockId())));
    }

    @Test
    void everyPlacementSitsOnTheFloorPlaneAndInsideTheRoom() {
        List<BlockPlacement> out = new ArrayList<>();
        new ChevronFloorPatternProvider(4, 1, true, Blocks.GOLD_BLOCK)
                .overlay(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        Set<String> cells = new HashSet<>();
        for (BlockPlacement p : out) {
            assertEquals(FLOOR_Y, p.getY(), "a floor pattern writes one plane: " + p);
            assertTrue(p.getX() >= 10 && p.getX() <= 18, "outside the room: " + p);
            assertTrue(p.getZ() >= 10 && p.getZ() <= 18, "outside the room: " + p);
            assertTrue(cells.add(p.getX() + "," + p.getZ()), "cell written twice: " + p);
        }
    }

    /**
     * The anchoring decision the backlog entry asked for, asserted where it is visible: the pattern
     * is planned in floor-local coordinates, so moving the room moves the whole chevron run with it
     * rather than sliding the room across a fixed field.
     */
    @Test
    void theRunIsAnchoredToTheRoomRatherThanToTheWorld() {
        ChevronFloorPatternProvider provider =
                new ChevronFloorPatternProvider(4, 1, false, Blocks.GOLD_BLOCK);

        List<BlockPlacement> here = new ArrayList<>();
        provider.build(9, 9, 0, 0, FLOOR_Y, here);
        List<BlockPlacement> there = new ArrayList<>();
        provider.build(9, 9, 37, 41, FLOOR_Y, there);

        for (int i = 0; i < here.size(); i++) {
            assertEquals(here.get(i).getBlockId(), there.get(i).getBlockId(),
                    "the same cell of each room draws the same block, wherever the room is");
        }
    }

    // ---- the schema -----------------------------------------------------------------------------

    @Test
    void theTypeIsRegisteredAndDecodesFromItsAuthoredForm() {
        DataResult<FloorPattern> result = FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "dungeons2:chevron",
                          "config": { "block": "minecraft:polished_andesite", "spacing": 5,
                                      "slope": 2, "filled": true }
                        }"""));
        FloorPattern pattern = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        ChevronFloorPattern chevron = (ChevronFloorPattern) pattern;
        assertEquals(5, chevron.spacing());
        assertEquals(2, chevron.slope());
        assertTrue(chevron.filled());
        assertNotNull(pattern.generator(FloorConfig.DEFAULT));
    }

    /** The defaults are an outlined V every four rows; only {@code block} has to be authored. */
    @Test
    void everythingButTheBlockIsOptional() {
        DataResult<FloorPattern> result = FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"type": "dungeons2:chevron",
                         "config": {"block": "minecraft:polished_andesite"}}"""));
        ChevronFloorPattern chevron = (ChevronFloorPattern) result.result().orElseThrow();
        assertEquals(new ChevronFloorPattern("minecraft:polished_andesite"), chevron);
        assertEquals(4, chevron.spacing());
        assertEquals(1, chevron.slope());
        assertFalse(chevron.filled());
    }

    @Test
    void theBlockIsRequired() {
        assertTrue(FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "dungeons2:chevron", "config": {"spacing": 4}}""")).result().isEmpty());
    }

    /** A spacing of 0 has no next V to space against, so the range starts at 1. */
    @Test
    void aSpacingBelowOneIsALoadError() {
        assertTrue(FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "dungeons2:chevron",
                 "config": {"block": "minecraft:stone", "spacing": 0}}""")).result().isEmpty());
    }

    @Test
    void aStrayKeyIsALoadError() {
        assertTrue(FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "dungeons2:chevron",
                 "config": {"block": "minecraft:stone", "inset": 2}}""")).result().isEmpty());
    }

    /** An unresolvable block degrades the whole pattern to plain, as every floor type does. */
    @Test
    void anUnresolvableBlockDegradesToPlain() {
        assertNotNull(new ChevronFloorPattern("dungeons2:no_such_block")
                .generator(FloorConfig.DEFAULT));
    }

    @Test
    void aChevronOfLiteralsIsNotEvenCopied() {
        ChevronFloorPattern pattern = new ChevronFloorPattern("minecraft:stone");
        assertSame(pattern, pattern.withRoles(role -> "minecraft:dirt"));
    }

    @Test
    void theBlockReadsAMaterialRole() {
        ChevronFloorPattern resolved = (ChevronFloorPattern)
                new ChevronFloorPattern("$inlay", 6, 2, true)
                        .withRoles(role -> "minecraft:polished_andesite");
        assertEquals("minecraft:polished_andesite", resolved.block());
        assertEquals(6, resolved.spacing(), "and keeps what it did not resolve");
        assertEquals(2, resolved.slope());
        assertTrue(resolved.filled());
    }
}
