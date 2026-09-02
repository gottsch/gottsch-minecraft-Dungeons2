package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.PropConfig;
import mod.gottsch.forge.dungeons2.core.config.PropConfig.PropPlacement;
import mod.gottsch.forge.dungeons2.core.config.PropConfig.PropVariant;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code props} slot's placement (#73). Pure data &mdash; a block id stays a string until the
 * piece resolves it, so no Minecraft bootstrap is needed.
 *
 * <p>What is actually worth checking here is not "did it place something" but the four things that
 * would each fail silently in a finished dungeon: a prop in a doorway (which a player walks round
 * without ever calling it a bug), two props in one cell (the second overwrites the first), a prop in
 * a cell something else already claimed, and a plan that is not a pure function of its seed &mdash;
 * which would place different furniture in each chunk a room straddles.</p>
 */
class RoomFurnitureGeneratorTest {

    private static final int FLOOR_Y = 60;

    private static PropConfig props(int min, int max, PropPlacement placement) {
        return new PropConfig(min, max, placement,
                List.of(new PropVariant("minecraft:barrel", 2),
                        new PropVariant("dungeonblocks:crate", 1)));
    }

    /** An 11x11 room at origin (10,10), with one door in the middle of each of two walls. */
    private static RoomData room() {
        RoomData room = new RoomData(1, 10, 10, 11, 11, 8, RoomRole.NORMAL);
        room.setDoorways(new ArrayList<>(List.of(
                new Coords2D(15, 10),   // north wall
                new Coords2D(10, 15))));// west wall
        return room;
    }

    private static List<BlockPlacement> place(RoomData room, PropConfig config,
                                              Set<Coords2D> occupied, long seed) {
        List<BlockPlacement> out = new ArrayList<>();
        RoomFurnitureGenerator.placeProps(room, FLOOR_Y, config, occupied,
                RandomSource.create(seed), out);
        return out;
    }

    private static List<BlockPlacement> place(RoomData room, PropConfig config, long seed) {
        return place(room, config, Set.of(), seed);
    }

    private static Set<Coords2D> cellsOf(List<BlockPlacement> placements) {
        return placements.stream().map(p -> new Coords2D(p.getX(), p.getZ()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // ---- counts ---------------------------------------------------------------------------------

    @Test
    void placesTheRequestedNumberOfProps() {
        assertEquals(3, place(room(), props(3, 3, PropPlacement.AGAINST_WALL), 1L).size());
    }

    @Test
    void countStaysWithinTheConfiguredRange() {
        for (long seed = 0; seed < 60; seed++) {
            int size = place(room(), props(1, 4, PropPlacement.AGAINST_WALL), seed).size();
            assertTrue(size >= 1 && size <= 4, "count " + size + " outside [1,4] at seed " + seed);
        }
    }

    /** Zero props is a legitimate authoring choice, not a reason to place one anyway. */
    @Test
    void aZeroCountPlacesNothing() {
        assertTrue(place(room(), props(0, 0, PropPlacement.AGAINST_WALL), 1L).isEmpty());
    }

    /**
     * A count above what the placement can offer costs props, not correctness. {@code corner} has
     * four cells however big the room is, so this is the ordinary case rather than an edge one.
     */
    @Test
    void cornerPlacementCannotExceedFourHoweverManyAreAskedFor() {
        List<BlockPlacement> placed = place(room(), props(9, 9, PropPlacement.CORNER), 5L);
        assertEquals(4, placed.size());
        assertEquals(4, cellsOf(placed).size(), "the four corners have to be four distinct cells");
    }

    // ---- where they land ------------------------------------------------------------------------

    @Test
    void everyPropRestsOnAnInteriorFloorCell() {
        RoomData room = room();
        for (PropPlacement placement : PropPlacement.values()) {
            for (long seed = 0; seed < 30; seed++) {
                for (BlockPlacement prop : place(room, props(2, 4, placement), seed)) {
                    int x = prop.getX() - room.getOriginX();
                    int z = prop.getZ() - room.getOriginZ();
                    assertTrue(x > 0 && x < room.getWidth() - 1,
                            placement + " prop in a wall column: " + prop);
                    assertTrue(z > 0 && z < room.getDepth() - 1,
                            placement + " prop in a wall column: " + prop);
                    // One above the floor plane: standing ON the floor block, the same row the
                    // chests, spawners and pots use.
                    assertEquals(FLOOR_Y + 1, prop.getY(),
                            placement + " prop not standing on the floor: " + prop);
                }
            }
        }
    }

    /**
     * No placement rule may offer the cell inside a doorway &mdash; including {@code free}, which
     * offers the whole interior, and {@code flanking_door}, which is defined relative to it.
     */
    @Test
    void noPlacementEverStandsInADoorwayApproach() {
        RoomData room = room();
        Set<Coords2D> approaches = RoomInterior.cellsInsideDoorways(room);
        for (PropPlacement placement : PropPlacement.values()) {
            for (long seed = 0; seed < 40; seed++) {
                for (Coords2D cell : cellsOf(place(room, props(4, 6, placement), seed))) {
                    assertFalse(approaches.contains(cell),
                            placement + " put a prop in a doorway at " + cell + " (seed " + seed + ")");
                }
            }
        }
    }

    @Test
    void againstWallOffersExactlyTheCellsThePotsSlotUses() {
        RoomData room = room();
        assertEquals(RoomPropGenerator.eligibleCells(room, Set.of()),
                RoomFurnitureGenerator.candidates(room, PropPlacement.AGAINST_WALL, Set.of()));
    }

    @Test
    void cornerOffersTheFourInteriorCorners() {
        assertEquals(Set.of(new Coords2D(11, 11), new Coords2D(19, 11),
                        new Coords2D(11, 19), new Coords2D(19, 19)),
                new HashSet<>(RoomFurnitureGenerator.candidates(room(), PropPlacement.CORNER, Set.of())));
    }

    /**
     * The flanks are the two cells beside a door's approach, along the wall &mdash; never the
     * approach itself, and never the cell a step further into the room.
     */
    @Test
    void flankingDoorOffersTheCellsEitherSideOfEachApproach() {
        // North door at (15,10) approaches (15,11); west door at (10,15) approaches (11,15).
        assertEquals(Set.of(new Coords2D(14, 11), new Coords2D(16, 11),
                        new Coords2D(11, 14), new Coords2D(11, 16)),
                new HashSet<>(RoomFurnitureGenerator.candidates(room(), PropPlacement.FLANKING_DOOR,
                        Set.of())));
    }

    @Test
    void freeOffersTheWholeInteriorLessTheDoorwayApproaches() {
        RoomData room = room();
        List<Coords2D> free = RoomFurnitureGenerator.candidates(room, PropPlacement.FREE, Set.of());
        // 9x9 interior, less the one approach cell each of the two doors has.
        assertEquals(81 - 2, free.size());
        assertTrue(free.containsAll(
                RoomFurnitureGenerator.candidates(room, PropPlacement.AGAINST_WALL, Set.of())),
                "the ring is part of the interior, so `free` has to be a superset of `against_wall`");
    }

    /**
     * A room too narrow to have four distinct corners gets the ones it has, once each. Without the
     * dedupe two draws would land on the same cell and the second block would silently replace the
     * first.
     */
    @Test
    void aThreeWideRoomHasTwoCornersRatherThanFourOverlappingOnes() {
        RoomData narrow = new RoomData(2, 0, 0, 3, 9, 8, RoomRole.NORMAL);
        List<Coords2D> corners =
                RoomFurnitureGenerator.candidates(narrow, PropPlacement.CORNER, Set.of());
        assertEquals(List.of(new Coords2D(1, 1), new Coords2D(1, 7)), corners);
    }

    // ---- claiming, and giving way ---------------------------------------------------------------

    /** Every cell a prop took comes back, so the pots placed afterwards can avoid it. */
    @Test
    void thePlacedCellsAreReturned() {
        List<BlockPlacement> out = new ArrayList<>();
        RoomData room = room();
        Set<Coords2D> used = RoomFurnitureGenerator.placeProps(room, FLOOR_Y,
                props(3, 3, PropPlacement.AGAINST_WALL), Set.of(), RandomSource.create(11L), out);
        assertEquals(cellsOf(out), used);
        assertEquals(3, used.size());
    }

    /**
     * A prop never stands in a cell architecture already claimed. The failure this prevents is
     * invisible from outside: a barrel inside a pilaster is simply not there.
     */
    @Test
    void aPropNeverStandsInAnOccupiedCell() {
        RoomData room = room();
        Set<Coords2D> occupied = new HashSet<>();
        for (int x = 11; x <= 19; x++) {
            occupied.add(new Coords2D(x, 11));
        }
        for (PropPlacement placement : PropPlacement.values()) {
            for (long seed = 0; seed < 40; seed++) {
                for (Coords2D cell : cellsOf(place(room, props(4, 6, placement), occupied, seed))) {
                    assertFalse(occupied.contains(cell),
                            placement + " put a prop at " + cell + " inside the wall trim");
                }
            }
        }
    }

    /** Nowhere to stand is not an error; it is a room that gets no furniture. */
    @Test
    void aFullyOccupiedRingPlacesNoPropsAgainstTheWall() {
        RoomData room = room();
        Set<Coords2D> occupied =
                new HashSet<>(RoomPropGenerator.eligibleCells(room, Set.of()));
        assertTrue(place(room, props(4, 4, PropPlacement.AGAINST_WALL), occupied, 3L).isEmpty());
    }

    /**
     * {@code corner} with its corners taken places nothing rather than spilling into the middle of
     * the floor. The spill would be the arrangement the author picked {@code corner} to avoid, and
     * nothing downstream could tell it from a deliberate one.
     */
    @Test
    void cornerWithItsCornersTakenDoesNotFallBackToTheRestOfTheRoom() {
        RoomData room = room();
        Set<Coords2D> occupied = new HashSet<>(
                RoomFurnitureGenerator.candidates(room, PropPlacement.CORNER, Set.of()));
        assertTrue(place(room, props(1, 4, PropPlacement.CORNER), occupied, 9L).isEmpty());
    }

    // ---- variants and facing --------------------------------------------------------------------

    @Test
    void onlyDeclaredVariantsArePlaced() {
        for (long seed = 0; seed < 40; seed++) {
            for (BlockPlacement prop : place(room(), props(3, 3, PropPlacement.FREE), seed)) {
                assertTrue(prop.getBlockId().equals("minecraft:barrel")
                                || prop.getBlockId().equals("dungeonblocks:crate"),
                        "undeclared variant " + prop.getBlockId());
            }
        }
    }

    /** An oriented prop against a wall faces into the room, not at the masonry. */
    @Test
    void anOrientedPropOnTheRingFacesAwayFromItsWall() {
        RoomData room = room();
        for (long seed = 0; seed < 40; seed++) {
            for (BlockPlacement prop : place(room, props(4, 6, PropPlacement.AGAINST_WALL), seed)) {
                Coords2D cell = new Coords2D(prop.getX(), prop.getZ());
                assertEquals(RoomChestGenerator.facingAwayFromWall(room, cell),
                        prop.getProperties().get("facing"),
                        "prop at " + cell + " faces its own wall");
            }
        }
    }

    /** {@code oriented: false} writes no property at all, so the block keeps its default state. */
    @Test
    void anUnorientedPropCarriesNoFacing() {
        PropConfig config = new PropConfig(3, 3, PropPlacement.AGAINST_WALL,
                List.of(new PropVariant("minecraft:barrel", 1, false)));
        for (BlockPlacement prop : place(room(), config, 4L)) {
            assertTrue(prop.getProperties().isEmpty(),
                    "an unoriented prop should carry no properties: " + prop);
        }
    }

    /**
     * Props out in the open floor do not all face the same way. A constant fallback would line a
     * hall's furniture up in a grid, which reads as placed by a machine.
     */
    @Test
    void orientedPropsAwayFromAnyWallVaryTheirFacing() {
        RoomData room = new RoomData(3, 0, 0, 21, 21, 8, RoomRole.NORMAL);
        Set<String> facings = new HashSet<>();
        for (long seed = 0; seed < 60; seed++) {
            for (BlockPlacement prop : place(room, props(4, 4, PropPlacement.FREE), seed)) {
                boolean onRing = prop.getX() == 1 || prop.getX() == 19
                        || prop.getZ() == 1 || prop.getZ() == 19;
                if (!onRing) {
                    facings.add(prop.getProperties().get("facing"));
                }
            }
        }
        assertEquals(Set.of("north", "east", "south", "west"), facings);
    }

    // ---- determinism ----------------------------------------------------------------------------

    /**
     * The same seed gives the same plan, and different seeds generally do not. The first half is
     * load-bearing: a piece runs once per overlapping chunk and the consumer clips each run to the
     * chunk box, so a plan that varied between runs would place furniture twice on a seam.
     */
    @Test
    void thePlanIsAPureFunctionOfItsSeed() {
        RoomData room = room();
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(place(room, props(2, 5, PropPlacement.FREE), seed).toString(),
                    place(room, props(2, 5, PropPlacement.FREE), seed).toString());
        }
        assertNotEquals(place(room, props(2, 5, PropPlacement.FREE), 1L).toString(),
                place(room, props(2, 5, PropPlacement.FREE), 2L).toString());
    }

    @Test
    void twoPropsNeverShareACell() {
        RoomData room = room();
        for (PropPlacement placement : PropPlacement.values()) {
            for (long seed = 0; seed < 40; seed++) {
                List<BlockPlacement> placed = place(room, props(4, 8, placement), seed);
                assertEquals(placed.size(), cellsOf(placed).size(),
                        placement + " placed two props in one cell at seed " + seed);
            }
        }
    }
}
