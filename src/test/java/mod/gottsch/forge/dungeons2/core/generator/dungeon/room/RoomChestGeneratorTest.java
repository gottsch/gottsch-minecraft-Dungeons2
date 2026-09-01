package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code chests} slot. Backlog #48.
 *
 * <p>Covers the three things that would be wrong in a way no other test would notice: a chest that
 * holds nothing, a chest a player can re-roll, and a chest standing where a pot is about to be
 * spawned.</p>
 */
class RoomChestGeneratorTest {

    private static final String TABLE = "dungeons2:chests/classic_common";

    private static RoomData room(int width, int depth) {
        RoomData room = new RoomData();
        room.setOriginX(0);
        room.setOriginZ(0);
        room.setWidth(width);
        room.setDepth(depth);
        room.setHeight(7);
        return room;
    }

    private static ChestConfig config(int min, int max) {
        return new ChestConfig(min, max, TABLE,
                List.of(new ChestConfig.ChestVariant("minecraft:chest", 1)));
    }

    private static List<BlockPlacement> place(ChestConfig config, long seed, Set<Coords2D> occupied) {
        List<BlockPlacement> out = new ArrayList<>();
        RoomChestGenerator.placeChests(room(9, 9), 64, config, occupied,
                RandomSource.create(seed), out);
        return out;
    }

    @Test
    void everyChestCarriesItsTableAndANonZeroSeed() {
        List<BlockPlacement> out = place(config(2, 2), 42L, Set.of());
        assertEquals(2, out.size());
        for (BlockPlacement chest : out) {
            assertNotNull(chest.getBlockEntityNbt(), "a chest with no block entity holds nothing");
            assertEquals(RoomChestGenerator.CHEST_ENTITY, chest.getBlockEntityNbt().getType());
            assertEquals(TABLE, chest.getBlockEntityNbt().getData().get(RoomChestGenerator.LOOT_TABLE));
            String seed = chest.getBlockEntityNbt().getData().get(RoomChestGenerator.LOOT_TABLE_SEED);
            assertNotNull(seed, "no LootTableSeed means the contents roll fresh on every open");
            assertNotEquals("0", seed, "seed 0 is vanilla's 'roll on open' -- re-rollable by reloading");
        }
    }

    /**
     * The facing is the whole reason a chest reads as furniture. A chest facing its own wall is the
     * failure this catches, and it is invisible to any test that only counts placements.
     */
    @Test
    void aChestFacesAwayFromTheWallItBacksOnto() {
        RoomData room = room(9, 9);
        // First interior row backs onto the north wall, so it must face south. And round the other
        // three sides.
        assertEquals("south", RoomChestGenerator.facingAwayFromWall(room, new Coords2D(4, 1)));
        assertEquals("north", RoomChestGenerator.facingAwayFromWall(room, new Coords2D(4, 7)));
        assertEquals("east", RoomChestGenerator.facingAwayFromWall(room, new Coords2D(1, 4)));
        assertEquals("west", RoomChestGenerator.facingAwayFromWall(room, new Coords2D(7, 4)));
    }

    @Test
    void theFacingIsWrittenOntoThePlacement() {
        for (BlockPlacement chest : place(config(3, 3), 7L, Set.of())) {
            String facing = chest.getProperties().get(RoomChestGenerator.FACING);
            assertNotNull(facing, "a chest with no facing property takes the block's default");
            assertTrue(Set.of("north", "south", "east", "west").contains(facing), facing);
        }
    }

    /**
     * A chest is solid, so a pot spawned in the same cell falls and shatters. The claimed cells are
     * the only thing that stops that, since the props are placed afterwards.
     */
    @Test
    void chestsClaimTheirCellsAndKeepOutOfOccupiedOnes() {
        List<BlockPlacement> out = new ArrayList<>();
        RoomData room = room(9, 9);
        Set<Coords2D> occupied = new HashSet<>(RoomPropGenerator.eligibleCells(room));
        Coords2D free = occupied.iterator().next();
        occupied.remove(free);

        Set<Coords2D> claimed = RoomChestGenerator.placeChests(room, 64, config(4, 4), occupied,
                RandomSource.create(11L), out);

        assertEquals(1, out.size(), "only one cell was left free, so only one chest fits");
        assertEquals(Set.of(free), claimed);
        assertEquals(free.getX(), out.get(0).getX());
        assertEquals(free.getY(), out.get(0).getZ());
    }

    @Test
    void aRoomWithNoEligibleCellsGetsNoChests() {
        RoomData room = room(9, 9);
        Set<Coords2D> everything = new HashSet<>(RoomPropGenerator.eligibleCells(room));
        List<BlockPlacement> out = new ArrayList<>();
        assertTrue(RoomChestGenerator.placeChests(room, 64, config(2, 2), everything,
                RandomSource.create(3L), out).isEmpty());
        assertTrue(out.isEmpty());
    }

    /**
     * {@code min_count} defaults to 0, so the commonest authored shape is "sometimes". A slot that
     * always fired would make finding a chest mean nothing.
     */
    @Test
    void aZeroMinimumSometimesPlacesNothing() {
        int empty = 0;
        for (int i = 0; i < 40; i++) {
            // SPREAD seeds, not 0..39. Sequential small seeds correlate hard on their first draw --
            // the trap already recorded for RandomSource.create(0,1,2,...) -- and the first draw
            // here IS the count. With 0..39 this rolled 1 all forty times and read as a slot that
            // always fires, which is a bug in the test, not in the slot.
            if (place(config(0, 1), 0xD2_0BADC0DEL + i * 7919L, Set.of()).isEmpty()) {
                empty++;
            }
        }
        assertTrue(empty > 0, "a 0..1 range never rolled 0 in 40 seeds");
        assertTrue(empty < 40, "a 0..1 range never rolled 1 in 40 seeds");
    }

    @Test
    void anInvertedCountRangeIsClampedRatherThanExploding() {
        assertEquals(3, new ChestConfig(3, 1, TABLE, List.of()).clampedMaxCount());
    }

    @Test
    void aSlotWithNoVariantsPlacesNothing() {
        List<BlockPlacement> out = new ArrayList<>();
        assertTrue(RoomChestGenerator.placeChests(room(9, 9), 64,
                new ChestConfig(1, 1, TABLE, List.of()), Set.of(),
                RandomSource.create(5L), out).isEmpty());
        assertTrue(out.isEmpty());
    }

    /** The gate is the element-level one: it decides whether the slot draws, not whether the scheme wins. */
    @Test
    void theGateIsCarriedOnTheSlot() {
        ChestConfig gated = new ChestConfig(1, 1, TABLE,
                List.of(new ChestConfig.ChestVariant("minecraft:chest", 1)),
                new SizeGate(0, 9, Optional.empty(), Optional.empty()));
        assertTrue(gated.gate().fits(9, 9, 7));
        assertFalse(gated.gate().fits(7, 7, 7), "a 7-wide room is below the slot's min_size");
    }
}
