package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform;

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry.PlatformEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.platform.CentrePlatformLayout;
import mod.gottsch.forge.dungeons2.core.config.platform.CornersPlatformLayout;
import mod.gottsch.forge.dungeons2.core.config.platform.PlatformLayoutPattern;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
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
        return new PlatformEntry("dais", layout, BLOCK, Optional.of(STAIR),
                Optional.of("minecraft:chiseled_stone_bricks"), top, size,
                SurfaceOrient.INWARD, Map.of(), Optional.empty(), SizeGate.UNBOUNDED);
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
                SurfaceOrient.OUTWARD, Map.of(), Optional.empty(), SizeGate.UNBOUNDED);
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
                SurfaceOrient.INWARD, Map.of(), Optional.empty(), SizeGate.UNBOUNDED);

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

    private static BlockPlacement at(List<BlockPlacement> out, int x, int z) {
        return out.stream().filter(bp -> bp.getX() == x && bp.getZ() == z && bp.getY() == 61)
                .findFirst().orElseThrow(() -> new AssertionError("nothing at " + x + "," + z));
    }
}
