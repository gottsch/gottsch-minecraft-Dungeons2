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

class RadialSpokesFloorPatternProviderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * With 4 spokes the rose is exactly the cardinals, which is the one case whose expected cells
     * can be written down by hand — a useful anchor for the rasteriser.
     */
    @Test
    void fourSpokesAreTheCardinalsThroughTheCentre() {
        boolean[][] grid = RadialSpokesFloorPatternProvider.plan(9, 9, 4);
        for (int i = 0; i < 9; i++) {
            assertTrue(grid[4][i], "vertical arm cell (4," + i + ")");
            assertTrue(grid[i][4], "horizontal arm cell (" + i + ",4)");
        }
        // Nothing diagonal at 4 spokes.
        assertFalse(grid[0][0]);
        assertFalse(grid[8][0]);
        assertFalse(grid[2][6]);
    }

    @Test
    void eightSpokesAddTheDiagonals() {
        boolean[][] grid = RadialSpokesFloorPatternProvider.plan(9, 9, 8);
        // Cardinals still present...
        assertTrue(grid[4][0]);
        assertTrue(grid[0][4]);
        // ...plus the four corners, which the diagonals reach on a square floor.
        assertTrue(grid[0][0]);
        assertTrue(grid[8][8]);
        assertTrue(grid[0][8]);
        assertTrue(grid[8][0]);
    }

    /** The rasteriser steps in half cells specifically so an arm is never broken. */
    @Test
    void everySpokeIsAnUnbrokenRunFromTheCentre() {
        boolean[][] grid = RadialSpokesFloorPatternProvider.plan(15, 15, 8);
        // Walk each cardinal outward from centre (7,7); no gaps allowed.
        for (int i = 7; i < 15; i++) {
            assertTrue(grid[i][7], "east arm broken at x=" + i);
            assertTrue(grid[7][i], "south arm broken at z=" + i);
        }
        for (int i = 7; i >= 0; i--) {
            assertTrue(grid[i][7], "west arm broken at x=" + i);
            assertTrue(grid[7][i], "north arm broken at z=" + i);
        }
    }

    @Test
    void nonPositiveCountDegradesToAllBase() {
        for (boolean[] column : RadialSpokesFloorPatternProvider.plan(9, 9, 0)) {
            for (boolean cell : column) {
                assertFalse(cell);
            }
        }
    }

    @Test
    void nonSquareRoomsStayInBounds() {
        boolean[][] grid = RadialSpokesFloorPatternProvider.plan(13, 7, 8);
        assertEquals(13, grid.length);
        assertEquals(7, grid[0].length);
        // Centre is always marked -- every spoke starts there.
        assertTrue(grid[6][3]);
    }

    @Test
    void buildFillsNonSpokeCellsWithTheSuppliedBase() {
        RadialSpokesFloorPatternProvider provider = new RadialSpokesFloorPatternProvider(
                4, Blocks.CHISELED_STONE_BRICKS, Blocks.GRANITE.defaultBlockState());
        List<BlockPlacement> out = new ArrayList<>();
        provider.build(9, 9, 0, 0, 0, out);

        Map<String, BlockPlacement> byCoord = new HashMap<>();
        for (BlockPlacement p : out) {
            byCoord.put(p.getX() + "," + p.getZ(), p);
        }
        assertEquals(81, out.size());
        assertEquals("minecraft:chiseled_stone_bricks", byCoord.get("4,4").getBlockId());
        // Off the (4-spoke) rose: the base block passed in, not a hardcoded stone_bricks.
        assertEquals("minecraft:granite", byCoord.get("0,0").getBlockId());
    }

    @Test
    void theAccentBlockHasNoJavaSideDefault() {
        assertThrows(NullPointerException.class, () -> new RadialSpokesFloorPatternProvider(8, null));
    }
}
