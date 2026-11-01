package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossFloorPatternProviderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aSingleCellCrossMarksExactlyTheCentreRowAndColumn() {
        // 9x9, thickness 1 -> the x=4 column and the z=4 row.
        boolean[][] grid = CrossFloorPatternProvider.plan(9, 9, 1);
        for (int i = 0; i < 9; i++) {
            assertTrue(grid[4][i], "centre column cell (4," + i + ")");
            assertTrue(grid[i][4], "centre row cell (" + i + ",4)");
        }
        // Everything off both bands is base.
        assertFalse(grid[0][0]);
        assertFalse(grid[3][3]);
        assertFalse(grid[8][8]);

        int marked = 0;
        for (boolean[] column : grid) {
            for (boolean cell : column) {
                if (cell) marked++;
            }
        }
        assertEquals(9 + 9 - 1, marked, "two 9-cell bands sharing the centre cell");
    }

    @Test
    void thicknessWidensBothBands() {
        // 10x10, thickness 2 -> columns/rows 4..5 ((10-2)/2 = 4).
        boolean[][] grid = CrossFloorPatternProvider.plan(10, 10, 2);
        for (int i = 0; i < 10; i++) {
            assertTrue(grid[4][i]);
            assertTrue(grid[5][i]);
            assertTrue(grid[i][4]);
            assertTrue(grid[i][5]);
        }
        assertFalse(grid[3][3]);
        assertFalse(grid[6][6]);
    }

    @Test
    void nonSquareRoomsCentreEachAxisIndependently() {
        // 13 wide x 7 deep, thickness 1 -> column 6, row 3.
        boolean[][] grid = CrossFloorPatternProvider.plan(13, 7, 1);
        assertTrue(grid[6][0]);
        assertTrue(grid[0][3]);
        assertFalse(grid[5][2]);
    }

    @Test
    void nonPositiveThicknessDegradesToAllBase() {
        for (boolean[] column : CrossFloorPatternProvider.plan(9, 9, 0)) {
            for (boolean cell : column) {
                assertFalse(cell);
            }
        }
    }

    @Test
    void buildFillsNonCrossCellsWithTheSuppliedBase() {
        CrossFloorPatternProvider provider = new CrossFloorPatternProvider(
                1, Blocks.CHISELED_STONE_BRICKS, Blocks.GRANITE.defaultBlockState());
        List<BlockPlacement> out = new ArrayList<>();
        provider.build(9, 9, 0, 0, 0, out);

        Map<String, BlockPlacement> byCoord = new HashMap<>();
        for (BlockPlacement p : out) {
            byCoord.put(p.getX() + "," + p.getZ(), p);
        }
        assertEquals(81, out.size(), "build fills every cell");
        assertEquals("minecraft:chiseled_stone_bricks", byCoord.get("4,4").getBlockId());
        assertEquals("minecraft:chiseled_stone_bricks", byCoord.get("4,0").getBlockId());
        // Off the cross: the base block passed in, NOT a hardcoded stone_bricks.
        assertEquals("minecraft:granite", byCoord.get("0,0").getBlockId());
    }

    @Test
    void overlayEmitsOnlyTheCrossCells() {
        CrossFloorPatternProvider provider = new CrossFloorPatternProvider(
                1, Blocks.CHISELED_STONE_BRICKS, Blocks.GRANITE.defaultBlockState());
        List<BlockPlacement> out = new ArrayList<>();
        provider.overlay(new mod.gottsch.forge.dungeons2.core.data.RoomData(
                        1, 0, 0, 9, 9, 4, mod.gottsch.forge.dungeons2.core.data.RoomRole.NORMAL),
                0, mod.gottsch.forge.dungeons2.core.enums.DungeonMotif.CLASSIC,
                net.minecraft.util.RandomSource.create(1), out);

        assertEquals(17, out.size(), "only the 17 cross cells, no base fill");
        for (BlockPlacement p : out) {
            assertEquals("minecraft:chiseled_stone_bricks", p.getBlockId());
        }
    }

    @Test
    void theAccentBlockHasNoJavaSideDefault() {
        assertThrows(NullPointerException.class, () -> new CrossFloorPatternProvider(1, null));
    }
}
