package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.floor.DiagonalFloorPattern;
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
 * The floor `diagonal` type (#82): alternating bands at 45 degrees.
 *
 * <p>Two things here are decisions rather than arithmetic. The bands are computed in
 * <strong>floor-local</strong> coordinates, so each room starts its own banding at its own corner
 * instead of every room sharing one continuous field through the walls between them. And the band
 * index uses {@link Math#floorDiv}/{@link Math#floorMod} rather than {@code /} and {@code %}, which
 * only shows on the {@code flipped} diagonal, where {@code x - z} goes negative: truncation toward
 * zero would double the width of the band straddling zero.</p>
 */
class DiagonalFloorPatternProviderTest {

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

    /** At width 1 the bands are one cell thick, which is exactly the checkerboard's own parity. */
    @Test
    void widthOneIsTheCheckerboardsParity() {
        boolean[][] grid = DiagonalFloorPatternProvider.plan(9, 9, 1, false);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                assertEquals((x + z) % 2 == 0, grid[x][z]);
            }
        }
    }

    /** A wider band is thicker across the diagonal, and the two colours still split the floor. */
    @Test
    void widthIsMeasuredAcrossTheDiagonal() {
        boolean[][] grid = DiagonalFloorPatternProvider.plan(8, 8, 2, false);
        assertTrue(grid[0][0] && grid[1][0], "x + z of 0 and 1 are one band");
        assertFalse(grid[2][0] || grid[3][0], "2 and 3 are the next");
        assertTrue(grid[4][0]);
        assertEquals(32, marked(grid), "an even floor at an even width splits exactly in half");
    }

    /** Every cell on one anti-diagonal is the same colour, which is what makes it a band. */
    @Test
    void oneDiagonalIsAllOneColour() {
        boolean[][] grid = DiagonalFloorPatternProvider.plan(9, 9, 3, false);
        for (int x = 0; x < 9; x++) {
            int z = 5 - x;
            if (z >= 0 && z < 9) {
                assertEquals(grid[5][0], grid[x][z], "x + z == 5 must be one band throughout");
            }
        }
    }

    /**
     * {@code flipped} runs the other diagonal, where {@code x - z} is negative on one side of the
     * room. The band that straddles zero has to be the SAME width as every other band; truncating
     * division would make it twice as wide and leave a visible seam down the room's own diagonal.
     */
    @Test
    void theFlippedDiagonalKeepsItsBandWidthAcrossZero() {
        boolean[][] grid = DiagonalFloorPatternProvider.plan(9, 9, 2, true);
        // Along column x = 0, x - z runs 0, -1, -2, -3, ... so the bands are {0}, {-1,-2}, {-3,-4}.
        assertTrue(grid[0][0]);
        assertFalse(grid[0][1], "the first negative band starts immediately");
        assertFalse(grid[0][2], "and is two cells wide, not one and not four");
        assertTrue(grid[0][3]);
        assertTrue(grid[0][4]);
        assertFalse(grid[0][5]);
    }

    /** And it really is the other diagonal, not the same one relabelled. */
    @Test
    void flippedIsNotTheSameGrid() {
        boolean[][] straight = DiagonalFloorPatternProvider.plan(9, 9, 2, false);
        boolean[][] flipped = DiagonalFloorPatternProvider.plan(9, 9, 2, true);
        boolean anyDifference = false;
        for (int x = 0; x < 9 && !anyDifference; x++) {
            for (int z = 0; z < 9 && !anyDifference; z++) {
                anyDifference = straight[x][z] != flipped[x][z];
            }
        }
        assertTrue(anyDifference);
    }

    /** A band width the codec forbids is clamped rather than dividing by zero. */
    @Test
    void aNonPositiveWidthIsClampedToOne() {
        assertEquals(marked(DiagonalFloorPatternProvider.plan(9, 9, 1, false)),
                marked(DiagonalFloorPatternProvider.plan(9, 9, 0, false)));
    }

    // ---- emitting -------------------------------------------------------------------------------

    private static RoomData room() {
        return new RoomData(1, 10, 10, 9, 9, 7, RoomRole.NORMAL);
    }

    /**
     * It is a FILL: every cell gets one of the two blocks. That is also why it is deliberately not
     * an {@link IFloorOverlayGenerator} &mdash; an overlay that wrote every cell would erase
     * whatever it was layered over, which is the one thing that contract forbids.
     */
    @Test
    void buildFillsEveryCellAndItIsNotAnOverlay() {
        DiagonalFloorPatternProvider provider = new DiagonalFloorPatternProvider(
                Blocks.GOLD_BLOCK, Blocks.STONE_BRICKS, 2, false);
        assertFalse(provider instanceof IFloorOverlayGenerator,
                "a two-block fill must not offer itself as an overlay");

        List<BlockPlacement> out = new ArrayList<>();
        provider.build(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        assertEquals(81, out.size());
        long gold = out.stream().filter(p -> "minecraft:gold_block".equals(p.getBlockId())).count();
        assertTrue(gold > 0 && gold < 81, "both blocks are drawn");
        assertEquals(81, gold + out.stream()
                .filter(p -> "minecraft:stone_bricks".equals(p.getBlockId())).count(),
                "and nothing else is");
    }

    @Test
    void everyPlacementSitsOnTheFloorPlaneAndInsideTheRoom() {
        List<BlockPlacement> out = new ArrayList<>();
        new DiagonalFloorPatternProvider(Blocks.GOLD_BLOCK, Blocks.STONE_BRICKS, 3, true)
                .build(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        Set<String> cells = new HashSet<>();
        for (BlockPlacement p : out) {
            assertEquals(FLOOR_Y, p.getY(), "a floor pattern writes one plane: " + p);
            assertTrue(p.getX() >= 10 && p.getX() <= 18, "outside the room: " + p);
            assertTrue(p.getZ() >= 10 && p.getZ() <= 18, "outside the room: " + p);
            assertTrue(cells.add(p.getX() + "," + p.getZ()), "cell written twice: " + p);
        }
    }

    /**
     * The anchoring decision the backlog entry asked for: local coordinates, so the banding belongs
     * to the room. Two rooms at different world positions draw identical floors rather than two
     * windows onto one dungeon-wide striped field.
     */
    @Test
    void theBandingIsAnchoredToTheRoomRatherThanToTheWorld() {
        DiagonalFloorPatternProvider provider = new DiagonalFloorPatternProvider(
                Blocks.GOLD_BLOCK, Blocks.STONE_BRICKS, 2, false);

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
                          "type": "dungeons2:diagonal",
                          "config": { "primary_block": "minecraft:stone_bricks",
                                      "secondary_block": "minecraft:polished_andesite",
                                      "width": 3, "flipped": true }
                        }"""));
        FloorPattern pattern = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        DiagonalFloorPattern diagonal = (DiagonalFloorPattern) pattern;
        assertEquals("minecraft:stone_bricks", diagonal.primaryBlock());
        assertEquals(3, diagonal.width());
        assertTrue(diagonal.flipped());
        assertNotNull(pattern.generator(FloorConfig.DEFAULT));
    }

    @Test
    void widthAndFlippedAreOptional() {
        DataResult<FloorPattern> result = FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"type": "dungeons2:diagonal",
                         "config": {"primary_block": "minecraft:stone_bricks",
                                    "secondary_block": "minecraft:polished_andesite"}}"""));
        DiagonalFloorPattern diagonal = (DiagonalFloorPattern) result.result().orElseThrow();
        assertEquals(2, diagonal.width());
        assertFalse(diagonal.flipped());
    }

    /** Both blocks are required: a two-material pattern with one material is a plain floor. */
    @Test
    void bothBlocksAreRequired() {
        assertTrue(FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "dungeons2:diagonal",
                 "config": {"primary_block": "minecraft:stone_bricks"}}""")).result().isEmpty());
    }

    @Test
    void aWidthBelowOneIsALoadError() {
        assertTrue(FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "dungeons2:diagonal",
                 "config": {"primary_block": "minecraft:stone_bricks",
                            "secondary_block": "minecraft:andesite", "width": 0}}"""))
                .result().isEmpty());
    }

    @Test
    void aStrayKeyIsALoadError() {
        assertTrue(FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "dungeons2:diagonal",
                 "config": {"primary_block": "minecraft:stone_bricks",
                            "secondary_block": "minecraft:andesite", "slope": 2}}"""))
                .result().isEmpty());
    }

    /** An unresolvable block degrades the whole pattern to plain, as every floor type does. */
    @Test
    void anUnresolvableBlockDegradesToPlain() {
        assertNotNull(new DiagonalFloorPattern("dungeons2:no_such_block", "minecraft:stone")
                .generator(FloorConfig.DEFAULT));
    }

    @Test
    void aDiagonalOfLiteralsIsNotEvenCopied() {
        DiagonalFloorPattern pattern =
                new DiagonalFloorPattern("minecraft:stone", "minecraft:andesite");
        assertSame(pattern, pattern.withRoles(role -> "minecraft:dirt"));
    }

    @Test
    void bothBlocksReadAMaterialRole() {
        DiagonalFloorPattern resolved = (DiagonalFloorPattern)
                new DiagonalFloorPattern("$inlay", "$inlay_alt", 3, true)
                        .withRoles(role -> "inlay".equals(role)
                                ? "minecraft:polished_andesite" : "minecraft:stone_bricks");
        assertEquals("minecraft:polished_andesite", resolved.primaryBlock());
        assertEquals("minecraft:stone_bricks", resolved.secondaryBlock());
        assertEquals(3, resolved.width(), "and keeps what it did not resolve");
        assertTrue(resolved.flipped());
    }
}
