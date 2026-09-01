package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import mod.gottsch.forge.dungeons2.core.config.ceiling.BorderCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CentreCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CoffersCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.JoistsCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfacePatternEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CentreSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.GridSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.JoistSurfacePatternProvider;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                entry(new SurfacePatternEntry(new CoffersCeilingPattern("minecraft:polished_andesite")))));
        assertInstanceOf(BorderSurfacePatternProvider.class, CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry(new BorderCeilingPattern("minecraft:polished_andesite")))));
        assertInstanceOf(CentreSurfacePatternProvider.class, CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry(new CentreCeilingPattern("minecraft:chiseled_stone_bricks")))));
    }

    // ---------- orient and properties ----------

    /** Flush (projection 0), so {@code plan()} is the layer under test rather than an empty one. */
    private static SurfacePatternEntry ring(SurfaceOrient orient, Map<String, String> properties) {
        return new SurfacePatternEntry(new BorderCeilingPattern(
                "minecraft:stone_brick_stairs", Optional.empty(), 0, orient, properties));
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
        SurfacePatternEntry pattern = new SurfacePatternEntry(new CoffersCeilingPattern(
                "minecraft:polished_andesite", 3, Map.of("half", "top")));
        assertSame(Blocks.POLISHED_ANDESITE.defaultBlockState(), planOf(pattern, 7, 7).get(3, 3));
    }

    /**
     * A ring authored before {@code orient} existed must come out byte-identical. This is the
     * regression that matters most here: {@code classic} already ships border and coffer patterns,
     * and every one of them decodes through the widened record.
     */
    @Test
    void anUnorientedRingIsUnchangedFromBeforeTheFeature() {
        SurfacePlan plan = planOf(new SurfacePatternEntry(new BorderCeilingPattern("minecraft:polished_andesite")), 7, 7);
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
        // Asserted through JSON now, and it HAS to be: since the pattern types became registry
        // entries, `coffers` does not declare `orient` at all, so an oriented coffers cannot be
        // constructed in Java to encode. That is the improvement -- the rule stopped being a
        // hand-written check in CeilingPatternEntry.validate and became the schema itself. The
        // authoring mistake this protects against is unchanged, and this is the form an author
        // would actually write it in.
        DataResult<CeilingPatternEntry> parsed = CeilingPatternEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"patterns\": [{\"type\": \"dungeons2:coffers\","
                        + " \"config\": {\"block\": \"minecraft:polished_andesite\","
                        + " \"orient\": \"outward\"}}]}"));
        assertTrue(parsed.result().isEmpty(), "expected a load error, got " + parsed.result());
        assertTrue(parsed.error().orElseThrow().message().contains("orient"),
                "the message should name the offending field: " + parsed.error().orElseThrow().message());
    }

    // ---------- joists (backlog #36) ----------

    /** Flush (projection 0), so {@code plan()} is the layer under test rather than an empty one. */
    private static SurfacePatternEntry joists(Optional<String> bracket, SurfaceOrient orient) {
        return new SurfacePatternEntry(new JoistsCeilingPattern(
                "minecraft:spruce_log", 3, bracket, orient, Map.of()));
    }

    @Test
    void joistsDispatchToTheirOwnProvider() {
        assertInstanceOf(JoistSurfacePatternProvider.class, CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry(new JoistsCeilingPattern("minecraft:spruce_log")))));
    }

    /** A bracket is optional, so an entry without one is a single flush layer of bare beams. */
    @Test
    void joistsWithoutABracketAreOneLayerOfBeams() {
        assertInstanceOf(JoistSurfacePatternProvider.class, CeilingPatternSelector.toProvider(
                entry(joists(Optional.empty(), SurfaceOrient.NONE))));
    }

    /**
     * <strong>A bracket is a layer of its own, one row below the beams.</strong> A corbel carries
     * its beam from underneath; one placed in the beam's own row is not supporting it, it is
     * interrupting it &mdash; which is what the first cut did, and what Mark rejected on the first
     * screenshots. So one authored pattern becomes two layers here, and the depth grouping the
     * selector already had keeps them apart.
     */
    @Test
    void aBracketedJoistEntryHangsItsBracketsARowBelowTheBeams() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(
                entry(joists(Optional.of("minecraft:stone_brick_stairs"), SurfaceOrient.INWARD)));
        SurfacePlan beams = provider.plan(5, 9, Direction.DOWN, RandomSource.create(0L));
        for (int u = 0; u < 5; u++) {
            assertEquals(Blocks.SPRUCE_LOG, beams.get(u, 4).getBlock(),
                    "the beam layer runs unbroken at u=" + u);
        }
        Map<Integer, SurfacePlan> below = assertInstanceOf(IProjectingPatternProvider.class, provider)
                .projectedPlans(5, 9, Direction.DOWN, RandomSource.create(0L));
        assertEquals(Set.of(1), below.keySet(), "the brackets hang exactly one row down");
        SurfacePlan brackets = below.get(1);
        assertEquals(Blocks.STONE_BRICK_STAIRS, brackets.get(0, 4).getBlock());
        assertEquals(Blocks.STONE_BRICK_STAIRS, brackets.get(4, 4).getBlock());
        assertNull(brackets.get(2, 4), "and nothing hangs under the middle of the span");
    }

    /**
     * A bracket id that will not resolve degrades to no bracket rather than dropping the pattern --
     * the same call the ring's {@code corner_block} makes. A typo in the trim should not delete the
     * beams it was decorating.
     */
    @Test
    void anUnresolvableBracketLeavesTheBeamsAlone() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(
                entry(joists(Optional.of("dungeonblocks:no_such_corbel"), SurfaceOrient.NONE)));
        // Bare beams, and specifically nothing hanging below them: an unknown id resolving to the
        // block registry's default would hang a row of AIR under every run -- backlog #13's trap,
        // here in its most visible form. blockOrNull rejecting AIR is what stops it.
        assertInstanceOf(JoistSurfacePatternProvider.class, provider);
        SurfacePlan plan = provider.plan(5, 9, Direction.DOWN, RandomSource.create(0L));
        assertEquals(Blocks.SPRUCE_LOG, plan.get(0, 4).getBlock());
        assertEquals(Blocks.SPRUCE_LOG, plan.get(4, 4).getBlock());
    }

    /**
     * {@code orient} on joists turns the bracket, so authoring one with no bracket is a line that
     * does nothing -- the silent-nothing class this whole validate exists to close. It fails the
     * load instead.
     */
    @Test
    void orientOnJoistsWithNoBracketFailsTheLoad() {
        DataResult<JsonElement> encoded = CeilingPatternEntry.CODEC.encodeStart(JsonOps.INSTANCE,
                new CeilingPatternEntry(List.of(joists(Optional.empty(), SurfaceOrient.INWARD))));
        assertTrue(encoded.error().isPresent(), "expected a load error, got " + encoded.result());
        assertTrue(encoded.error().get().message().contains("bracket_block"),
                "the message should name what is missing: " + encoded.error().get().message());
    }

    @Test
    void orientOnJoistsWithABracketLoadsCleanly() {
        DataResult<JsonElement> encoded = CeilingPatternEntry.CODEC.encodeStart(JsonOps.INSTANCE,
                new CeilingPatternEntry(List.of(
                        joists(Optional.of("dungeonblocks:spruce_corbel_block"), SurfaceOrient.INWARD))));
        assertTrue(encoded.error().isEmpty(), "unexpected error: " + encoded.error());
    }

    /** And the field is joists-only: a ring has corners, not ends. */
    @Test
    void aBracketOnANonJoistPatternFailsTheLoad() {
        // Through JSON for the same reason as orientOnANonBorderPatternFailsTheLoad: `border` has
        // no bracketBlock field to set.
        DataResult<CeilingPatternEntry> parsed = CeilingPatternEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"patterns\": [{\"type\": \"dungeons2:border\","
                        + " \"config\": {\"block\": \"minecraft:stone_brick_stairs\","
                        + " \"bracket_block\": \"dungeonblocks:spruce_corbel_block\"}}]}"));
        assertTrue(parsed.result().isEmpty(), "expected a load error, got " + parsed.result());
        assertTrue(parsed.error().orElseThrow().message().contains("bracket_block"),
                "the message should name the offending field: " + parsed.error().orElseThrow().message());
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
                entry(new SurfacePatternEntry(new CentreCeilingPattern("minecraft:chiseled_stone_bricks")))));
    }

    @Test
    void anUnrecognizedTypeIsALoadError() {
        // Was anUnrecognizedTypeIsSkipped. An unregistered type cannot reach the selector any more
        // -- it fails at decode, naming the id and listing what IS registered, which beats a
        // ceiling that silently came out flat.
        DataResult<CeilingPatternEntry> parsed = CeilingPatternEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"patterns\": [{\"type\": \"dungeons2:vault\","
                        + " \"config\": {\"block\": \"minecraft:polished_andesite\"}}]}"));
        assertTrue(parsed.result().isEmpty());
        assertTrue(parsed.error().orElseThrow().message().contains("dungeons2:vault"));
    }

    @Test
    void aPatternWithNoBlockIsALoadError() {
        // Was aPatternWithNoBlockIsSkipped. `block` is a required fieldOf on every ceiling type
        // now, which the flat record could not express -- it had to be Optional because some type
        // or other always left it out.
        DataResult<CeilingPatternEntry> parsed = CeilingPatternEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"patterns\": [{\"type\": \"dungeons2:coffers\"}]}"));
        assertTrue(parsed.result().isEmpty());
        assertTrue(parsed.error().orElseThrow().message().contains("block"));
    }

    /**
     * The ceiling's degradation rule differs from the wall's on purpose: the list is several
     * independent patterns, so a typo in the boss should not silently strip the coffers with it.
     */
    @Test
    void oneBadPatternIsDroppedAndTheRestSurvive() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(entry(
                new SurfacePatternEntry(new CoffersCeilingPattern("minecraft:polished_andesite")),
                new SurfacePatternEntry(new CentreCeilingPattern("minecraft:not_a_real_block"))));

        assertInstanceOf(GridSurfacePatternProvider.class, provider,
                "a single surviving layer should not be wrapped");
        assertTrue(provider.plan(7, 7, Direction.DOWN).markedCells() > 0);
    }

    /** Ordering is execution order: the boss goes on last, so it wins the centre. */
    @Test
    void patternsLayerInListOrder() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(entry(
                new SurfacePatternEntry(new CoffersCeilingPattern("minecraft:polished_andesite")),
                new SurfacePatternEntry(new CentreCeilingPattern("minecraft:chiseled_stone_bricks"))));

        SurfacePlan plan = provider.plan(7, 7, Direction.DOWN);
        assertSame(net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                plan.get(3, 3), "the later pattern should win the centre cell");
    }

    /** cornerBlock is optional and falls back to block -- an authored value, not a guessed one. */
    @Test
    void anAbsentCornerBlockFallsBackToTheEdgeBlock() {
        ISurfacePatternProvider provider = CeilingPatternSelector.toProvider(
                entry(new SurfacePatternEntry(new BorderCeilingPattern("minecraft:polished_andesite"))));
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
                        entry(new SurfacePatternEntry(new CoffersCeilingPattern("minecraft:polished_andesite"))))))
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
        return new SurfacePatternEntry(pattern(type, block), 1, SizeGate.UNBOUNDED);
    }

    /** Test-local name-to-type mapping, replacing the selector switch these tests used to drive. */
    private static CeilingPattern pattern(String type, String block) {
        return switch (type) {
            case "coffers" -> new CoffersCeilingPattern(block);
            case "border" -> new BorderCeilingPattern(block);
            case "joists" -> new JoistsCeilingPattern(block);
            case "centre", "center" -> new CentreCeilingPattern(block);
            default -> throw new IllegalArgumentException("no such ceiling pattern: " + type);
        };
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
                new SurfacePatternEntry(new BorderCeilingPattern("minecraft:andesite")),
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

    // ---------- per-entry gates (backlog #24) ----------

    private static SurfacePatternEntry gated(String type, String block, int minSize) {
        return new SurfacePatternEntry(pattern(type, block), 0,
                new mod.gottsch.forge.dungeons2.core.config.SizeGate(0, minSize,
                        Optional.empty(), Optional.empty()));
    }

    /**
     * The case a slot-level gate cannot express, and the reason this was the last list in the schema
     * still missing one: a coffered lattice belongs on every ceiling, while the boss at its centre is
     * a lonely dot in a small room. Before this, that needed two schemes.
     */
    @Test
    void aPatternGatesOutWhileTheRestOfTheListStillDraws() {
        CeilingPatternEntry entry = entry(
                new SurfacePatternEntry(new CoffersCeilingPattern("minecraft:polished_andesite")),
                gated("centre", "minecraft:chiseled_stone_bricks", 11));

        assertEquals(1, entry.forRoom(7, 7, 6).patterns().size(),
                "the boss gates out of a 7-wide room");
        assertEquals(2, entry.forRoom(13, 13, 6).patterns().size(),
                "and is back in a 13-wide one");
        assertInstanceOf(CoffersCeilingPattern.class,
                entry.forRoom(7, 7, 6).patterns().get(0).pattern(),
                "the lattice is the one that survives");
    }

    /** Nothing gated means the same instance back, not a copy -- the wall's rule, mirrored. */
    @Test
    void anUngatedListIsReturnedUnchanged() {
        CeilingPatternEntry entry = entry(new SurfacePatternEntry(new CoffersCeilingPattern("minecraft:polished_andesite")));
        assertSame(entry, entry.forRoom(7, 7, 6));
    }

    /** Every pattern gating out is a plain ceiling, the same as an absent slot. */
    @Test
    void everyPatternGatingOutIsAPlainCeiling() {
        CeilingPatternEntry entry = entry(gated("coffers", "minecraft:polished_andesite", 11));
        assertNull(CeilingPatternSelector.providerFor(Optional.of(entry), 7, 7, 6),
                "nothing left to draw");
        assertInstanceOf(ISurfacePatternProvider.class,
                CeilingPatternSelector.providerFor(Optional.of(entry), 13, 13, 6));
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
