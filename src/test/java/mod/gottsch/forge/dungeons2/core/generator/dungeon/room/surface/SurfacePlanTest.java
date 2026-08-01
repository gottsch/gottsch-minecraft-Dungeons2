package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The sparse plan grid. Vanilla blocks only, so a bare bootstrap resolves everything.
 */
class SurfacePlanTest {

    private static BlockState brick;
    private static BlockState andesite;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        brick = Blocks.STONE_BRICKS.defaultBlockState();
        andesite = Blocks.POLISHED_ANDESITE.defaultBlockState();
    }

    @Test
    void aNewPlanIsEntirelyNull() {
        SurfacePlan plan = SurfacePlan.of(5, 4);
        assertEquals(0, plan.markedCells(), "a fresh plan marks nothing -- every cell is base");
        assertNull(plan.get(2, 2));
    }

    @Test
    void setAndGetRoundTrip() {
        SurfacePlan plan = SurfacePlan.of(5, 4);
        plan.set(3, 1, brick);
        assertSame(brick, plan.get(3, 1));
        assertEquals(1, plan.markedCells());
    }

    /**
     * Out-of-range writes are swallowed on purpose: a course anchored to the top of a wall is
     * written against a height the room may not have, and clamping here is what keeps those
     * providers arithmetic instead of every one repeating the same bounds check.
     */
    @Test
    void outOfRangeWritesAreIgnoredRatherThanThrowing() {
        SurfacePlan plan = SurfacePlan.of(3, 3);
        plan.set(-1, 0, brick);
        plan.set(0, -1, brick);
        plan.set(3, 0, brick);
        plan.set(0, 3, brick);
        assertEquals(0, plan.markedCells());
    }

    @Test
    void outOfRangeReadsReturnNull() {
        SurfacePlan plan = SurfacePlan.of(3, 3);
        assertNull(plan.get(-1, 0));
        assertNull(plan.get(3, 3));
    }

    /** A degenerate extent clamps rather than blowing up -- rooms can be too thin to have a run. */
    @Test
    void aNegativeExtentClampsToEmpty() {
        SurfacePlan plan = SurfacePlan.of(-4, 3);
        assertEquals(0, plan.uSize());
        assertEquals(3, plan.vSize());
        assertEquals(0, plan.markedCells());
    }

    /** Later non-null wins; null cells in the overlay leave what was underneath alone. */
    @Test
    void overlayKeepsUnderlyingCellsWhereTheOverlayIsNull() {
        SurfacePlan base = SurfacePlan.of(4, 4);
        base.set(0, 0, brick);
        base.set(1, 1, brick);

        SurfacePlan over = SurfacePlan.of(4, 4);
        over.set(1, 1, andesite);
        over.set(2, 2, andesite);

        base.overlay(over);

        assertSame(brick, base.get(0, 0), "untouched by the overlay");
        assertSame(andesite, base.get(1, 1), "later non-null wins");
        assertSame(andesite, base.get(2, 2), "overlay adds its own cells");
        assertEquals(3, base.markedCells());
    }

    /** A mismatched overlay is clipped, not rejected -- the shorter of the two extents wins. */
    @Test
    void overlayOfADifferentExtentIsClipped() {
        SurfacePlan base = SurfacePlan.of(2, 2);
        SurfacePlan over = SurfacePlan.of(6, 6);
        over.set(0, 0, andesite);
        over.set(5, 5, andesite);

        base.overlay(over);

        assertNotNull(base.get(0, 0));
        assertEquals(1, base.markedCells(), "the out-of-extent cell is dropped, not wrapped");
    }
}
