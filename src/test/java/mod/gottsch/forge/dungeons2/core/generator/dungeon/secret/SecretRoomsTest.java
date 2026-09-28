package mod.gottsch.forge.dungeons2.core.generator.dungeon.secret;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.SecretConfig;
import mod.gottsch.forge.dungeons2.core.config.wall.DoorJambsWallPattern;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.SecretDoorway;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.counter.CounterItemPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.counter.CounterItemPlanner.CounterChestPlan;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.BasicDoorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.HiddenDoors;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomDoorways;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector.SecretEligibility;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.BasicWallGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.secret.SecretRoomPlanner.RoomKey;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonDoorPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonPieceEmitter;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonRoomPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.dungeons2.core.world.structure.PieceNbt;
import mod.gottsch.forge.dungeons2.core.world.structure.SecretRooms;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Secret rooms end to end: which rooms can be one, the scheme roll, the hidden door column and its
 * lever, the pedestal, and the counter-item forcing a room secret.
 */
class SecretRoomsTest {

    private static final String MOTIF = "classic";
    /** Tests run without DungeonBlocks' registry, so every hidden door is taken as registered. */
    private static final Predicate<String> REGISTERED = id -> true;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------- doorway counting ----------

    @Test
    void aTwoWideOpeningIsOneDoorAndTwoSeparateOpeningsAreTwo() {
        assertEquals(1, RoomDoorways.count(List.of(new Coords2D(5, 0))));
        assertEquals(1, RoomDoorways.count(List.of(new Coords2D(5, 0), new Coords2D(6, 0))));
        assertEquals(2, RoomDoorways.count(List.of(new Coords2D(5, 0), new Coords2D(0, 5))));
        assertEquals(0, RoomDoorways.count(List.of()));
    }

    // ---------- the roll ----------

    private static RoomScheme scheme(String name, boolean secret) {
        RoomScheme plain = new RoomScheme(name, 1, 0, 0);
        return secret ? new RoomScheme(name, 1, plain.gate(), plain.slots(), plain.floors(),
                plain.doors(), Optional.of(SecretConfig.EMPTY), Optional.empty(), false) : plain;
    }

    @Test
    void aSecretSchemeIsNeverRolledInARoomThatCannotHideItsDoor() {
        List<RoomScheme> schemes = List.of(scheme("plain", false), scheme("vault", true));
        for (long seed = 0; seed < 200; seed++) {
            RoomScheme rolled = RoomSchemeSelector.select(schemes, 7, 7, 6, 0, 1,
                    SecretEligibility.NONE, RandomSource.create(seed));
            assertEquals("plain", rolled.name());
        }
    }

    @Test
    void anAllowedRoomRollsASecretSchemeByWeightAndAForcedOneAlways() {
        List<RoomScheme> schemes = List.of(scheme("plain", false), scheme("vault", true));
        int secret = 0;
        // One stream, not a fresh source per seed: a legacy RandomSource's FIRST draw barely moves
        // between neighbouring seeds, and the scheme roll is the first draw.
        RandomSource random = RandomSource.create(17L);
        for (long seed = 0; seed < 400; seed++) {
            if (RoomSchemeSelector.select(schemes, 7, 7, 6, 0, 1, SecretEligibility.ALLOWED,
                    random).isSecret()) {
                secret++;
            }
            assertTrue(RoomSchemeSelector.select(schemes, 7, 7, 6, 0, 1, SecretEligibility.FORCED,
                    RandomSource.create(seed)).isSecret());
        }
        assertTrue(secret > 140 && secret < 260, "equal weights, expected ~half: " + secret);
    }

    /** Forcing and allowing both make exactly one draw, so the room's stream after it is aligned. */
    @Test
    void forcingChangesTheArgumentToTheDrawNotHowManyAreMade() {
        List<RoomScheme> schemes = List.of(scheme("plain", false), scheme("vault", true));
        RandomSource forced = RandomSource.create(9L);
        RandomSource none = RandomSource.create(9L);
        RoomSchemeSelector.select(schemes, 7, 7, 6, 0, 1, SecretEligibility.FORCED, forced);
        RoomSchemeSelector.select(schemes, 7, 7, 6, 0, 1, SecretEligibility.NONE, none);
        assertEquals(none.nextLong(), forced.nextLong());
    }

    @Test
    void theDoorGateFiltersBeforeTheRoll() {
        RoomScheme deadEnd = new RoomScheme("dead_end", 1, scheme("x", false).gate(),
                scheme("x", false).slots(), scheme("x", false).floors(),
                new mod.gottsch.forge.dungeons2.core.config.DoorRange(0, Optional.of(1)),
                Optional.empty(), Optional.empty(), false);
        List<RoomScheme> schemes = List.of(deadEnd);
        assertEquals("dead_end", RoomSchemeSelector.select(schemes, 7, 7, 6, 0, 1,
                SecretEligibility.NONE, RandomSource.create(1L)).name());
        assertSame(RoomScheme.PLAIN, RoomSchemeSelector.select(schemes, 7, 7, 6, 0, 2,
                SecretEligibility.NONE, RandomSource.create(1L)));
    }

    // ---------- which rooms are hideable ----------

    /** A 7x7 room at (10,10) with its one door in the middle of the west wall. */
    private static RoomData westDoorRoom() {
        RoomData room = new RoomData(1, 10, 10, 7, 7, 6, RoomRole.NORMAL);
        room.getDoorways().add(new Coords2D(10, 13));
        return room;
    }

    /** A 3-wide corridor running north-south along the room's west wall. */
    private static Set<Coords2D> westCorridor() {
        Set<Coords2D> cells = new HashSet<>();
        for (int z = 5; z <= 20; z++) {
            cells.add(new Coords2D(9, z));
            cells.add(new Coords2D(8, z));
        }
        return cells;
    }

    @Test
    void aDeadEndOntoACorridorIsHideableFacingIntoTheRoom() {
        SecretDoorway secret = SecretRoomPlanner.candidate(westDoorRoom(), westCorridor(),
                Set.of(new Coords2D(10, 13)), 1L).orElseThrow();
        assertEquals(Direction2D.EAST, secret.inward());
        assertEquals(Direction2D.WEST, secret.outward());
        assertTrue(secret.decoy(), "corridor on both sides: a decoy goes opposite the lever");
        // The lever hangs on the room's own wall beside the door, in the corridor in front of it.
        assertEquals(10, secret.leverWall().getX());
        assertEquals(13 + secret.leverSide(), secret.leverWall().getY());
        assertEquals(new Coords2D(9, 13 + secret.leverSide()), secret.lever());
        assertEquals(new Coords2D(9, 13 - secret.leverSide()), secret.decoyCell());
    }

    @Test
    void theLeverSideIsRolledPerRoomButStable() {
        Set<Integer> sides = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            SecretDoorway a = SecretRoomPlanner.candidate(westDoorRoom(), westCorridor(),
                    Set.of(new Coords2D(10, 13)), seed).orElseThrow();
            assertEquals(a, SecretRoomPlanner.candidate(westDoorRoom(), westCorridor(),
                    Set.of(new Coords2D(10, 13)), seed).orElseThrow());
            sides.add(a.leverSide());
        }
        assertEquals(Set.of(1, -1), sides);
    }

    @Test
    void withCorridorOnOneSideOnlyTheLeverGoesThereAndThereIsNoDecoy() {
        Set<Coords2D> corridor = new HashSet<>();
        corridor.add(new Coords2D(9, 13));
        corridor.add(new Coords2D(9, 14));
        corridor.add(new Coords2D(8, 13));
        SecretDoorway secret = SecretRoomPlanner.candidate(westDoorRoom(), corridor,
                Set.of(new Coords2D(10, 13)), 3L).orElseThrow();
        assertEquals(1, secret.leverSide());
        assertFalse(secret.decoy());
    }

    @Test
    void roomsThatCannotHideADoorAreNotCandidates() {
        Set<Coords2D> doors = Set.of(new Coords2D(10, 13));
        // No corridor outside the door (a door into another room, or rock).
        assertTrue(SecretRoomPlanner.candidate(westDoorRoom(), Set.of(), doors, 1L).isEmpty());
        // No door piece there (a template's connector).
        assertTrue(SecretRoomPlanner.candidate(westDoorRoom(), westCorridor(), Set.of(), 1L).isEmpty());
        // Two ways in.
        RoomData twoDoors = westDoorRoom();
        twoDoors.getDoorways().add(new Coords2D(13, 10));
        assertTrue(SecretRoomPlanner.candidate(twoDoors, westCorridor(), doors, 1L).isEmpty());
        // One way in, two blocks wide: a lever opens one leaf.
        RoomData wide = westDoorRoom();
        wide.getDoorways().add(new Coords2D(10, 14));
        assertTrue(SecretRoomPlanner.candidate(wide, westCorridor(), doors, 1L).isEmpty());
        // Not a NORMAL procedural room.
        RoomData terminal = westDoorRoom();
        terminal.setRole(RoomRole.TERMINAL);
        assertTrue(SecretRoomPlanner.candidate(terminal, westCorridor(), doors, 1L).isEmpty());
        RoomData prefab = westDoorRoom();
        prefab.setTemplateId("dungeons2:rooms/classic/x");
        assertTrue(SecretRoomPlanner.candidate(prefab, westCorridor(), doors, 1L).isEmpty());
    }

    // ---------- the door column ----------

    private static BlockPlacement at(List<BlockPlacement> out, int x, int y, int z) {
        BlockPlacement found = null;
        for (BlockPlacement p : out) {
            if (p.getX() == x && p.getY() == y && p.getZ() == z) {
                found = p; // last writer wins
            }
        }
        return found;
    }

    @Test
    void theSecretColumnIsAHiddenDoorUnderAWallLintelWithALeverBeside() {
        MotifConfig motif = MotifConfigs.load(MOTIF);
        SecretDoorway secret = new SecretDoorway(10, 13, Direction2D.EAST, 1, true);
        List<BlockPlacement> out = new ArrayList<>();
        new BasicDoorGenerator().withMotifConfig(motif)
                .withSecret(secret, "dungeonblocks:stone_brick_hidden_door")
                .build(new DoorData(10, 13, 1, 2, Direction2D.EAST), 60, DungeonMotif.CLASSIC,
                        RandomSource.create(4L), out);

        String wall = "minecraft:stone_bricks";
        for (int y : new int[]{61, 62}) {
            BlockPlacement half = at(out, 10, y, 13);
            assertEquals("dungeonblocks:stone_brick_hidden_door", half.getBlockId());
            assertEquals("east", half.getProperties().get("facing"), "facing is INTO the room");
            assertEquals(y == 61 ? "lower" : "upper", half.getProperties().get("half"));
            assertEquals("false", half.getProperties().get("open"));
            assertTrue(half.isUndecorated());
        }
        BlockPlacement lintel = at(out, 10, 63, 13);
        assertEquals(wall, lintel.getBlockId(), "the lintel is the wall, not door.lintel");
        assertTrue(lintel.isUndecorated());

        BlockPlacement leverWall = at(out, 10, 62, 14);
        assertEquals(wall, leverWall.getBlockId());
        assertTrue(leverWall.isUndecorated(), "the lever's wall must survive weathering");
        BlockPlacement lever = at(out, 9, 62, 14);
        assertEquals(HiddenDoors.LEVER_SCONCE, lever.getBlockId());
        assertEquals("west", lever.getProperties().get("facing"), "facing away from its wall");
        assertEquals("false", lever.getProperties().get("powered"));
        BlockPlacement decoy = at(out, 9, 62, 12);
        assertEquals(HiddenDoors.TORCH_SCONCE, decoy.getBlockId());
        assertEquals("west", decoy.getProperties().get("facing"));

        String motifDoor = "dungeonblocks:spruce_dungeon_door";
        String motifLintel = "minecraft:polished_andesite";
        for (BlockPlacement p : out) {
            assertFalse(motifDoor.equals(p.getBlockId()), "the motif's door must not be hung");
            if (p.getY() == 63) {
                assertFalse(motifLintel.equals(p.getBlockId()), "the motif's lintel must not appear");
            }
        }
    }

    /** door.probability is about ruin: whatever it says, a secret door is always hung. */
    @Test
    void aSecretDoorIsHungOnEverySeed() {
        MotifConfig motif = MotifConfigs.load(MOTIF);
        SecretDoorway secret = new SecretDoorway(10, 13, Direction2D.EAST, -1, false);
        for (long seed = 0; seed < 50; seed++) {
            List<BlockPlacement> out = new ArrayList<>();
            new BasicDoorGenerator().withMotifConfig(motif)
                    .withSecret(secret, "dungeonblocks:stone_brick_hidden_door")
                    .build(new DoorData(10, 13, 1, 2, Direction2D.EAST), 60, DungeonMotif.CLASSIC,
                            RandomSource.create(seed), out);
            assertEquals("dungeonblocks:stone_brick_hidden_door", at(out, 10, 61, 13).getBlockId());
            assertNull(at(out, 9, 62, 14), "no decoy was asked for");
        }
    }

    @Test
    void doorJambsDoNotDressAHiddenDoorway() {
        RoomData room = new RoomData(1, 10, 20, 9, 9, 6, RoomRole.NORMAL);
        Coords2D door = new Coords2D(14, 20);
        room.getDoorways().add(door);
        var jambs = new DoorJambsWallPattern("minecraft:polished_andesite", Optional.empty(),
                Optional.empty(), Optional.of("minecraft:chiseled_stone_bricks"), Map.of()).provider();

        List<BlockPlacement> dressed = new ArrayList<>();
        new BasicWallGenerator().withWallPattern(jambs)
                .build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), dressed);
        List<BlockPlacement> hidden = new ArrayList<>();
        new BasicWallGenerator().withWallPattern(jambs).withHiddenDoorways(Set.of(door))
                .build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), hidden);

        assertTrue(dressed.stream().anyMatch(p -> p.getBlockId().equals("minecraft:polished_andesite")));
        assertFalse(hidden.stream().anyMatch(p -> p.getBlockId().equals("minecraft:polished_andesite")
                || p.getBlockId().equals("minecraft:chiseled_stone_bricks")));
        // The two door rows are still left open for the hidden door to hang in.
        assertEquals("minecraft:air", at(hidden, 14, 61, 20).getBlockId());
        assertEquals("minecraft:air", at(hidden, 14, 62, 20).getBlockId());
    }

    @Test
    void aSecretDoorwayRoundTripsThroughNbt() {
        SecretDoorway secret = new SecretDoorway(4, 9, Direction2D.SOUTH, -1, true);
        assertEquals(secret, PieceNbt.readSecretDoorway(PieceNbt.writeSecretDoorway(secret)));
    }

    // ---------- the real motif, end to end ----------

    private static DungeonLayout plan(long seed, DungeonSize size, String boss) {
        DungeonLayout layout = new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, MOTIF,
                new TemplateCatalog())
                .withSize(size)
                .withCorridorWidth(3)
                .withCorridorStyles(DungeonStructure.corridorStyleWeights(
                        MotifConfigs.load(MOTIF).corridor()))
                .plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
        layout.setBoss(boss);
        return layout;
    }

    private static List<StructurePiece> pieces(DungeonLayout layout, Optional<CounterChestPlan> counter) {
        List<StructurePiece> pieces = new ArrayList<>(DungeonPieceEmitter.emitTerrain(layout, 0, 0, 0, 0,
                null, counter.orElse(null)));
        pieces.addAll(DungeonPieceEmitter.emitDoors(layout, 0, 0));
        return pieces;
    }

    /**
     * Across real classic dungeons: some rooms come out secret, each one's door piece hangs a
     * hidden door with a lever, and each one's room renders a pedestal and no door jambs.
     */
    @Test
    void realDungeonsGrowSecretRoomsThatBuildWhole() {
        MotifConfig motif = MotifConfigs.load(MOTIF);
        int rooms = 0;
        int hideableRooms = 0;
        int secretRooms = 0;
        for (long seed = 0; seed < 40; seed++) {
            DungeonLayout layout = plan(seed, DungeonSize.MEDIUM, null);
            Map<RoomKey, SecretDoorway> hideable = SecretRooms.hideable(layout, motif, REGISTERED);
            List<StructurePiece> pieces = pieces(layout, Optional.empty());
            secretRooms += SecretRooms.apply(pieces, hideable, Optional.empty(), motif);
            hideableRooms += hideable.size();
            for (StructurePiece piece : pieces) {
                if (piece instanceof DungeonRoomPiece room) {
                    rooms++;
                    MotifConfig floorMotif = motif.forFloor(room.getFloorIndex());
                    if (room.getSecretDoorway() != null && room.rolledScheme(floorMotif).isSecret()) {
                        assertSecretRoomBuilds(room, floorMotif, "seed " + seed);
                    }
                }
                if (piece instanceof DungeonDoorPiece door && door.getSecret() != null) {
                    assertHiddenDoorBuilds(door, motif.forFloor(door.getFloorIndex()), "seed " + seed);
                }
            }
        }
        System.out.printf("[secret rooms] %d rooms, %d hideable (%.1f%%), %d secret (%.1f%%)%n",
                rooms, hideableRooms, 100.0 * hideableRooms / rooms, secretRooms,
                100.0 * secretRooms / rooms);
        assertTrue(hideableRooms > 0, "no room in 40 dungeons could hide its door");
        assertTrue(secretRooms > 0, "no hideable room ever rolled secret_vault");
    }

    private static void assertSecretRoomBuilds(DungeonRoomPiece room, MotifConfig motif, String where) {
        List<BlockPlacement> blocks = room.renderRoom(motif).getBlocks();
        long pedestals = blocks.stream().filter(p -> HiddenDoors.PEDESTAL.equals(p.getBlockId())).count();
        assertEquals(1, pedestals, where + ": a secret room has one pedestal");
        BlockPlacement pedestal = blocks.stream()
                .filter(p -> HiddenDoors.PEDESTAL.equals(p.getBlockId())).findFirst().orElseThrow();
        assertNotNull(pedestal.getBlockEntityNbt(), where);
        assertTrue(pedestal.getBlockEntityNbt().getData().containsKey("LootTable")
                        || pedestal.getBlockEntityNbt().getNbtValues().containsKey("Item"),
                where + ": the pedestal holds nothing");
        long chests = blocks.stream().filter(p -> "minecraft:chest".equals(p.getBlockId())).count();
        assertEquals(1, chests, where + ": secret_vault always has its chest");
    }

    private static void assertHiddenDoorBuilds(DungeonDoorPiece door, MotifConfig motif, String where) {
        SecretDoorway secret = door.getSecret();
        List<BlockPlacement> out = door.renderPlacements(motif, REGISTERED);
        int floorY = out.stream().mapToInt(BlockPlacement::getY).min().orElseThrow();
        BlockPlacement lower = at(out, secret.x(), floorY + 1, secret.z());
        assertTrue(lower.getBlockId().endsWith("_hidden_door"), where + ": " + lower);
        BlockPlacement lever = at(out, secret.lever().getX(), floorY + 2, secret.lever().getY());
        assertNotNull(lever, where + ": no lever");
        assertEquals(HiddenDoors.LEVER_SCONCE, lever.getBlockId());
        // The door piece's box has to reach the lever, or a chunk border between them loses it.
        assertTrue(door.getBoundingBox().isInside(new net.minecraft.core.BlockPos(
                secret.lever().getX(), floorY + 2, secret.lever().getY())), where + ": lever outside the door piece's box");
    }

    /** Mark, 2026-09-28: the counter-item goes on a secret room's pedestal when one can be had. */
    @Test
    void theCounterItemForcesASecretRoomAndSitsOnItsPedestal() {
        MotifConfig motif = MotifConfigs.load(MOTIF);
        int onPedestal = 0;
        for (long seed = 0; seed < 200 && onPedestal < 5; seed++) {
            DungeonLayout layout = plan(seed, DungeonSize.MEDIUM, "dungeons2:stone_colossus");
            Map<RoomKey, SecretDoorway> hideable = SecretRooms.hideable(layout, motif, REGISTERED);
            Optional<CounterChestPlan> counter = CounterItemPlanner.plan(layout,
                    (floorIndex, room) -> hideable.containsKey(new RoomKey(floorIndex, room.getId())));
            if (counter.isEmpty()) {
                continue;
            }
            assertEquals(!hideable.isEmpty(), counter.get().secret(), "seed " + seed
                    + ": a hideable room was available and the item went elsewhere, or vice versa");
            if (!counter.get().secret()) {
                continue;
            }
            onPedestal++;
            assertEquals("dungeons2:boots_of_shock_absorption", counter.get().item());
            List<StructurePiece> pieces = pieces(layout, counter);
            SecretRooms.apply(pieces, hideable, counter, motif);
            DungeonRoomPiece room = pieces.stream()
                    .filter(p -> p instanceof DungeonRoomPiece r
                            && r.getFloorIndex() == counter.get().floorIndex()
                            && r.getRoom().getId() == counter.get().roomId())
                    .map(p -> (DungeonRoomPiece) p).findFirst().orElseThrow();
            MotifConfig floorMotif = motif.forFloor(room.getFloorIndex());
            assertTrue(room.rolledScheme(floorMotif).isSecret(), "seed " + seed + ": not forced secret");
            assertNull(room.getCounterLootTable(), "seed " + seed + ": a counter chest as well");
            List<BlockPlacement> blocks = room.renderRoom(floorMotif).getBlocks();
            BlockPlacement pedestal = blocks.stream()
                    .filter(p -> HiddenDoors.PEDESTAL.equals(p.getBlockId())).findFirst().orElseThrow();
            assertTrue(pedestal.getBlockEntityNbt().getNbtValues().get("Item")
                    .contains("dungeons2:boots_of_shock_absorption"), "seed " + seed);
            assertTrue(pieces.stream().anyMatch(p -> p instanceof DungeonDoorPiece d
                    && d.getSecret() != null && d.getFloorIndex() == room.getFloorIndex()
                    && d.getSecret().equals(room.getSecretDoorway())), "seed " + seed + ": door not hidden");
        }
        assertTrue(onPedestal > 0, "no colossus dungeon in 200 put the boots on a pedestal");
    }

    /** No hideable rooms at all (an older DungeonBlocks): nothing is secret and nothing changes. */
    @Test
    void withoutTheHiddenDoorBlocksNoRoomIsSecret() {
        MotifConfig motif = MotifConfigs.load(MOTIF);
        for (long seed = 0; seed < 10; seed++) {
            DungeonLayout layout = plan(seed, DungeonSize.MEDIUM, null);
            assertTrue(SecretRooms.hideable(layout, motif, id -> false).isEmpty(), "seed " + seed);
        }
    }
}
