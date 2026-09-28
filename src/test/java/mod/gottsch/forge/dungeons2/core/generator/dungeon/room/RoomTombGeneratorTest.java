package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.TombConfig;
import mod.gottsch.forge.dungeons2.core.config.TombContents;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code tombs} slot (#104). What would be wrong here in a way nothing else notices: a tomb
 * with one half, a tomb generated open, a tomb whose foot is in a doorway, two tombs welded
 * together, and contents on the half DungeonBlocks does not read.
 */
class RoomTombGeneratorTest {

    private static final String BLOCK = "dungeonblocks:stone_sarcophagus";
    private static final String TABLE = "dungeons2:chests/classic_tomb_shallow";

    private static final TombContents CONTENTS = new TombContents(
            Optional.of(List.of(new ChestConfig.LootTableEntry(TABLE, 1))),
            Optional.of(List.of(new TombContents.GuardianEntry("minecraft:zombie", 1))),
            Optional.of(3), Optional.of(2), Optional.of(5));

    private static RoomData room(int width, int depth) {
        RoomData room = new RoomData();
        room.setOriginX(0);
        room.setOriginZ(0);
        room.setWidth(width);
        room.setDepth(depth);
        room.setHeight(7);
        return room;
    }

    private static TombConfig config(int min, int max, TombContents contents) {
        return new TombConfig(min, max, List.of(new TombConfig.TombVariant(BLOCK, 1)), contents,
                SizeGate.UNBOUNDED);
    }

    private static List<BlockPlacement> place(RoomData room, TombConfig config, long seed,
                                              Set<Coords2D> occupied, String motif) {
        List<BlockPlacement> out = new ArrayList<>();
        RoomTombGenerator.placeTombs(room, 64, 2, motif, config, occupied,
                RandomSource.create(seed), out);
        return out;
    }

    private static Coords2D cell(BlockPlacement placement) {
        return new Coords2D(placement.getX(), placement.getZ());
    }

    /** One step along a facing, in the floor-local grid (Coords2D's Y is Z). */
    private static Coords2D step(Coords2D from, String facing) {
        return switch (facing) {
            case "north" -> new Coords2D(from.getX(), from.getY() - 1);
            case "south" -> new Coords2D(from.getX(), from.getY() + 1);
            case "east" -> new Coords2D(from.getX() + 1, from.getY());
            default -> new Coords2D(from.getX() - 1, from.getY());
        };
    }

    /** Pairs each head with the foot the head's facing says it has, failing on an orphan half. */
    private static List<BlockPlacement[]> pairs(List<BlockPlacement> out) {
        Map<Coords2D, BlockPlacement> feet = new HashMap<>();
        List<BlockPlacement> heads = new ArrayList<>();
        for (BlockPlacement placement : out) {
            if ("head".equals(placement.getProperties().get("part"))) {
                heads.add(placement);
            } else {
                assertEquals("foot", placement.getProperties().get("part"),
                        "every half states its part");
                feet.put(cell(placement), placement);
            }
        }
        List<BlockPlacement[]> pairs = new ArrayList<>();
        for (BlockPlacement head : heads) {
            String facing = head.getProperties().get("facing");
            // facing points FOOT -> HEAD, so the foot is one step back from the head.
            Coords2D footCell = step(cell(head), opposite(facing));
            BlockPlacement foot = feet.remove(footCell);
            assertNotNull(foot, "head at " + cell(head) + " facing " + facing + " has no foot at "
                    + footCell + " -- a one-block tomb");
            assertEquals(facing, foot.getProperties().get("facing"), "the halves disagree on facing");
            pairs.add(new BlockPlacement[] {head, foot});
        }
        assertTrue(feet.isEmpty(), "feet with no head: " + feet.keySet());
        return pairs;
    }

    private static String opposite(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    @Test
    void everyTombIsTwoHalvesStatedOutrightAndClosed() {
        List<BlockPlacement> out = place(room(9, 9), config(3, 3, CONTENTS), 42L, Set.of(), "classic");
        assertEquals(6, out.size(), "three tombs are six halves");
        for (BlockPlacement half : out) {
            assertEquals(BLOCK, half.getBlockId());
            assertNotNull(half.getProperties().get("facing"), "facing must be stated");
            assertNotNull(half.getProperties().get("part"), "part must be stated");
            assertEquals("false", half.getProperties().get("open"),
                    "open must be stated FALSE -- a boolean's default state is true-first");
            assertEquals(65, half.getY(), "a tomb rests on the floor surface");
        }
        assertEquals(3, pairs(out).size());
    }

    /** Head in the wall ring and backing onto it; foot one cell further in. */
    @Test
    void theHeadBacksOntoTheWallAndTheFootPointsIntoTheRoom() {
        RoomData room = room(11, 9);
        for (long seed = 0; seed < 100; seed++) {
            for (BlockPlacement[] pair : pairs(place(room, config(2, 4, CONTENTS), seed, Set.of(),
                    "classic"))) {
                Coords2D head = cell(pair[0]);
                Coords2D wall = step(head, pair[0].getProperties().get("facing"));
                boolean wallCell = wall.getX() == 0 || wall.getX() == room.getWidth() - 1
                        || wall.getY() == 0 || wall.getY() == room.getDepth() - 1;
                assertTrue(wallCell, "seed " + seed + ": the head at " + head
                        + " faces " + wall + ", which is not the wall");
            }
        }
    }

    @Test
    void noTwoTombsShareAnEdge() {
        for (long seed = 0; seed < 200; seed++) {
            List<BlockPlacement[]> pairs = pairs(place(room(9, 9), config(4, 4, CONTENTS), seed,
                    Set.of(), "classic"));
            for (int a = 0; a < pairs.size(); a++) {
                for (int b = a + 1; b < pairs.size(); b++) {
                    for (BlockPlacement x : pairs.get(a)) {
                        for (BlockPlacement y : pairs.get(b)) {
                            int distance = Math.abs(x.getX() - y.getX()) + Math.abs(x.getZ() - y.getZ());
                            assertTrue(distance > 1, "seed " + seed + ": tombs touch at "
                                    + cell(x) + " / " + cell(y));
                        }
                    }
                }
            }
        }
    }

    /**
     * A cell the gap rule rejects is replaced from the rest of the draw, so a crypt that asked for
     * three gets three wherever three fit -- rather than silently one fewer per unlucky draw.
     */
    @Test
    void aRejectedCellIsReplacedSoTheCountIsMet() {
        for (long seed = 0; seed < 200; seed++) {
            assertEquals(3, pairs(place(room(9, 9), config(3, 3, CONTENTS), seed, Set.of(),
                    "classic")).size(), "seed " + seed);
        }
    }

    @Test
    void noHalfStandsInADoorwaysApproach() {
        RoomData room = room(9, 9);
        room.getDoorways().add(new Coords2D(4, 0));
        room.getDoorways().add(new Coords2D(0, 4));
        room.getDoorways().add(new Coords2D(8, 3));
        Set<Coords2D> approaches = RoomInterior.cellsInsideDoorways(room);
        for (long seed = 0; seed < 200; seed++) {
            for (BlockPlacement half : place(room, config(4, 4, CONTENTS), seed, Set.of(), "classic")) {
                assertFalse(approaches.contains(cell(half)), "seed " + seed + ": a tomb's "
                        + half.getProperties().get("part") + " blocks the doorway at " + cell(half));
            }
        }
    }

    @Test
    void claimedCellsAreAvoidedAndBothHalvesAreClaimed() {
        RoomData room = room(9, 9);
        Set<Coords2D> occupied = new HashSet<>();
        for (int x = 1; x <= 7; x++) {
            occupied.add(new Coords2D(x, 2));   // a row the feet on the north wall would need
        }
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> claimed = RoomTombGenerator.placeTombs(room, 64, 2, "classic",
                config(3, 3, CONTENTS), occupied, RandomSource.create(7L), out);
        for (BlockPlacement half : out) {
            assertFalse(occupied.contains(cell(half)), "a tomb stands in a claimed cell " + cell(half));
            assertTrue(claimed.contains(cell(half)), "a half the pots were not told about " + cell(half));
        }
        assertEquals(out.size(), claimed.size());
    }

    /** DungeonBlocks reads the head first; the foot carries only the depth stamp. */
    @Test
    void theContentsRideOnTheHeadAndTheStampOnBoth() {
        for (BlockPlacement[] pair : pairs(place(room(9, 9), config(2, 2, CONTENTS), 11L, Set.of(),
                "classic"))) {
            BlockEntityData head = pair[0].getBlockEntityNbt();
            BlockEntityData foot = pair[1].getBlockEntityNbt();
            assertEquals(RoomTombGenerator.SARCOPHAGUS_ENTITY, head.getType());
            assertEquals(TABLE, head.getData().get("LootTable"));
            assertNotNull(head.getData().get("LootTableSeed"));
            assertNotEquals("0", head.getData().get("LootTableSeed"),
                    "seed 0 is 'roll fresh on open'");
            assertEquals("minecraft:zombie", head.getData().get("Guardian"));
            assertEquals("3", head.getData().get("LootWeight"));
            assertEquals("2", head.getData().get("GuardianWeight"));
            assertEquals("5", head.getData().get("EmptyWeight"));

            assertNotNull(foot, "the foot needs data too, or it is written before hollow()'s air");
            assertTrue(foot.getData().isEmpty(), "contents on the foot as well would be one tomb"
                    + " holding two rolls' worth of intent: " + foot.getData());
            for (BlockEntityData data : List.of(head, foot)) {
                String stamp = data.getNbtValues().get("ForgeData");
                assertNotNull(stamp, "each half carries the origin, so either may be found");
                assertTrue(stamp.contains("dungeons2") && stamp.contains("floorIndex:2")
                        && stamp.contains("classic"), stamp);
            }
        }
    }

    @Test
    void noMotifMeansNoStamp() {
        for (BlockPlacement half : place(room(9, 9), config(1, 1, CONTENTS), 3L, Set.of(), null)) {
            assertNotNull(half.getBlockEntityNbt());
            assertNull(half.getBlockEntityNbt().getNbtValues().get("ForgeData"));
        }
    }

    /**
     * A slot that can release nothing places nothing -- and draws nothing, so a room whose tombs
     * resolve to no contents lays out its props and pots exactly as if the slot were absent.
     */
    @Test
    void aSlotThatCanReleaseNothingPlacesAndDrawsNothing() {
        TombContents inert = new TombContents(
                Optional.of(List.of(new ChestConfig.LootTableEntry(TABLE, 1))), Optional.empty(),
                Optional.of(0), Optional.empty(), Optional.of(4));
        RandomSource used = RandomSource.create(99L);
        List<BlockPlacement> out = new ArrayList<>();
        assertTrue(RoomTombGenerator.placeTombs(room(9, 9), 64, 2, "classic", config(2, 2, inert),
                Set.of(), used, out).isEmpty());
        assertTrue(out.isEmpty());
        assertEquals(RandomSource.create(99L).nextLong(), used.nextLong(),
                "the stream moved, so every room after this one would lay out differently");
    }

    /** The slot states what it states; the depth's band fills the rest, key by key. */
    @Test
    void theBandFillsOnlyWhatTheSlotDoesNotSay() {
        TombContents slot = new TombContents(Optional.empty(), Optional.empty(),
                Optional.of(1), Optional.empty(), Optional.empty());
        TombConfig resolved = config(1, 1, slot).resolvedAgainst(Optional.of(
                new mod.gottsch.forge.dungeons2.core.config.TombBand(0, CONTENTS)));
        assertEquals(Optional.of(1), resolved.contents().lootWeight(), "the slot's own weight wins");
        assertEquals(CONTENTS.lootTables(), resolved.contents().lootTables());
        assertEquals(CONTENTS.guardians(), resolved.contents().guardians());
        assertEquals(CONTENTS.emptyWeight(), resolved.contents().emptyWeight());
    }

    @Test
    void theSameSeedLaysTheSameTombs() {
        List<BlockPlacement> a = place(room(11, 11), config(2, 4, CONTENTS), 5L, Set.of(), "classic");
        List<BlockPlacement> b = place(room(11, 11), config(2, 4, CONTENTS), 5L, Set.of(), "classic");
        assertEquals(a.toString(), b.toString());
    }
}
