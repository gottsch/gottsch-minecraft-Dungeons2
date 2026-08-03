package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling;

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfacePatternEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CentreSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.GridSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entry &rarr; provider mapping for the ceiling, plus the end-to-end path through
 * {@code BasicCeilingGenerator}.
 */
class CeilingPatternSelectorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CeilingPatternEntry entry(SurfacePatternEntry... patterns) {
        return new CeilingPatternEntry(List.of(patterns));
    }

    @Test
    void anAbsentSlotMeansPlainCeiling() {
        assertNull(CeilingPatternSelector.providerFor(Optional.empty()));
    }

    @Test
    void anEmptyPatternListMeansPlainCeiling() {
        assertNull(CeilingPatternSelector.toProvider(entry()));
    }

    @Test
    void eachTypeMapsToItsProvider() {
        assertInstanceOf(GridSurfacePatternProvider.class, CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry("coffers", "minecraft:polished_andesite"))));
        assertInstanceOf(BorderSurfacePatternProvider.class, CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry("border", "minecraft:polished_andesite"))));
        assertInstanceOf(CentreSurfacePatternProvider.class, CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry("centre", "minecraft:chiseled_stone_bricks"))));
    }

    /** Both spellings, because half the world writes one and half the other. */
    @Test
    void centerAndCentreAreBothAccepted() {
        assertInstanceOf(CentreSurfacePatternProvider.class, CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry("center", "minecraft:chiseled_stone_bricks"))));
    }

    @Test
    void anUnrecognizedTypeIsSkipped() {
        assertNull(CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry("vault", "minecraft:polished_andesite"))));
    }

    @Test
    void aPatternWithNoBlockIsSkipped() {
        assertNull(CeilingPatternSelector.toProvider(entry(
                new SurfacePatternEntry("coffers", Optional.empty(), Optional.empty(), 0, 3, 1, 0))));
    }

    /**
     * The ceiling's degradation rule differs from the wall's on purpose: the list is several
     * independent patterns, so a typo in the boss should not silently strip the coffers with it.
     */
    @Test
    void oneBadPatternIsDroppedAndTheRestSurvive() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(entry(
                new SurfacePatternEntry("coffers", "minecraft:polished_andesite"),
                new SurfacePatternEntry("centre", "minecraft:not_a_real_block")));

        assertInstanceOf(GridSurfacePatternProvider.class, provider,
                "a single surviving layer should not be wrapped");
        assertTrue(provider.plan(7, 7, Direction.DOWN).markedCells() > 0);
    }

    /** Ordering is execution order: the boss goes on last, so it wins the centre. */
    @Test
    void patternsLayerInListOrder() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(entry(
                new SurfacePatternEntry("coffers", "minecraft:polished_andesite"),
                new SurfacePatternEntry("centre", "minecraft:chiseled_stone_bricks")));

        SurfacePlan plan = provider.plan(7, 7, Direction.DOWN);
        assertSame(net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                plan.get(3, 3), "the later pattern should win the centre cell");
    }

    /** cornerBlock is optional and falls back to block -- an authored value, not a guessed one. */
    @Test
    void anAbsentCornerBlockFallsBackToTheEdgeBlock() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry("border", "minecraft:polished_andesite")));
        SurfacePlan plan = provider.plan(5, 5, Direction.DOWN);
        assertSame(plan.get(2, 0), plan.get(0, 0), "corner and edge should be the same state");
    }

    // ---------- end to end ----------

    /**
     * Through the real generator: the pattern lands on the ceiling plane, over the interior
     * footprint only, and never on the wall ring.
     */
    @Test
    void aCofferedCeilingRendersOnTheCeilingPlaneOverTheInteriorOnly() {
        RoomData room = new RoomData(1, 10, 20, 9, 9, 6, RoomRole.NORMAL);
        int floorY = 60;
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCeilingGenerator()
                .withCeilingPattern(CeilingPatternSelector.providerFor(Optional.of(
                        entry(new SurfacePatternEntry("coffers", "minecraft:polished_andesite")))))
                .build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        int ceilingY = floorY + room.getHeight() - 1;
        Set<String> ribs = new HashSet<>();
        for (BlockPlacement bp : out) {
            assertEquals(ceilingY, bp.getY(), "everything belongs on the ceiling plane: " + bp);
            int x = bp.getX() - room.getOriginX();
            int z = bp.getZ() - room.getOriginZ();
            assertTrue(x >= 1 && x <= room.getWidth() - 2, "ceiling leaked onto the wall ring: " + bp);
            assertTrue(z >= 1 && z <= room.getDepth() - 2, "ceiling leaked onto the wall ring: " + bp);
            if ("minecraft:polished_andesite".equals(bp.getBlockId())) {
                ribs.add(x + "," + z);
            }
        }
        // 7x7 interior, still one placement per cell.
        assertEquals(49, out.size());
        assertTrue(!ribs.isEmpty() && ribs.size() < 49, "ribs should be some but not all cells");
    }

    // ---------- projection ----------

    private static SurfacePatternEntry hanging(String type, String block) {
        return new SurfacePatternEntry(type, Optional.of(block), Optional.empty(), 0, 3, 1, 1);
    }

    /**
     * The point of the whole feature: a projecting rib leaves the ceiling plane plain and hangs a
     * cell below, so the panels between the ribs are genuinely recessed rather than flush with them.
     */
    @Test
    void aProjectingCofferHangsBelowAPlainCeiling() {
        RoomData room = new RoomData(1, 10, 20, 9, 9, 7, RoomRole.NORMAL);
        int floorY = 60;
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCeilingGenerator()
                .withCeilingPattern(CeilingPatternSelector.providerFor(Optional.of(
                        entry(hanging("coffers", "minecraft:polished_andesite")))))
                .build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        int ceilingY = floorY + room.getHeight() - 1;
        int ribs = 0;
        for (BlockPlacement bp : out) {
            if (bp.getY() == ceilingY) {
                assertEquals("minecraft:stone_bricks", bp.getBlockId(),
                        "a projecting treatment must leave the ceiling plane plain: " + bp);
            } else {
                assertEquals(ceilingY - 1, bp.getY(), "ribs hang exactly one cell: " + bp);
                assertEquals("minecraft:polished_andesite", bp.getBlockId());
                ribs++;
            }
        }
        assertEquals(49, out.size() - ribs, "the full 7x7 ceiling plane is still emitted");
        assertTrue(ribs > 0, "expected some hanging ribs");
    }

    /** Hanging ribs as floor-local (x, z) -- the cells a coffer actually claims. */
    private static Set<String> hangingRibs(RoomData room, int floorY) {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCeilingGenerator()
                .withCeilingPattern(CeilingPatternSelector.providerFor(Optional.of(
                        entry(hanging("coffers", "minecraft:polished_andesite")))))
                .build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        int hangingY = floorY + room.getHeight() - 2;
        Set<String> ribs = new HashSet<>();
        for (BlockPlacement bp : out) {
            if (bp.getY() == hangingY) {
                ribs.add((bp.getX() - room.getOriginX()) + "," + (bp.getZ() - room.getOriginZ()));
            }
        }
        return ribs;
    }

    /**
     * The regression that shipped: ribs must run right up to the wall. They were briefly inset by a
     * cell to keep clear of a projecting crown, which left a ring of plain ceiling around every
     * lattice -- including on the schemes whose crown is flush and never contested those cells at
     * all. The ceiling overrides the wall's trim in that ring instead of dodging it; see
     * {@code CeilingSurface#emitProjected} and {@code BasicRoomGeneratorTest}.
     */
    @Test
    void ribsReachTheWall() {
        RoomData room = new RoomData(1, 0, 0, 11, 11, 7, RoomRole.NORMAL);
        Set<String> ribs = hangingRibs(room, 60);

        // Interior cells run 1..9; a rib must land on the first of them, hard against the wall.
        assertTrue(ribs.stream().anyMatch(cell -> cell.startsWith("1,")),
                "no rib reached the wall: " + ribs);
        assertTrue(ribs.stream().anyMatch(cell -> cell.endsWith(",1")),
                "no rib reached the wall: " + ribs);
    }

    /** Flush and hanging treatments in one entry land in their own layers, not on top of each other. */
    @Test
    void flushAndHangingPatternsSeparateByDepth() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(entry(
                new SurfacePatternEntry("border", "minecraft:andesite"),
                hanging("coffers", "minecraft:polished_andesite")));

        assertInstanceOf(IProjectingPatternProvider.class, provider);
        SurfacePlan flush = provider.plan(9, 9, Direction.DOWN);
        for (int u = 0; u < 9; u++) {
            for (int v = 0; v < 9; v++) {
                if (flush.get(u, v) != null) {
                    assertSame(Blocks.ANDESITE.defaultBlockState(), flush.get(u, v),
                            "only the flush border belongs in the ceiling plane");
                }
            }
        }

        SurfacePlan hangingLayer = ((IProjectingPatternProvider) provider)
                .projectedPlans(9, 9, Direction.DOWN).get(1);
        assertTrue(hangingLayer.markedCells() > 0, "the coffers should be in the depth-1 layer");
    }

    /** A single flush layer stays a bare provider; anything that projects needs the wrapper. */
    @Test
    void aSingleHangingLayerIsStillWrapped() {
        assertInstanceOf(IProjectingPatternProvider.class, CeilingPatternSelector.toProvider(
                entry(hanging("coffers", "minecraft:polished_andesite"))));
    }

    /** With no pattern the ceiling is exactly what it always was: every interior cell, plain. */
    @Test
    void noPatternRendersThePlainCeiling() {
        RoomData room = new RoomData(1, 0, 0, 7, 7, 6, RoomRole.NORMAL);
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCeilingGenerator().build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        assertEquals(25, out.size());
        for (BlockPlacement bp : out) {
            assertEquals("minecraft:stone_bricks", bp.getBlockId());
        }
    }
}
