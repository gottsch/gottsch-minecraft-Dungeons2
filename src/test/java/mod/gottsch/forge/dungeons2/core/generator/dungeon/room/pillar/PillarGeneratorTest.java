package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar;

import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry.PillarEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Free-standing pillars: the grid layout's arithmetic, and the generator that turns a footprint into
 * columns.
 *
 * <p>The layout tests carry most of the weight here. Every fault the projecting wall strips shipped
 * with was an arithmetic one &mdash; centred on the wrong window, phase drifting with room size,
 * corners double-claimed &mdash; and every one of them looked like "the generator sometimes does
 * something odd" rather than like a bug, because a lattice that is slightly wrong still draws. So
 * the positions are asserted across a sweep of room sizes rather than at one convenient size.
 */
class PillarGeneratorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String PILLAR = "minecraft:stone_bricks";

    // ---------- the grid's arithmetic ----------

    @Test
    void positionsAreCentredWithinTheInsetWindow() {
        // interior 11, spacing 4, inset 2 -> usable [2..8], 2 columns spanning 4, centred at 3 and 7
        assertArray(new int[]{3, 7}, GridPillarPatternProvider.positions(11, 4, 2));
    }

    /**
     * The fault the pilasters shipped with, in its pillar form: stepping from the origin instead of
     * centring makes the lattice crowd one wall and leave a gap at the other, and which rooms it
     * happens in depends on how the division falls -- so it is invisible at any single test size.
     */
    @Test
    void everyRoomSizeGetsASymmetricLattice() {
        for (int size = 5; size <= 40; size++) {
            final int interior = size;
            int[] positions = GridPillarPatternProvider.positions(interior, 4, 2);
            if (positions.length == 0) {
                continue;
            }
            final int leading = positions[0];
            final int trailing = interior - 1 - positions[positions.length - 1];
            assertTrue(Math.abs(leading - trailing) <= 1,
                    () -> "interior " + interior + ": lattice is off-centre, "
                            + leading + " clear at one end and " + trailing + " at the other");
        }
    }

    @Test
    void everyRoomSizeKeepsTheLatticeInsideItsInset() {
        for (int interior = 5; interior <= 40; interior++) {
            for (int inset = 0; inset <= 3; inset++) {
                for (int position : GridPillarPatternProvider.positions(interior, 4, inset)) {
                    assertTrue(position >= inset && position <= interior - 1 - inset,
                            "interior " + interior + " inset " + inset + ": column at " + position
                                    + " is outside the window");
                }
            }
        }
    }

    /** A window too small for even one column draws none rather than forcing one against a wall. */
    @Test
    void aWindowTooSmallDrawsNoColumns() {
        assertEquals(0, GridPillarPatternProvider.positions(4, 4, 2).length);
        assertEquals(0, GridPillarPatternProvider.positions(3, 4, 2).length);
    }

    /** One column per axis -- a single pillar in the middle -- is a legitimate outcome, not a floor. */
    @Test
    void aSmallWindowDrawsASingleCentredColumn() {
        assertArray(new int[]{2}, GridPillarPatternProvider.positions(5, 4, 2));
    }

    /** A square room's lattice must be square: the two axes may not round differently. */
    @Test
    void aSquareRoomGetsASquareLattice() {
        for (int interior = 5; interior <= 40; interior++) {
            Set<Coords2D> cells = new GridPillarPatternProvider(4, 2).footprint(interior, interior);
            Set<Coords2D> transposed = cells.stream()
                    .map(c -> new Coords2D(c.getY(), c.getX()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            assertEquals(keys(cells), keys(transposed),
                    "interior " + interior + ": the lattice is not symmetric under transpose");
        }
    }

    // ---------- the generator ----------

    private static RoomData room(int width, int depth, int height) {
        return new RoomData(1, 0, 0, width, depth, height, RoomRole.NORMAL);
    }

    private static List<BlockPlacement> build(RoomData room, PillarEntry entry, BasicPillarGenerator gen) {
        List<BlockPlacement> out = new ArrayList<>();
        gen.withPillarLayouts(PillarPatternSelector.toLayouts(
                        new PillarPatternEntry(List.of(entry))))
                .build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        return out;
    }

    @Test
    void aColumnSpansFloorPlusOneToCeilingMinusOne() {
        RoomData room = room(13, 13, 8); // interior rows 6: y 61..66
        List<BlockPlacement> out = build(room, new PillarEntry("grid", PILLAR), new BasicPillarGenerator());

        Set<Integer> ys = out.stream().map(BlockPlacement::getY).collect(Collectors.toSet());
        assertEquals(Set.of(61, 62, 63, 64, 65, 66), ys,
                "a column fills exactly the interior air rows the hollow step cleared");
        assertEquals(6, out.size() / distinctCells(out).size(), "every column is the full height");
    }

    @Test
    void theBaseAndCapRowsTakeTheirOwnBlocks() {
        PillarEntry entry = new PillarEntry("grid", PILLAR,
                Optional.of("minecraft:polished_andesite"), Optional.of("minecraft:chiseled_stone_bricks"),
                4, 2, Map.of(), Optional.of(Map.of("base", "up")), Optional.of(Map.of("base", "down")),
                SizeGate.UNBOUNDED);
        List<BlockPlacement> out = build(room(13, 13, 8), entry, new BasicPillarGenerator());

        BlockPlacement base = atY(out, 61);
        BlockPlacement shaft = atY(out, 63);
        BlockPlacement cap = atY(out, 66);
        assertEquals("minecraft:polished_andesite", base.getBlockId());
        assertEquals("minecraft:stone_bricks", shaft.getBlockId());
        assertEquals("minecraft:chiseled_stone_bricks", cap.getBlockId());

        // The counter-intuitive half, and the one that was authored inverted on the wall strips:
        // the row standing ON THE FLOOR wants base=up, the capital under the ceiling base=down.
        assertEquals("up", base.getProperties().get("base"));
        assertEquals("down", cap.getProperties().get("base"));
    }

    /** A two-row column is all plinth and capital. That is what a short column looks like. */
    @Test
    void aTwoRowColumnIsBaseAndCapWithNoShaft() {
        PillarEntry entry = new PillarEntry("grid", PILLAR,
                Optional.of("minecraft:polished_andesite"), Optional.of("minecraft:chiseled_stone_bricks"),
                4, 2, Map.of(), Optional.empty(), Optional.empty(), SizeGate.UNBOUNDED);
        List<BlockPlacement> out = build(room(13, 13, 4), entry, new BasicPillarGenerator());

        assertTrue(out.stream().noneMatch(bp -> PILLAR.equals(bp.getBlockId())),
                "there is no shaft row to draw");
        assertEquals("minecraft:polished_andesite", atY(out, 61).getBlockId());
        assertEquals("minecraft:chiseled_stone_bricks", atY(out, 62).getBlockId());
    }

    /**
     * Dropped WHOLE, not clipped: removing only the two door rows leaves the rest of the column
     * hanging over the opening. Same rule a projecting wall strip follows at a doorway.
     */
    @Test
    void aColumnOnADoorwayApproachIsDroppedWhole() {
        RoomData room = room(13, 13, 8);
        List<BlockPlacement> before = build(room, new PillarEntry("grid", PILLAR), new BasicPillarGenerator());
        Set<String> cells = distinctCells(before);

        // Put a door directly outside one of the columns the lattice placed.
        Coords2D column = distinctCoords(before).iterator().next();
        room.getDoorways().add(new Coords2D(column.getX(), column.getY() - 1));

        List<BlockPlacement> after = build(room, new PillarEntry("grid", PILLAR), new BasicPillarGenerator());
        Set<String> remaining = distinctCells(after);

        assertEquals(cells.size() - 1, remaining.size(), "exactly one column went");
        assertFalse(remaining.contains(key(column)), "and it was the one in the doorway");
        assertTrue(after.stream().noneMatch(bp -> bp.getX() == column.getX() && bp.getZ() == column.getY()),
                "no part of it survives -- not even the rows above the door");
    }

    /** The cells reported to the prop pass are what was BUILT, doorway drops and all. */
    @Test
    void occupiedCellsReportWhatWasActuallyBuilt() {
        RoomData room = room(13, 13, 8);
        BasicPillarGenerator gen = new BasicPillarGenerator();
        List<BlockPlacement> out = build(room, new PillarEntry("grid", PILLAR), gen);

        assertEquals(distinctCells(out), gen.occupiedFloorCells().stream()
                .map(PillarGeneratorTest::key).collect(Collectors.toSet()));
    }

    /** Two overlapping layouts emit one column, not two writes into the same cell. */
    @Test
    void overlappingLayoutsDoNotEmitACellTwice() {
        PillarPatternEntry entry = new PillarPatternEntry(List.of(
                new PillarEntry("grid", PILLAR),
                new PillarEntry("grid", "minecraft:polished_andesite")));
        List<BlockPlacement> out = new ArrayList<>();
        new BasicPillarGenerator().withPillarLayouts(PillarPatternSelector.toLayouts(entry))
                .build(room(13, 13, 8), 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        assertEquals(out.size(), out.stream()
                        .map(bp -> bp.getX() + "," + bp.getY() + "," + bp.getZ()).distinct().count(),
                "the same cell must not be written twice");
        // First layout wins a shared cell, since the second is skipped rather than overwriting.
        assertTrue(out.stream().allMatch(bp -> PILLAR.equals(bp.getBlockId())));
    }

    @Test
    void aRoomWithNoInteriorGetsNoColumns() {
        assertEquals(0, build(room(3, 3, 3), new PillarEntry("grid", PILLAR),
                new BasicPillarGenerator()).size(), "3-high room has one interior row and one cell");
        assertEquals(0, build(room(2, 9, 8), new PillarEntry("grid", PILLAR),
                new BasicPillarGenerator()).size(), "no interior at all");
    }

    // ---------- the selector ----------

    @Test
    void anUnrecognizedTypeIsSkippedAndTheRestStillDraw() {
        List<PillarLayout> layouts = PillarPatternSelector.toLayouts(new PillarPatternEntry(List.of(
                new PillarEntry("obelisks", PILLAR),
                new PillarEntry("grid", PILLAR))));
        assertEquals(1, layouts.size());
    }

    @Test
    void anUnresolvableShaftBlockDropsThatLayoutOnly() {
        List<PillarLayout> layouts = PillarPatternSelector.toLayouts(new PillarPatternEntry(List.of(
                new PillarEntry("grid", "minecraft:not_a_real_block"),
                new PillarEntry("grid", PILLAR))));
        assertEquals(1, layouts.size());
        assertEquals(PILLAR, layouts.get(0).entry().block());
    }

    @Test
    void aGatedPatternDropsOutOfSmallRooms() {
        PillarPatternEntry entry = new PillarPatternEntry(List.of(
                new PillarEntry("grid", PILLAR, Optional.empty(), Optional.empty(), 4, 2, Map.of(),
                        Optional.empty(), Optional.empty(),
                        new SizeGate(0, 11, Optional.empty(), Optional.empty()))));

        assertEquals(0, PillarPatternSelector.layoutsFor(Optional.of(entry), 9, 9, 8).size());
        assertEquals(1, PillarPatternSelector.layoutsFor(Optional.of(entry), 13, 13, 8).size());
    }

    @Test
    void anAbsentSlotDrawsNothing() {
        assertEquals(0, PillarPatternSelector.layoutsFor(Optional.empty(), 13, 13, 8).size());
    }

    // ---------- helpers ----------

    private static void assertArray(int[] expected, int[] actual) {
        assertEquals(java.util.Arrays.toString(expected), java.util.Arrays.toString(actual));
    }

    private static String key(Coords2D c) {
        return c.getX() + "," + c.getY();
    }

    private static Set<String> keys(Set<Coords2D> cells) {
        return cells.stream().map(PillarGeneratorTest::key).collect(Collectors.toSet());
    }

    private static Set<String> distinctCells(List<BlockPlacement> out) {
        return out.stream().map(bp -> bp.getX() + "," + bp.getZ()).collect(Collectors.toSet());
    }

    private static Set<Coords2D> distinctCoords(List<BlockPlacement> out) {
        return out.stream().map(bp -> new Coords2D(bp.getX(), bp.getZ()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static BlockPlacement atY(List<BlockPlacement> out, int y) {
        return out.stream().filter(bp -> bp.getY() == y).findFirst().orElseThrow();
    }
}
