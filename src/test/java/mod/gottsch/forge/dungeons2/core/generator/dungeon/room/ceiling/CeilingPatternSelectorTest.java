package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    // ---------- orient and properties ----------

    /** Flush (projection 0), so {@code plan()} is the layer under test rather than an empty one. */
    private static SurfacePatternEntry ring(SurfaceOrient orient, Map<String, String> properties) {
        return new SurfacePatternEntry("border", Optional.of("minecraft:stone_brick_stairs"),
                Optional.empty(), 0, 3, 1, 0, orient, properties);
    }

    private static SurfacePlan planOf(SurfacePatternEntry pattern, int uSize, int vSize) {
        return CeilingPatternSelector.toProvider(entry(pattern))
                .plan(uSize, vSize, Direction.DOWN, RandomSource.create(0L));
    }

    /**
     * The end-to-end shape of a vault springing: the selector has to carry {@code orient} through to
     * the provider, and the provider has to have the surface's axes to make sense of it. A break
     * anywhere in that chain leaves every cell at the block's default facing.
     */
    @Test
    void orientReachesTheProviderAndTurnsEachSideOfTheRing() {
        SurfacePlan plan = planOf(ring(SurfaceOrient.OUTWARD, Map.of()), 7, 7);
        assertEquals(Direction.NORTH, plan.get(3, 0).getValue(StairBlock.FACING));
        assertEquals(Direction.SOUTH, plan.get(3, 6).getValue(StairBlock.FACING));
        assertEquals(Direction.WEST, plan.get(0, 3).getValue(StairBlock.FACING));
        assertEquals(Direction.EAST, plan.get(6, 3).getValue(StairBlock.FACING));
    }

    /**
     * {@code properties} must reach the corner cells too, not just the edges. A corner stair missing
     * its {@code half=top} sits at the wrong end of the block and reads as a hole in the springing --
     * the same quiet defect a wall course's shared property map exists to prevent.
     */
    @Test
    void propertiesApplyToTheCornerBlockAsWellAsTheEdge() {
        SurfacePlan plan = planOf(ring(SurfaceOrient.OUTWARD, Map.of("half", "top")), 7, 7);
        assertEquals(Half.TOP, plan.get(3, 0).getValue(StairBlock.HALF), "edge");
        assertEquals(Half.TOP, plan.get(0, 0).getValue(StairBlock.HALF), "corner");
    }

    /** A property the block does not have is ignored rather than fatal, per BlockStateCodec. */
    @Test
    void anUnknownPropertyIsIgnored() {
        SurfacePatternEntry pattern = new SurfacePatternEntry("coffers",
                Optional.of("minecraft:polished_andesite"), Optional.empty(), 0, 3, 1, 0,
                SurfaceOrient.NONE, Map.of("half", "top"));
        assertSame(Blocks.POLISHED_ANDESITE.defaultBlockState(), planOf(pattern, 7, 7).get(3, 3));
    }

    /**
     * A ring authored before {@code orient} existed must come out byte-identical. This is the
     * regression that matters most here: {@code classic} already ships border and coffer patterns,
     * and every one of them decodes through the widened record.
     */
    @Test
    void anUnorientedRingIsUnchangedFromBeforeTheFeature() {
        SurfacePlan plan = planOf(new SurfacePatternEntry("border", "minecraft:polished_andesite"), 7, 7);
        assertSame(Blocks.POLISHED_ANDESITE.defaultBlockState(), plan.get(3, 0));
        assertSame(Blocks.POLISHED_ANDESITE.defaultBlockState(), plan.get(0, 0));
    }

    /**
     * {@code orient} on a type with no outward direction is a <strong>load error</strong>, not a
     * silently ignored field. An ignored one produces a ceiling exactly as correct as it was before
     * the author wrote the line, which is the hardest authoring mistake to notice -- the pattern
     * still draws.
     */
    @Test
    void orientOnANonBorderPatternFailsTheLoad() {
        SurfacePatternEntry coffers = new SurfacePatternEntry("coffers",
                Optional.of("minecraft:polished_andesite"), Optional.empty(), 0, 3, 1, 0,
                SurfaceOrient.OUTWARD, Map.of());
        DataResult<JsonElement> encoded = CeilingPatternEntry.CODEC.encodeStart(
                JsonOps.INSTANCE, new CeilingPatternEntry(List.of(coffers)));
        assertTrue(encoded.error().isPresent(), "expected a load error, got " + encoded.result());
        assertTrue(encoded.error().get().message().contains("orient"),
                "the message should name the offending field: " + encoded.error().get().message());
    }

    /** The same field on a border is of course fine -- the guard must not be a blanket ban. */
    @Test
    void orientOnABorderLoadsCleanly() {
        DataResult<JsonElement> encoded = CeilingPatternEntry.CODEC.encodeStart(
                JsonOps.INSTANCE, new CeilingPatternEntry(List.of(ring(SurfaceOrient.OUTWARD, Map.of()))));
        assertTrue(encoded.error().isEmpty(), "unexpected error: " + encoded.error());
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
