package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.PotConfig;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pot placement. Pure data &mdash; entity ids stay as strings until spawn time, so no Minecraft
 * bootstrap is needed.
 *
 * <p>The invariants here are not cosmetic. A pot off a floor cell falls and shatters before anyone
 * sees it, two pots in one cell is a visible glitch, and a plan that is not a pure function of its
 * seed would break the chunk-clipping the piece relies on to spawn each pot exactly once.</p>
 */
class RoomPropGeneratorTest {

    private static final int FLOOR_Y = 60;

    private static PotConfig potConfig(int min, int max) {
        return new PotConfig(min, max, "dungeons2:pots/classic",
                List.of(new PotConfig.PotVariant("dungeonblocks:pot", 2),
                        new PotConfig.PotVariant("dungeonblocks:squat_clay_pot", 1)));
    }

    /** An 11x11 room at origin (10,10), tall enough to be unremarkable. */
    private static RoomData room() {
        return new RoomData(1, 10, 10, 11, 11, 8, RoomRole.NORMAL);
    }

    private static List<EntityPlacement> place(RoomData room, PotConfig config, long seed) {
        List<EntityPlacement> out = new ArrayList<>();
        RoomPropGenerator.placePots(room, FLOOR_Y, config, RandomSource.create(seed), out);
        return out;
    }

    @Test
    void placesTheRequestedNumberOfPots() {
        assertEquals(3, place(room(), potConfig(3, 3), 1L).size());
    }

    @Test
    void countStaysWithinTheConfiguredRange() {
        for (long seed = 0; seed < 60; seed++) {
            int size = place(room(), potConfig(1, 4), seed).size();
            assertTrue(size >= 1 && size <= 4, "count " + size + " outside [1,4] at seed " + seed);
        }
    }

    /** Zero pots is a legitimate authoring choice, not a reason to place one anyway. */
    @Test
    void aZeroCountPlacesNothing() {
        assertEquals(0, place(room(), potConfig(0, 0), 1L).size());
    }

    @Test
    void everyPotRestsOnAnInteriorFloorCell() {
        RoomData room = room();
        for (long seed = 0; seed < 40; seed++) {
            for (EntityPlacement pot : place(room, potConfig(4, 4), seed)) {
                int x = pot.getX() - room.getOriginX();
                int z = pot.getZ() - room.getOriginZ();
                assertTrue(x > 0 && x < room.getWidth() - 1, "pot in a wall column: " + pot);
                assertTrue(z > 0 && z < room.getDepth() - 1, "pot in a wall column: " + pot);
                // One above the floor plane: an entity's position is its feet, so this rests
                // exactly on the floor block. Anything higher falls and shatters.
                assertEquals(FLOOR_Y + 1, pot.getY(), "pot not resting on the floor: " + pot);
            }
        }
    }

    /** Pots hug the walls; one alone in an open floor reads as dropped, not placed. */
    @Test
    void everyPotTouchesAWall() {
        RoomData room = room();
        for (long seed = 0; seed < 40; seed++) {
            for (EntityPlacement pot : place(room, potConfig(4, 4), seed)) {
                int x = pot.getX() - room.getOriginX();
                int z = pot.getZ() - room.getOriginZ();
                assertTrue(x == 1 || x == room.getWidth() - 2 || z == 1 || z == room.getDepth() - 2,
                        "pot stranded in the middle of the floor: " + pot);
            }
        }
    }

    @Test
    void twoPotsNeverShareACell() {
        RoomData room = room();
        for (long seed = 0; seed < 40; seed++) {
            List<EntityPlacement> pots = place(room, potConfig(6, 6), seed);
            Set<String> cells = new HashSet<>();
            for (EntityPlacement pot : pots) {
                assertTrue(cells.add(pot.getX() + "," + pot.getZ()), "two pots in one cell: " + pot);
            }
        }
    }

    /**
     * The cell just inside a door is where a player walks through. Doorways themselves are on the
     * perimeter ring so they can never be chosen, but the cell behind one can.
     */
    @Test
    void noPotSitsInFrontOfADoorway() {
        RoomData room = room();
        // A door in the middle of the west wall (x = originX), and one in the north wall.
        room.getDoorways().add(new Coords2D(10, 15));
        room.getDoorways().add(new Coords2D(15, 10));

        Set<String> forbidden = Set.of("11,15", "15,11");
        for (long seed = 0; seed < 60; seed++) {
            for (EntityPlacement pot : place(room, potConfig(8, 8), seed)) {
                assertFalse(forbidden.contains(pot.getX() + "," + pot.getZ()),
                        "pot blocking a doorway at seed " + seed + ": " + pot);
            }
        }
    }

    /** A room with no interior gets no pots rather than pots inside its walls. */
    @Test
    void aRoomWithNoInteriorGetsNoPots() {
        assertEquals(0, place(new RoomData(1, 0, 0, 2, 9, 8, RoomRole.NORMAL), potConfig(3, 3), 1L).size());
    }

    /** Fewer eligible cells than the rolled count means fewer pots, never a doubled-up cell. */
    @Test
    void morePotsThanCellsIsCappedAtTheCellCount() {
        RoomData room = new RoomData(1, 0, 0, 3, 3, 8, RoomRole.NORMAL); // one interior cell
        assertEquals(1, place(room, potConfig(9, 9), 1L).size());
    }

    @Test
    void everyPotCarriesTheConfiguredLootTable() {
        for (EntityPlacement pot : place(room(), potConfig(4, 4), 5L)) {
            assertEquals("dungeons2:pots/classic", pot.getLootTable());
        }
    }

    /**
     * A zero seed means something else to PotEntity -- "roll the table when the pot breaks" rather
     * than "fix the contents now" -- so it must never be handed out by accident.
     */
    @Test
    void lootSeedsAreNonZeroAndVaryPerPot() {
        Set<Long> seeds = new HashSet<>();
        for (EntityPlacement pot : place(room(), potConfig(6, 6), 11L)) {
            assertNotEquals(0L, pot.getLootTableSeed(), "a zero loot seed changes the drop timing");
            seeds.add(pot.getLootTableSeed());
        }
        assertEquals(6, seeds.size(), "each pot should get its own loot seed");
    }

    @Test
    void onlyConfiguredVariantsAreUsed() {
        Set<String> allowed = Set.of("dungeonblocks:pot", "dungeonblocks:squat_clay_pot");
        for (long seed = 0; seed < 40; seed++) {
            for (EntityPlacement pot : place(room(), potConfig(4, 4), seed)) {
                assertTrue(allowed.contains(pot.getEntityId()), "unexpected variant: " + pot);
            }
        }
    }

    /**
     * The whole plan is a pure function of the seed. The piece spawns pots clipped to the chunk box
     * and relies on every per-chunk re-run producing an identical plan; if this drifted, pots would
     * duplicate on one side of a seam and vanish on the other.
     */
    @Test
    void theSamePlanIsProducedForTheSameSeed() {
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(place(room(), potConfig(1, 5), seed).toString(),
                    place(room(), potConfig(1, 5), seed).toString());
        }
    }

    // ---------- cells claimed by projecting wall trim ----------

    private static List<EntityPlacement> place(RoomData room, PotConfig config,
                                               Set<Coords2D> occupied, long seed) {
        List<EntityPlacement> out = new ArrayList<>();
        RoomPropGenerator.placePots(room, FLOOR_Y, config, occupied, RandomSource.create(seed), out);
        return out;
    }

    /**
     * A pot never stands in a cell the wall's projecting trim already took.
     *
     * <p>This is what lets a scheme carry pilasters <em>and</em> pots. A pilaster occupies an
     * inner-ring cell at exactly pot height for every strip on the wall &mdash; by construction, not
     * by an authoring slip &mdash; so without this the two could only ever be mutually exclusive.
     * A pot inside a block is invisible until someone walks into the room, which is why it is
     * checked rather than reasoned about.</p>
     */
    @Test
    void aPotNeverStandsInACellTakenByProjectingTrim() {
        RoomData room = room();
        // Every inner-ring cell along one wall, as a run of pilasters would claim.
        Set<Coords2D> occupied = new HashSet<>();
        for (int x = 11; x <= 19; x++) {
            occupied.add(new Coords2D(x, 11));
        }

        for (long seed = 0; seed < 60; seed++) {
            for (EntityPlacement pot : place(room, potConfig(4, 8), occupied, seed)) {
                Coords2D cell = new Coords2D(pot.getX(), pot.getZ());
                assertFalse(occupied.contains(cell),
                        "pot at " + cell + " is inside the wall trim (seed " + seed + ")");
            }
        }
    }

    /**
     * Trim taking most of the ring costs pots rather than breaking anything: the room gets fewer,
     * the same degradation a room with too few eligible cells always had.
     */
    @Test
    void trimOverMostOfTheRingLeavesFewerPotsRatherThanFailing() {
        RoomData room = room();
        Set<Coords2D> occupied = new HashSet<>(RoomPropGenerator.eligibleCells(room));
        Coords2D survivor = occupied.iterator().next();
        occupied.remove(survivor);

        List<EntityPlacement> pots = place(room, potConfig(4, 4), occupied, 7L);
        assertEquals(1, pots.size(), "one free cell means one pot, not four and not a crash");
        assertEquals(survivor, new Coords2D(pots.get(0).getX(), pots.get(0).getZ()));
    }

    /** With the whole ring taken there is nowhere to stand, and that is not an error. */
    @Test
    void trimOverTheWholeRingPlacesNoPots() {
        RoomData room = room();
        Set<Coords2D> occupied = new HashSet<>(RoomPropGenerator.eligibleCells(room));
        assertTrue(place(room, potConfig(4, 4), occupied, 3L).isEmpty());
    }

    /** The unoccupied overload is the old behaviour exactly -- nothing shifted for existing schemes. */
    @Test
    void anEmptyOccupiedSetChangesNothing() {
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(place(room(), potConfig(1, 5), seed).toString(),
                    place(room(), potConfig(1, 5), Set.of(), seed).toString());
        }
    }
}
