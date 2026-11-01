package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.HangingConfig;
import mod.gottsch.forge.dungeons2.core.config.HangingConfig.HangingPlacement;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code hangings} slot. What would be wrong here in a way nothing else notices: a fixture at
 * head height, a fixture hanging from nothing or from a block whose underside cannot hold it, a
 * chain threaded through a column, and a gibbet written as one block of three.
 */
class RoomHangingGeneratorTest {

    private static final int FLOOR_Y = 64;
    private static final String CEILING = "minecraft:stone_bricks";
    private static final String CHANDELIER = "dungeonblocks:chandelier";
    private static final String GIBBET = "dungeonblocks:gibbet";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RoomData room(int width, int depth, int height) {
        RoomData room = new RoomData();
        room.setOriginX(0);
        room.setOriginZ(0);
        room.setWidth(width);
        room.setDepth(depth);
        room.setHeight(height);
        return room;
    }

    /** What the room generator has emitted by the time the hangings run: air, and a ceiling. */
    private static List<BlockPlacement> shell(RoomData room) {
        List<BlockPlacement> out = new ArrayList<>();
        RoomVolumeGenerator.hollow(room, FLOOR_Y, out);
        int ceilingY = FLOOR_Y + room.getHeight() - 1;
        for (int x = 1; x < room.getWidth() - 1; x++) {
            for (int z = 1; z < room.getDepth() - 1; z++) {
                out.add(new BlockPlacement(x, ceilingY, z, CEILING));
            }
        }
        return out;
    }

    private static HangingConfig config(int min, int max, HangingPlacement placement, String block,
                                        int minDrop, int maxDrop) {
        return new HangingConfig(min, max, placement,
                List.of(new HangingConfig.HangingVariant(block)), minDrop, maxDrop);
    }

    /** Only what the generator appended. */
    private static List<BlockPlacement> hang(RoomData room, List<BlockPlacement> out,
                                             HangingConfig config, long seed) {
        int before = out.size();
        RoomHangingGenerator.placeHangings(room, FLOOR_Y, config, RandomSource.create(seed), out);
        return new ArrayList<>(out.subList(before, out.size()));
    }

    private static List<BlockPlacement> fixtures(List<BlockPlacement> hung, String block) {
        return hung.stream().filter(p -> p.getBlockId().equals(block)).collect(Collectors.toList());
    }

    @Test
    void aChandelierHangsFlushUnderTheCeilingInTheMiddleOfAnOddRoom() {
        RoomData room = room(9, 9, 7);
        List<BlockPlacement> hung = hang(room, shell(room),
                config(1, 1, HangingPlacement.CENTRE, CHANDELIER, 0, 0), 1L);

        assertEquals(1, hung.size(), () -> hung.toString());
        BlockPlacement chandelier = hung.get(0);
        assertEquals(CHANDELIER, chandelier.getBlockId());
        assertEquals(new Coords2D(4, 4), new Coords2D(chandelier.getX(), chandelier.getZ()));
        assertEquals(FLOOR_Y + 7 - 2, chandelier.getY(), "directly under the ceiling block");
    }

    @Test
    void anEvenRoomOffersEveryCellThatSharesTheMiddle() {
        Set<Coords2D> centres = new HashSet<>(RoomHangingGenerator.candidates(room(10, 9, 7),
                HangingPlacement.CENTRE));
        assertEquals(Set.of(new Coords2D(4, 4), new Coords2D(5, 4)), centres);
        assertEquals(4, RoomHangingGenerator.candidates(room(10, 10, 7), HangingPlacement.CENTRE).size());
    }

    @Test
    void theChainRunsFromTheCeilingToTheFixture() {
        RoomData room = room(9, 9, 9);
        List<BlockPlacement> hung = hang(room, shell(room),
                config(1, 1, HangingPlacement.CENTRE, CHANDELIER, 2, 2), 1L);

        int ceilingY = FLOOR_Y + 9 - 1;
        assertEquals(List.of("minecraft:chain", "minecraft:chain", CHANDELIER),
                hung.stream().map(BlockPlacement::getBlockId).toList());
        assertEquals(List.of(ceilingY - 1, ceilingY - 2, ceilingY - 3),
                hung.stream().map(BlockPlacement::getY).toList());
    }

    @Test
    void theDropIsClampedToTheHeadroomNotRefused() {
        // Height 5: three rows of air. A one-block fixture leaves exactly two under it flush, so
        // an authored drop of 4 comes out as none -- hung shorter rather than not at all.
        RoomData room = room(9, 9, 5);
        List<BlockPlacement> hung = hang(room, shell(room),
                config(1, 1, HangingPlacement.CENTRE, CHANDELIER, 4, 4), 1L);

        assertEquals(1, hung.size(), () -> hung.toString());
        assertEquals(FLOOR_Y + 3, hung.get(0).getY());
    }

    @Test
    void everyFixtureLeavesTwoRowsOfAirBeneathIt() {
        for (int height = 4; height <= 12; height++) {
            RoomData room = room(11, 11, height);
            for (long seed = 0; seed < 20; seed++) {
                List<BlockPlacement> hung = hang(room, shell(room),
                        config(1, 4, HangingPlacement.FREE, CHANDELIER, 0, 6), seed);
                for (BlockPlacement p : fixtures(hung, CHANDELIER)) {
                    assertTrue(p.getY() >= FLOOR_Y + 1 + RoomHangingGenerator.CLEARANCE,
                            "height " + height + ": " + p);
                }
            }
        }
    }

    @Test
    void aRoomTooLowForHeadroomGetsNothing() {
        // Height 4: two rows of air, and a fixture would take one of them.
        RoomData room = room(9, 9, 4);
        assertTrue(hang(room, shell(room), config(1, 3, HangingPlacement.FREE, CHANDELIER, 0, 0), 1L)
                .isEmpty());
    }

    @Test
    void aGibbetIsWrittenAsItsThreePartsWithHeadroomBelow() {
        RoomData room = room(9, 9, 7);
        List<BlockPlacement> hung = hang(room, shell(room),
                config(1, 1, HangingPlacement.CENTRE, GIBBET, 0, 0), 1L);

        assertEquals(3, hung.size(), () -> hung.toString());
        assertEquals(List.of("top", "middle", "bottom"),
                hung.stream().map(p -> p.getProperties().get("part")).toList());
        assertEquals(FLOOR_Y + 3, hung.get(2).getY(), "bottom part two rows clear of the floor");
        assertEquals(1, hung.stream().map(p -> p.getProperties().get("facing")).distinct().count());
        assertFalse(hung.get(0).getProperties().containsKey("hanging"));
    }

    @Test
    void aGibbetIsRefusedWhereItWouldHangAtHeadHeight() {
        RoomData room = room(9, 9, 6);
        assertTrue(hang(room, shell(room), config(1, 1, HangingPlacement.FREE, GIBBET, 0, 0), 1L)
                .isEmpty());
    }

    @Test
    void aOneBlockFixtureIsWrittenHanging() {
        RoomData room = room(9, 9, 7);
        List<BlockPlacement> hung = hang(room, shell(room),
                config(1, 1, HangingPlacement.CENTRE, "minecraft:lantern", 0, 0), 1L);
        assertEquals("true", hung.get(0).getProperties().get("hanging"));
    }

    @Test
    void aColumnFilledToTheCeilingIsRefused() {
        RoomData room = room(9, 9, 7);
        List<BlockPlacement> out = shell(room);
        for (int y = FLOOR_Y + 1; y < FLOOR_Y + 6; y++) {
            out.add(new BlockPlacement(4, y, 4, CEILING));
        }
        assertTrue(hang(room, out, config(1, 1, HangingPlacement.CENTRE, CHANDELIER, 0, 0), 1L)
                .isEmpty());
    }

    @Test
    void aCeilingWhoseUndersideCannotHoldAHookIsRefused() {
        RoomData room = room(9, 9, 7);
        List<BlockPlacement> out = shell(room);
        // A top slab: its underside is half a block up, so nothing touches the hook.
        out.add(new BlockPlacement(4, FLOOR_Y + 6, 4, "minecraft:stone_brick_slab",
                Map.of("type", "top")));
        assertTrue(hang(room, out, config(1, 1, HangingPlacement.CENTRE, CHANDELIER, 0, 0), 1L)
                .isEmpty());
    }

    @Test
    void aRowNothingWroteIsNotAirToHangIn() {
        // No ceiling at all over the column: the run meets rock nobody placed, and is refused
        // rather than hung from it.
        RoomData room = room(9, 9, 7);
        List<BlockPlacement> out = new ArrayList<>();
        RoomVolumeGenerator.hollow(room, FLOOR_Y, out);
        assertTrue(hang(room, out, config(1, 1, HangingPlacement.CENTRE, CHANDELIER, 0, 0), 1L)
                .isEmpty());
    }

    @Test
    void itHangsFromWhereTheVaultRoseToNotWhereTheCeilingWas() {
        RoomData room = room(9, 9, 7);
        List<BlockPlacement> out = shell(room);
        int ceilingY = FLOOR_Y + 6;
        // CeilingSurface#emitRaised's shape: excavate the plane, roof it two rows up.
        out.add(new BlockPlacement(4, ceilingY, 4, "minecraft:air"));
        out.add(new BlockPlacement(4, ceilingY + 1, 4, "minecraft:air"));
        out.add(new BlockPlacement(4, ceilingY + 2, 4, CEILING));

        List<BlockPlacement> hung = hang(room, out,
                config(1, 1, HangingPlacement.CENTRE, CHANDELIER, 0, 0), 1L);
        assertEquals(ceilingY + 1, hung.get(0).getY());
    }

    @Test
    void itStandsItsHeadroomOnWhateverIsOnTheFloor() {
        // A barrel under the middle: the air run starts above it, so a fixture that fit over bare
        // floor with no room to spare no longer fits.
        RoomData room = room(9, 9, 5);
        List<BlockPlacement> out = shell(room);
        out.add(new BlockPlacement(4, FLOOR_Y + 1, 4, "minecraft:barrel"));
        assertTrue(hang(room, out, config(1, 1, HangingPlacement.CENTRE, CHANDELIER, 0, 0), 1L)
                .isEmpty());
    }

    @Test
    void noTwoHangingsAreOrthogonalNeighbours() {
        RoomData room = room(13, 13, 8);
        for (long seed = 0; seed < 50; seed++) {
            List<BlockPlacement> hung = hang(room, shell(room),
                    config(6, 12, HangingPlacement.FREE, CHANDELIER, 0, 0), seed);
            Set<Coords2D> cells = hung.stream().map(p -> new Coords2D(p.getX(), p.getZ()))
                    .collect(Collectors.toSet());
            for (Coords2D c : cells) {
                assertFalse(cells.contains(new Coords2D(c.getX() + 1, c.getY())), "seed " + seed);
                assertFalse(cells.contains(new Coords2D(c.getX(), c.getY() + 1)), "seed " + seed);
            }
        }
    }

    @Test
    void theSameSeedHangsTheSameRoom() {
        RoomData room = room(11, 13, 8);
        HangingConfig config = config(1, 4, HangingPlacement.FREE, CHANDELIER, 0, 3);
        assertEquals(hang(room, shell(room), config, 42L).toString(),
                hang(room, shell(room), config, 42L).toString());
    }

    @Test
    void theChainIsTheSlotsToChoose() {
        RoomData room = room(9, 9, 9);
        HangingConfig config = new HangingConfig(1, 1, HangingPlacement.CENTRE,
                List.of(new HangingConfig.HangingVariant(CHANDELIER)), "dungeonblocks:rusty_chain",
                1, 1, mod.gottsch.forge.dungeons2.core.config.SizeGate.UNBOUNDED);
        List<BlockPlacement> hung = hang(room, shell(room), config, 1L);
        assertEquals("dungeonblocks:rusty_chain", hung.get(0).getBlockId());
    }
}
