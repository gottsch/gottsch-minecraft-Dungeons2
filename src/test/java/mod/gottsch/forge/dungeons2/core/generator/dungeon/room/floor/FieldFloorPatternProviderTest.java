package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.floor.FieldFloorPattern;
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
 * The floor `field` type (#75): a filled rectangle at or inside an inset.
 *
 * <p>The thing actually worth pinning down is the relationship to {@code border}, because it is the
 * one an author will get wrong: both measure {@code inset} from the room edge, so a field and a
 * border at the SAME inset overlap on the ring, and filling a border's panel means authoring the
 * field one further in.</p>
 */
class FieldFloorPatternProviderTest {

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

    @Test
    void insetZeroFillsTheWholeFloor() {
        boolean[][] grid = FieldFloorPatternProvider.plan(9, 9, 0);
        assertEquals(81, marked(grid));
    }

    @Test
    void anInsetShrinksTheRectangleOnEveryEdge() {
        // 9x9 at inset 2 -> x and z both 2..6, a 5x5.
        boolean[][] grid = FieldFloorPatternProvider.plan(9, 9, 2);
        assertEquals(25, marked(grid));
        assertTrue(grid[2][2], "the inset row IS part of the field");
        assertTrue(grid[6][6]);
        assertFalse(grid[1][4]);
        assertFalse(grid[7][4]);
    }

    @Test
    void itFollowsAnUnequalFootprint() {
        // 13 wide, 7 deep, inset 1 -> 11 x 5.
        assertEquals(55, marked(FieldFloorPatternProvider.plan(13, 7, 1)));
    }

    /**
     * An inset that meets or crosses the middle marks nothing, rather than drawing a one-cell dot
     * or throwing. It matters here more than on the ceiling: a floor pattern is rolled for whatever
     * room the planner produced, so an inset tuned for a hall has to simply do nothing in a chamber.
     */
    @Test
    void anInsetWithNoFieldLeftMarksNothing() {
        assertEquals(0, marked(FieldFloorPatternProvider.plan(7, 7, 4)));
        assertEquals(0, marked(FieldFloorPatternProvider.plan(7, 7, 20)));
        assertEquals(0, marked(FieldFloorPatternProvider.plan(1, 9, 1)));
    }

    /**
     * The relationship an author has to get right: a field at inset {@code n} covers the border's
     * ring at inset {@code n}, so filling a border's panel means inset {@code n + 1}.
     */
    @Test
    void aFieldOneFurtherInIsExactlyWhatABorderEncloses() {
        boolean[][] sameInset = FieldFloorPatternProvider.plan(11, 11, 2);
        boolean[][] oneFurther = FieldFloorPatternProvider.plan(11, 11, 3);
        // The border at inset 2 is the perimeter of the 7x7 rectangle at 2..8: 24 cells.
        assertEquals(49, marked(sameInset));
        assertEquals(25, marked(oneFurther));
        assertEquals(24, marked(sameInset) - marked(oneFurther),
                "the difference between the two is exactly the ring the border draws");
        assertTrue(sameInset[2][5], "the same inset covers the ring");
        assertFalse(oneFurther[2][5], "one further in leaves it to the border");
    }

    // ---- emitting -------------------------------------------------------------------------------

    private static RoomData room() {
        return new RoomData(1, 10, 10, 9, 9, 7, RoomRole.NORMAL);
    }

    @Test
    void buildFillsEveryCellAndOverlayFillsOnlyTheField() {
        FieldFloorPatternProvider provider = new FieldFloorPatternProvider(2, Blocks.GOLD_BLOCK,
                Blocks.STONE_BRICKS.defaultBlockState());

        List<BlockPlacement> built = new ArrayList<>();
        provider.build(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), built);
        assertEquals(81, built.size(), "build fills the whole floor");
        assertEquals(25, built.stream()
                .filter(p -> "minecraft:gold_block".equals(p.getBlockId())).count());

        List<BlockPlacement> layered = new ArrayList<>();
        provider.overlay(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), layered);
        assertEquals(25, layered.size(), "overlay emits the field and nothing else");
        assertTrue(layered.stream()
                .allMatch(p -> "minecraft:gold_block".equals(p.getBlockId())));
    }

    @Test
    void everyPlacementSitsOnTheFloorPlaneAndInsideTheRoom() {
        List<BlockPlacement> out = new ArrayList<>();
        new FieldFloorPatternProvider(1, Blocks.GOLD_BLOCK)
                .overlay(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        Set<String> cells = new HashSet<>();
        for (BlockPlacement p : out) {
            assertEquals(FLOOR_Y, p.getY(), "a floor pattern writes one plane: " + p);
            assertTrue(p.getX() >= 10 && p.getX() <= 18, "outside the room: " + p);
            assertTrue(p.getZ() >= 10 && p.getZ() <= 18, "outside the room: " + p);
            assertTrue(cells.add(p.getX() + "," + p.getZ()), "cell written twice: " + p);
        }
        assertEquals(49, out.size(), "9x9 at inset 1 is 7x7");
    }

    /** An empty field emits nothing at all through the overlay path, not a plane of base. */
    @Test
    void anEmptyFieldOverlaysNothing() {
        List<BlockPlacement> out = new ArrayList<>();
        new FieldFloorPatternProvider(9, Blocks.GOLD_BLOCK)
                .overlay(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        assertTrue(out.isEmpty());
    }

    // ---- the schema -----------------------------------------------------------------------------

    @Test
    void theTypeIsRegisteredAndDecodesFromItsAuthoredForm() {
        DataResult<FloorPattern> result = FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "dungeons2:field",
                          "config": { "block": "minecraft:polished_andesite", "inset": 3 }
                        }"""));
        FloorPattern pattern = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        assertTrue(pattern instanceof FieldFloorPattern);
        assertEquals(3, ((FieldFloorPattern) pattern).inset());
        assertNotNull(pattern.generator(FloorConfig.DEFAULT));
    }

    /**
     * {@code block} is required. A field with no material is not a floor treatment at all, and the
     * closed schema turns the omission into a load error rather than a pattern that draws nothing
     * where the author expected a panel.
     */
    @Test
    void theBlockIsRequired() {
        assertTrue(FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "dungeons2:field", "config": {"inset": 2}}""")).result().isEmpty());
    }

    @Test
    void aStrayKeyIsALoadError() {
        assertTrue(FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "dungeons2:field",
                 "config": {"block": "minecraft:stone", "insett": 2}}""")).result().isEmpty());
    }

    /** An unresolvable block degrades the whole pattern to plain, as every floor type does. */
    @Test
    void anUnresolvableBlockDegradesToPlain() {
        assertNotNull(new FieldFloorPattern("dungeons2:no_such_block", 2)
                .generator(FloorConfig.DEFAULT));
    }

    @Test
    void aFieldOfLiteralsIsNotEvenCopied() {
        FieldFloorPattern pattern = new FieldFloorPattern("minecraft:stone", 2);
        assertSame(pattern, pattern.withRoles(role -> "minecraft:dirt"));
    }

    @Test
    void theBlockReadsAMaterialRole() {
        FieldFloorPattern resolved = (FieldFloorPattern)
                new FieldFloorPattern("$panel", 3).withRoles(role -> "minecraft:polished_andesite");
        assertEquals("minecraft:polished_andesite", resolved.block());
        assertEquals(3, resolved.inset(), "and keeps what it did not resolve");
    }
}
