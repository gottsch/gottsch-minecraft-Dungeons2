package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorBorderPatternProvider.RingCell;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies {@link FloorBorderPatternProvider#plan} against the exact palette dumped from the
 * hand-authored reference {@code floor_border_pattern_1.nbt} (9x9 floor, ring inset 2), read
 * back with nbtlib rather than assumed. {@code plan} itself touches no Forge registry, but the
 * class's {@code PLAIN} field (a vanilla {@code Blocks.STONE_BRICKS} state) still triggers
 * vanilla static init on class-load, hence the bootstrap -- see the class javadoc for why
 * {@code dungeonblocks:*} blocks specifically can't be touched in this test environment.
 */
class FloorBorderPatternProviderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void matchesTheReferenceNbtsRing() {
        RingCell[][] grid = FloorBorderPatternProvider.plan(9, 9, 2);

        // The 16 ring cells, read off the reference NBT's palette (local x,z -> left?, facing).
        assertRing(grid, 2, 2, false, Direction.NORTH); // NW corner
        assertRing(grid, 3, 2, true, Direction.NORTH);
        assertRing(grid, 4, 2, false, Direction.NORTH);
        assertRing(grid, 5, 2, true, Direction.NORTH);
        assertRing(grid, 6, 2, false, Direction.EAST); // NE corner

        assertRing(grid, 6, 3, true, Direction.EAST);
        assertRing(grid, 6, 4, false, Direction.EAST);
        assertRing(grid, 6, 5, true, Direction.EAST);
        assertRing(grid, 6, 6, false, Direction.SOUTH); // SE corner

        assertRing(grid, 5, 6, true, Direction.SOUTH);
        assertRing(grid, 4, 6, false, Direction.SOUTH);
        assertRing(grid, 3, 6, true, Direction.SOUTH);
        assertRing(grid, 2, 6, false, Direction.WEST); // SW corner

        assertRing(grid, 2, 5, true, Direction.WEST);
        assertRing(grid, 2, 4, false, Direction.WEST);
        assertRing(grid, 2, 3, true, Direction.WEST);

        // Everything else -- outside the ring and inside it -- is plain.
        int ringCount = 0;
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                if (grid[x][z] != null) {
                    ringCount++;
                }
            }
        }
        assertEquals(16, ringCount);
        assertNull(grid[4][4], "center should be plain");
        assertNull(grid[0][0], "outer margin should be plain");
        assertNull(grid[1][1], "outer margin should be plain");
    }

    @Test
    void tooSmallForTheInsetDegradesToAllPlain() {
        RingCell[][] grid = FloorBorderPatternProvider.plan(4, 9, 2);
        for (RingCell[] column : grid) {
            for (RingCell cell : column) {
                assertNull(cell);
            }
        }
    }

    @Test
    void nonSquareFloorRingStaysWithinBounds() {
        // 13 wide x 9 deep, inset 2 -> ring is 9x5 (x: 2..10, z: 2..6).
        RingCell[][] grid = FloorBorderPatternProvider.plan(13, 9, 2);
        assertRing(grid, 2, 2, false, Direction.NORTH);
        assertRing(grid, 10, 2, false, Direction.EAST);
        assertRing(grid, 10, 6, false, Direction.SOUTH);
        assertRing(grid, 2, 6, false, Direction.WEST);
        // Longer north/south run (7 cells between corners) still alternates starting with LEFT.
        assertRing(grid, 3, 2, true, Direction.NORTH);
        assertRing(grid, 9, 2, true, Direction.NORTH);
    }

    private static void assertRing(RingCell[][] grid, int x, int z, boolean left, Direction facing) {
        RingCell cell = grid[x][z];
        assertEquals(new RingCell(left, facing), cell, "cell at (" + x + "," + z + ")");
    }
}
