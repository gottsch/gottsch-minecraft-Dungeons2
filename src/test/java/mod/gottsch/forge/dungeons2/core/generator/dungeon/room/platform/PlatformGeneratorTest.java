package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform;

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry.PlatformEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.PitPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.pit.CentrePitShape;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.RoomPitGenerator;
import mod.gottsch.forge.dungeons2.core.config.platform.CentrePlatformLayout;
import mod.gottsch.forge.dungeons2.core.config.platform.CornersPlatformLayout;
import mod.gottsch.forge.dungeons2.core.config.platform.PlatformLayoutPattern;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.config.PotConfig;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Raised platforms: the dais's shape, its steps, and what stands on it.
 *
 * <p>The dais is the first feature with <strong>internal</strong> structure -- a cell's role depends
 * on where it sits within the platform, not just whether the platform is there. So these assert the
 * roles cell by cell rather than counting blocks.
 */
class PlatformGeneratorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String BLOCK = "minecraft:stone_bricks";
    private static final String STAIR = "minecraft:stone_brick_stairs";
    private static final String TOP = "minecraft:campfire";

    private static PlatformEntry dais(PlatformLayoutPattern layout, int size, Optional<String> top) {
        return dais(layout, size, top, Optional.empty());
    }

    private static PlatformEntry dais(PlatformLayoutPattern layout, int size, Optional<String> top,
                                      Optional<PotConfig> topProps) {
        return dais(layout, size, top, topProps, Optional.empty());
    }

    private static PlatformEntry dais(PlatformLayoutPattern layout, int size, Optional<String> top,
                                      Optional<PotConfig> topProps, Optional<ChestConfig> topChest) {
        return new PlatformEntry("dais", layout, BLOCK, Optional.of(STAIR),
                Optional.of("minecraft:chiseled_stone_bricks"), top, size,
                SurfaceOrient.INWARD, Map.of(), Optional.empty(), topProps, topChest,
                SizeGate.UNBOUNDED);
    }

    /** A chest that always draws, from one named table. */
    private static Optional<ChestConfig> chest() {
        return Optional.of(new ChestConfig(1, 1,
                Optional.of(List.of(new ChestConfig.LootTableEntry("dungeons2:chests/classic_shallow", 1))),
                List.of(new ChestConfig.ChestVariant("minecraft:chest", 1)), SizeGate.UNBOUNDED));
    }

    /** Exactly {@code count} pots, so a test can assert WHICH cells rather than how many. */
    private static Optional<PotConfig> props(int count) {
        return Optional.of(new PotConfig(count, count, "dungeons2:pots/classic",
                List.of(new PotConfig.PotVariant("dungeonblocks:stone_pot", 1))));
    }

    private static List<EntityPlacement> buildProps(RoomData room, PlatformEntry entry) {
        BasicPlatformGenerator gen = new BasicPlatformGenerator();
        build(room, entry, gen);
        return gen.entities();
    }

    private static List<BlockPlacement> build(RoomData room, PlatformEntry entry,
                                              BasicPlatformGenerator gen) {
        List<BlockPlacement> out = new ArrayList<>();
        gen.withPlatformLayouts(PlatformPatternSelector.toLayouts(
                        new PlatformPatternEntry(List.of(entry))))
                .build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        return out;
    }

    private static RoomData room(int w, int d, int h) {
        return new RoomData(1, 0, 0, w, d, h, RoomRole.NORMAL);
    }

    @Test
    void aDaisIsOneRowAboveTheFloor() {
        List<BlockPlacement> out = build(room(11, 11, 7), dais(new CentrePlatformLayout(), 3, Optional.empty()),
                new BasicPlatformGenerator());
        assertEquals(Set.of(61), out.stream().map(BlockPlacement::getY).collect(Collectors.toSet()),
                "the dais sits in the air above the finished floor, it does not replace it");
        assertEquals(9, out.size(), "a 3x3 dais is nine cells");
    }

    /** Centre block, stairs on the mid-sides, plain block on the corners. */
    @Test
    void theMidSidesAreStepsAndTheCornersAreNot() {
        List<BlockPlacement> out = build(room(11, 11, 7), dais(new CentrePlatformLayout(), 3, Optional.empty()),
                new BasicPlatformGenerator());
        // 11-wide room -> interior 9 -> centre at interior 4 -> floor-local 5.
        assertEquals("minecraft:chiseled_stone_bricks", at(out, 5, 5).getBlockId(), "centre");
        for (int[] side : new int[][]{{4, 5}, {6, 5}, {5, 4}, {5, 6}}) {
            assertEquals(STAIR, at(out, side[0], side[1]).getBlockId(),
                    "mid-side " + side[0] + "," + side[1] + " should be a step");
        }
        for (int[] corner : new int[][]{{4, 4}, {6, 4}, {4, 6}, {6, 6}}) {
            assertEquals(BLOCK, at(out, corner[0], corner[1]).getBlockId(),
                    "corner " + corner[0] + "," + corner[1] + " cannot face two ways, so it is solid");
        }
    }

    /**
     * INWARD points a vanilla stair's solid half at the dais centre, so the low edge meets the room
     * and a player walks up. Asserted per side, because getting one axis inverted is exactly the
     * mistake the wall courses and ceiling rings both made.
     */
    @Test
    void theStepsFaceInwardSoYouCanWalkUp() {
        List<BlockPlacement> out = build(room(11, 11, 7), dais(new CentrePlatformLayout(), 3, Optional.empty()),
                new BasicPlatformGenerator());
        assertEquals("west", at(out, 6, 5).getProperties().get("facing"),
                "the east step's solid half must point back at the centre");
        assertEquals("east", at(out, 4, 5).getProperties().get("facing"));
        assertEquals("north", at(out, 5, 6).getProperties().get("facing"));
        assertEquals("south", at(out, 5, 4).getProperties().get("facing"));
    }

    @Test
    void outwardIsTheOppositeOnEverySide() {
        PlatformEntry entry = new PlatformEntry("dais", new CentrePlatformLayout(), BLOCK,
                Optional.of(STAIR), Optional.empty(), Optional.empty(), 3,
                SurfaceOrient.OUTWARD, Map.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), SizeGate.UNBOUNDED);
        List<BlockPlacement> out = build(room(11, 11, 7), entry, new BasicPlatformGenerator());
        assertEquals("east", at(out, 6, 5).getProperties().get("facing"));
        assertEquals("west", at(out, 4, 5).getProperties().get("facing"));
    }

    @Test
    void theTopBlockStandsOnTheCentreOneRowUp() {
        List<BlockPlacement> out = build(room(11, 11, 7), dais(new CentrePlatformLayout(), 3, Optional.of(TOP)),
                new BasicPlatformGenerator());
        BlockPlacement top = out.stream().filter(bp -> TOP.equals(bp.getBlockId()))
                .findFirst().orElseThrow();
        assertEquals(62, top.getY(), "on top of the dais, not in it");
        assertEquals(5, top.getX());
        assertEquals(5, top.getZ());
    }

    /** Four daises, and the middle of the room left completely open. */
    @Test
    void aCornersLayoutPutsOneInEachCorner() {
        List<BlockPlacement> out = build(room(17, 17, 7), dais(new CornersPlatformLayout(), 3, Optional.of(TOP)),
                new BasicPlatformGenerator());
        assertEquals(4, out.stream().filter(bp -> TOP.equals(bp.getBlockId())).count());
        assertEquals(4 * 9 + 4, out.size(), "four 3x3 daises, each carrying one top block");
    }

    /**
     * A dais that would not fit inside the interior is skipped whole. In a 5-wide room the interior
     * IS 3x3, so a 3x3 dais would be the entire floor and sit across every doorway.
     */
    @Test
    void aDaisTooBigForTheRoomIsSkipped() {
        RoomData tight = room(5, 5, 7);
        tight.getDoorways().add(new Coords2D(2, 0));
        assertTrue(build(tight, dais(new CentrePlatformLayout(), 3, Optional.of(TOP)),
                new BasicPlatformGenerator()).isEmpty());
        assertFalse(build(room(9, 9, 7), dais(new CentrePlatformLayout(), 3, Optional.of(TOP)),
                new BasicPlatformGenerator()).isEmpty(), "a 9-wide room has room to spare");
    }

    /**
     * A dais at the default {@code inset: 1} keeps clear of the inner ring, so in an ordinary room it
     * cannot reach a doorway approach at all -- which is the design working, not the rule going
     * untested. The rule bites when a dais is authored wide enough to reach the ring: a size-5 dais
     * at {@code inset: 0} fills a 7-wide room's interior edge to edge.
     */
    @Test
    void aDaisTouchingADoorwayApproachIsDroppedWhole() {
        PlatformEntry wide = new PlatformEntry("dais", new CentrePlatformLayout(0), BLOCK,
                Optional.of(STAIR), Optional.empty(), Optional.empty(), 5,
                SurfaceOrient.INWARD, Map.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), SizeGate.UNBOUNDED);

        RoomData room = room(7, 7, 7);   // interior 5x5; a size-5 dais covers all of it
        assertEquals(25, build(room, wide, new BasicPlatformGenerator()).size(), "no doors yet");

        // A door at (3,0) makes floor-local (3,1) an approach cell, and the dais's edge is on it.
        room.getDoorways().add(new Coords2D(3, 0));
        assertTrue(build(room, wide, new BasicPlatformGenerator()).isEmpty(),
                "dropped whole rather than clipped -- half a platform across a doorway is worse");
    }

    /** The flip side, worth pinning: at the default inset an ordinary room is never affected. */
    @Test
    void aDaisAtTheDefaultInsetClearsTheDoorwayRingEntirely() {
        RoomData room = room(9, 9, 7);
        room.getDoorways().add(new Coords2D(4, 0));
        room.getDoorways().add(new Coords2D(0, 4));
        assertEquals(9, build(room, dais(new CentrePlatformLayout(), 3, Optional.empty()),
                new BasicPlatformGenerator()).size(),
                "a centred dais keeps off the inner ring, so doorways never reach it");
    }

    @Test
    void occupiedCellsCoverTheWholeDaisSoPotsStayOff() {
        BasicPlatformGenerator gen = new BasicPlatformGenerator();
        List<BlockPlacement> out = build(room(11, 11, 7), dais(new CentrePlatformLayout(), 3, Optional.empty()), gen);
        assertEquals(9, gen.occupiedFloorCells().size());
        assertEquals(out.stream().map(bp -> bp.getX() + "," + bp.getZ()).collect(Collectors.toSet()),
                gen.occupiedFloorCells().stream().map(c -> c.getX() + "," + c.getY())
                        .collect(Collectors.toSet()));
    }

    /** A size-1 dais is a single plinth cell -- the small-room form, with no steps to speak of. */
    @Test
    void aSizeOneDaisIsASinglePlinth() {
        List<BlockPlacement> out = build(room(9, 9, 7), dais(new CentrePlatformLayout(), 1, Optional.of(TOP)),
                new BasicPlatformGenerator());
        assertEquals(2, out.size(), "one plinth cell and the thing standing on it");
    }

    /**
     * <strong>Replaces {@code anUnrecognizedLayoutOrTypeDrawsNothing}.</strong> Both halves of that
     * test are now LOAD ERRORS rather than silent skips, and neither can reach the selector:
     * an unregistered {@code layout} cannot decode, and a non-dais {@code type} is rejected by
     * {@code PlatformPatternEntry.validate}. "Draws nothing" was the worst possible outcome for
     * either -- the room simply came out flat with nothing logged.
     */
    @Test
    void anUnrecognizedLayoutOrTypeIsALoadError() {
        DataResult<PlatformPatternEntry> badLayout = PlatformPatternEntry.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"patterns\": [{\"type\": \"dais\", \"layout\": \"dungeons2:spiral\","
                                + " \"block\": \"" + BLOCK + "\"}]}"));
        assertTrue(badLayout.result().isEmpty(), "an unregistered layout must not decode");
        assertTrue(badLayout.error().orElseThrow().message().contains("dungeons2:spiral"));

        DataResult<PlatformPatternEntry> badType = PlatformPatternEntry.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"patterns\": [{\"type\": \"gazebo\", \"layout\": \"dungeons2:centre\","
                                + " \"block\": \"" + BLOCK + "\"}]}"));
        assertTrue(badType.result().isEmpty(), "an unknown platform type must not decode");
        assertTrue(badType.error().orElseThrow().message().contains("gazebo"));
    }

    // ---------- #58: a dais must not be built over a pit ----------

    /**
     * Backlog #58, the platform half. A centre dais and a centre pit want the same cells by
     * construction, which is the collision an author is most likely to write by accident.
     *
     * <h2>All-or-nothing here, per-cell for columns</h2>
     * <p>A dais with a bite taken out of it over a hole is worse than no dais, where a colonnade
     * missing one column is still a colonnade. So this generator drops the whole footprint and
     * {@code BasicPillarGenerator} skips individual cells. The asymmetry is not new &mdash; it is
     * exactly how each already treats the doorway approaches.</p>
     */
    @Test
    void aDaisOverlappingAPitIsDroppedWhole() {
        RoomData room = room(15, 15, 8);
        int floorY = 60;
        Set<Coords2D> dug = RoomPitGenerator.excavate(room, floorY,
                new PitPatternEntry(new CentrePitShape(7, 3)), 5,
                new FloorConfig(BLOCK, BLOCK), RandomSource.create(0xD2_58L), new ArrayList<>());
        assertFalse(dug.isEmpty(), "the pit did not excavate, so this test proves nothing");

        PlatformEntry entry = dais(new CentrePlatformLayout(), 5, Optional.empty());

        List<BlockPlacement> unguarded = build(room, entry, new BasicPlatformGenerator());
        assertFalse(unguarded.isEmpty(), "the dais never built, so this test proves nothing");

        List<BlockPlacement> guarded = new ArrayList<>();
        new BasicPlatformGenerator()
                .withPlatformLayouts(PlatformPatternSelector.toLayouts(
                        new PlatformPatternEntry(List.of(entry))))
                .build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(1L), guarded, dug);

        assertTrue(guarded.isEmpty(),
                "the dais sits on the pit and must be dropped entirely, but " + guarded.size()
                        + " placement(s) were emitted");
    }

    /**
     * The no-exclusion overload must be exactly an empty exclusion set, so a room with no pit lays
     * out as it always has and #58 re-rolls nothing.
     */
    @Test
    void theConvenienceOverloadIsTheSameAsExcludingNothing() {
        RoomData room = room(15, 15, 8);
        PlatformEntry entry = dais(new CentrePlatformLayout(), 5, Optional.empty());

        List<BlockPlacement> viaOverload = build(room, entry, new BasicPlatformGenerator());

        List<BlockPlacement> viaEmptySet = new ArrayList<>();
        new BasicPlatformGenerator()
                .withPlatformLayouts(PlatformPatternSelector.toLayouts(
                        new PlatformPatternEntry(List.of(entry))))
                .build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), viaEmptySet, Set.of());

        assertFalse(viaOverload.isEmpty(), "nothing built, so the comparison is vacuous");
        assertEquals(viaOverload.size(), viaEmptySet.size(),
                "the no-exclusion overload and an empty exclusion set built different rooms");
    }

    // ---------- top_props: the pots ON the dais (#86) ----------

    /**
     * A dais pot stands a row ABOVE the dais blocks, which is two rows above the finished floor.
     *
     * <p>This is the whole reason the room's own {@code pots} slot cannot do it: that slot places at
     * the walking plane, which here is the row the dais itself occupies -- a pot authored there
     * would be inside the dais block and would drop and shatter on the first chunk tick.</p>
     */
    @Test
    void aDaisPotStandsOnTheDaisRatherThanInIt() {
        List<EntityPlacement> pots = buildProps(room(11, 11, 7),
                dais(new CentrePlatformLayout(), 1, Optional.empty(), props(1)));

        assertEquals(1, pots.size());
        assertEquals(62, pots.get(0).getY(),
                "the dais block is at 61, so its surface is 62");
        assertEquals(5, pots.get(0).getX(), "the centre of an 11-wide room");
        assertEquals(5, pots.get(0).getZ());
        assertEquals("dungeons2:pots/classic", pots.get(0).getLootTable(),
                "a dais pot is a pot: it carries the same loot table vocabulary");
    }

    /**
     * The one-block plinth carries its pot on the only cell it has &mdash; the "ornamental block
     * with a pot on it" this was asked for. Nothing is excluded at {@code size: 1}, even though the
     * stair block and the centre block are the same id there.
     */
    @Test
    void theOneBlockPlinthIsItselfAValidPerch() {
        List<EntityPlacement> pots = buildProps(room(7, 7, 7),
                dais(new CentrePlatformLayout(), 1, Optional.empty(), props(1)));

        assertEquals(1, pots.size(), "size 1 has exactly one cell and it is a surface, not a step");
        assertEquals(3, pots.get(0).getX(), "the centre of a 7-wide room");
        assertEquals(3, pots.get(0).getZ());
    }

    /**
     * A pot never stands on a step, and never where {@code top_block} already stands. On a 3x3 that
     * leaves the four corners exactly &mdash; asked for four, four corners come back.
     */
    @Test
    void daisPotsKeepOffTheStepsAndOffTheTopBlock() {
        List<EntityPlacement> pots = buildProps(room(11, 11, 7),
                dais(new CentrePlatformLayout(), 3, Optional.of(TOP), props(4)));

        Set<Coords2D> where = pots.stream()
                .map(pot -> new Coords2D(pot.getX(), pot.getZ()))
                .collect(Collectors.toSet());
        assertEquals(Set.of(new Coords2D(4, 4), new Coords2D(6, 4),
                        new Coords2D(4, 6), new Coords2D(6, 6)), where,
                "the four corners: the mid-sides are steps and the centre holds the top block");
        for (EntityPlacement pot : pots) {
            assertEquals(62, pot.getY());
        }
    }

    /** With the centre free it becomes a perch too: five surfaces on a 3x3, not four. */
    @Test
    void aDaisWithNoTopBlockOffersItsCentreAsWell() {
        List<EntityPlacement> pots = buildProps(room(11, 11, 7),
                dais(new CentrePlatformLayout(), 3, Optional.empty(), props(9)));

        Set<Coords2D> where = pots.stream()
                .map(pot -> new Coords2D(pot.getX(), pot.getZ()))
                .collect(Collectors.toSet());
        assertEquals(5, where.size(), "four corners and the centre; the four mid-sides are steps");
        assertTrue(where.contains(new Coords2D(5, 5)), "the centre is free, so it is a perch");
    }

    /** A dais that authored none produces none, and the generator reports an empty list. */
    @Test
    void aDaisWithoutTopPropsProducesNoEntities() {
        assertTrue(buildProps(room(11, 11, 7),
                        dais(new CentrePlatformLayout(), 3, Optional.of(TOP))).isEmpty(),
                "nothing was authored, so nothing may be placed");
    }

    // ---------- top_chest: the treasure ON the dais (#86) ----------

    /**
     * A dais chest is a REAL chest: it stands on the dais surface and carries the loot table that
     * makes it worth walking to.
     *
     * <p>The alternative an author would otherwise reach for is {@code top_block:
     * "minecraft:chest"}, which places a chest with no block entity data at all &mdash; a chest that
     * generates EMPTY. That is the outcome the whole chest slot refuses ("an empty chest costs the
     * player a walk to find out it was empty"), which is why the chest is its own field rather than
     * a block id.</p>
     */
    @Test
    void aDaisChestStandsOnTheDaisAndCarriesItsLootTable() {
        List<BlockPlacement> out = build(room(11, 11, 7),
                dais(new CentrePlatformLayout(), 1, Optional.empty(), Optional.empty(), chest()),
                new BasicPlatformGenerator());

        BlockPlacement chest = out.stream()
                .filter(placement -> "minecraft:chest".equals(placement.getBlockId()))
                .findFirst().orElseThrow(() -> new AssertionError("no chest was placed"));
        assertEquals(62, chest.getY(), "the plinth is at 61, so its surface is 62");
        assertEquals(5, chest.getX());
        assertEquals(5, chest.getZ());
        assertTrue(chest.getProperties().containsKey("facing"),
                "a chest with no wall behind it still has to face somewhere");
        assertEquals("dungeons2:chests/classic_shallow",
                chest.getBlockEntityNbt().getData().get("LootTable"),
                "without this the chest generates empty");
        assertFalse("0".equals(chest.getBlockEntityNbt().getData().get("LootTableSeed")),
                "a zero seed means 'roll fresh on open', which is a re-rollable chest");
    }

    /** The pots keep off a centre a chest is standing in, exactly as they do for a top block. */
    @Test
    void daisPotsKeepOffAChestToo() {
        BasicPlatformGenerator gen = new BasicPlatformGenerator();
        build(room(11, 11, 7),
                dais(new CentrePlatformLayout(), 3, Optional.empty(), props(9), chest()), gen);

        Set<Coords2D> where = gen.entities().stream()
                .map(pot -> new Coords2D(pot.getX(), pot.getZ()))
                .collect(Collectors.toSet());
        assertFalse(where.contains(new Coords2D(5, 5)),
                "the chest is standing in the centre cell's air; a pot there would be inside it");
        assertEquals(4, where.size(), "which leaves the four corners");
    }

    private static BlockPlacement at(List<BlockPlacement> out, int x, int z) {
        return out.stream().filter(bp -> bp.getX() == x && bp.getZ() == z && bp.getY() == 61)
                .findFirst().orElseThrow(() -> new AssertionError("nothing at " + x + "," + z));
    }
}
