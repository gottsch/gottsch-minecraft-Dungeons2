package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar;

import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry.PillarEntry;
import mod.gottsch.forge.dungeons2.core.config.pillar.ColonnadePillarLayout;
import mod.gottsch.forge.dungeons2.core.config.pillar.GridPillarLayout;
import mod.gottsch.forge.dungeons2.core.config.pillar.QuartetPillarLayout;
import mod.gottsch.forge.dungeons2.core.config.PitPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.pit.CentrePitShape;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.RoomPitGenerator;
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

    // ---------- the colonnade's axis ----------

    /** Two rows, and they run along the LONGER axis -- that is the whole point of the layout. */
    @Test
    void aColonnadeRunsAlongTheLongerAxis() {
        // interior 15 x 7: the run is along X, the two rows are at fixed Z.
        Set<Coords2D> wide = new ColonnadePillarPatternProvider(4, 2).footprint(15, 7);
        assertEquals(2, wide.stream().map(Coords2D::getY).distinct().count(),
                "two rows across the short axis");
        assertTrue(wide.stream().map(Coords2D::getX).distinct().count() > 2,
                "and more than two positions along the long one");

        // Transposed room: the colonnade must transpose with it.
        Set<Coords2D> deep = new ColonnadePillarPatternProvider(4, 2).footprint(7, 15);
        assertEquals(2, deep.stream().map(Coords2D::getX).distinct().count());
        assertTrue(deep.stream().map(Coords2D::getY).distinct().count() > 2);
        assertEquals(wide.size(), deep.size(), "same room, turned 90 degrees, same column count");
    }

    /**
     * The axis must come from the room's proportions and never from chance. A piece renders once per
     * overlapping chunk and every run has to agree; a coin flip here would give a room a different
     * colonnade per chunk and tear it along the seam.
     */
    @Test
    void theAxisIsDeterministic() {
        Set<Coords2D> first = new ColonnadePillarPatternProvider(4, 2).footprint(17, 9);
        for (int i = 0; i < 20; i++) {
            assertEquals(keys(first), keys(new ColonnadePillarPatternProvider(4, 2).footprint(17, 9)));
        }
        assertEquals(2, first.stream().map(Coords2D::getY).distinct().count(),
                "the longer axis is X, so the rows sit at fixed Z");
    }

    /** The rows leave an aisle. A colonnade with no gap between its rows is a wall. */
    @Test
    void theRowsAlwaysLeaveAClearAisle() {
        for (int cross = 3; cross <= 20; cross++) {
            for (int inset = 0; inset <= 3; inset++) {
                Set<Coords2D> cells = new ColonnadePillarPatternProvider(4, inset).footprint(20, cross);
                if (cells.isEmpty()) {
                    continue;
                }
                List<Integer> rows = cells.stream().map(Coords2D::getY).distinct().sorted().toList();
                assertEquals(2, rows.size(), "cross " + cross + " inset " + inset);
                assertTrue(rows.get(1) - rows.get(0) >= 2,
                        "cross " + cross + " inset " + inset + ": rows at " + rows + " leave no aisle");
            }
        }
    }

    /**
     * The layout is an axis, so a room without one gets nothing. In a square room the two rows sit
     * against opposite walls with a void between and read as a grid missing its middle row -- grid
     * is what square rooms are for.
     */
    @Test
    void aRoomThatIsNotElongatedGetsNoColonnade() {
        assertTrue(new ColonnadePillarPatternProvider(4, 2).footprint(13, 13).isEmpty(),
                "square: declines");
        assertTrue(new ColonnadePillarPatternProvider(4, 2).footprint(11, 9).isEmpty(),
                "11x9 is only one bay off square: declines");
        assertFalse(new ColonnadePillarPatternProvider(4, 2).footprint(13, 7).isEmpty(),
                "13x7 is a hall: draws");
        assertFalse(new ColonnadePillarPatternProvider(4, 2).footprint(15, 9).isEmpty(),
                "15x9 is a hall: draws");
    }

    /** Declining is symmetric: turning a square room 90 degrees does not make it a hall. */
    @Test
    void decliningDoesNotDependOnWhichAxisIsWhich() {
        for (int a = 5; a <= 25; a++) {
            for (int b = 5; b <= 25; b++) {
                assertEquals(new ColonnadePillarPatternProvider(4, 2).footprint(a, b).isEmpty(),
                        new ColonnadePillarPatternProvider(4, 2).footprint(b, a).isEmpty(),
                        "interior " + a + "x" + b + " disagrees with its transpose");
            }
        }
    }

    @Test
    void aRoomTooNarrowForAnAisleGetsNoColonnade() {
        // inset 2 needs a cross axis of at least 7 to leave a gap; 6 must draw nothing.
        assertEquals(7, ColonnadePillarPatternProvider.minimumCrossAxis(2));
        assertTrue(new ColonnadePillarPatternProvider(4, 2).footprint(20, 6).isEmpty());
        assertFalse(new ColonnadePillarPatternProvider(4, 2).footprint(20, 7).isEmpty());
    }

    /** The run inherits the grid's centring rather than restating the arithmetic. */
    @Test
    void theRunIsCentredAlongItsAxis() {
        for (int run = 5; run <= 40; run++) {
            final int length = run;
            Set<Coords2D> cells = new ColonnadePillarPatternProvider(4, 2).footprint(length, 9);
            if (cells.isEmpty()) {
                continue;
            }
            int lowest = cells.stream().mapToInt(Coords2D::getX).min().orElseThrow();
            int highest = cells.stream().mapToInt(Coords2D::getX).max().orElseThrow();
            assertTrue(Math.abs(lowest - (length - 1 - highest)) <= 1,
                    () -> "run " + length + ": colonnade is off-centre along its axis");
        }
    }

    // ---------- the quartet ----------

    @Test
    void aQuartetIsAlwaysFourColumnsMarkingASquare() {
        for (int iw = 5; iw <= 30; iw++) {
            for (int idp = 5; idp <= 30; idp++) {
                Set<Coords2D> cells = new QuartetPillarPatternProvider(6, 2).footprint(iw, idp);
                if (cells.isEmpty()) {
                    continue;
                }
                assertEquals(4, cells.size(), "interior " + iw + "x" + idp);
                int spanX = cells.stream().mapToInt(Coords2D::getX).max().orElseThrow()
                        - cells.stream().mapToInt(Coords2D::getX).min().orElseThrow();
                int spanZ = cells.stream().mapToInt(Coords2D::getY).max().orElseThrow()
                        - cells.stream().mapToInt(Coords2D::getY).min().orElseThrow();
                assertEquals(spanX, spanZ,
                        "interior " + iw + "x" + idp + ": a quartet marks a SQUARE, not a rectangle");
            }
        }
    }

    /**
     * The property that makes this a distinct layout rather than a sparse grid: the square marks one
     * centre and <strong>does not grow with the room</strong>, so a bigger room gets the same four
     * columns with more space round them.
     */
    @Test
    void theSquareDoesNotGrowWithTheRoom() {
        int span = span(new QuartetPillarPatternProvider(6, 2).footprint(15, 15));
        assertEquals(span, span(new QuartetPillarPatternProvider(6, 2).footprint(21, 21)));
        assertEquals(span, span(new QuartetPillarPatternProvider(6, 2).footprint(29, 29)));
        // ...where the grid's column count does grow, which is the whole difference.
        assertTrue(new GridPillarPatternProvider(6, 2).footprint(29, 29).size()
                > new GridPillarPatternProvider(6, 2).footprint(15, 15).size());
    }

    /** It shrinks to fit rather than declining -- a small room still has a centre worth marking. */
    @Test
    void asquareTooBigForTheRoomShrinksInsteadOfVanishing() {
        Set<Coords2D> tight = new QuartetPillarPatternProvider(10, 2).footprint(7, 7);
        assertEquals(4, tight.size(), "still four columns in a 7-interior room");
        assertTrue(span(tight) < 10, "the authored square did not fit, so it was shrunk");
        for (Coords2D cell : tight) {
            assertTrue(cell.getX() >= 2 && cell.getX() <= 4 && cell.getY() >= 2 && cell.getY() <= 4,
                    "and it still respects the inset: " + cell.getX() + "," + cell.getY());
        }
    }

    @Test
    void aQuartetIsCentredAndSymmetric() {
        for (int iw = 5; iw <= 30; iw++) {
            final int width = iw;
            Set<Coords2D> cells = new QuartetPillarPatternProvider(6, 2).footprint(width, 11);
            if (cells.isEmpty()) {
                continue;
            }
            int low = cells.stream().mapToInt(Coords2D::getX).min().orElseThrow();
            int high = cells.stream().mapToInt(Coords2D::getX).max().orElseThrow();
            assertTrue(Math.abs(low - (width - 1 - high)) <= 1,
                    () -> "interior " + width + ": quartet is off-centre");
        }
    }

    private static int span(Set<Coords2D> cells) {
        return cells.stream().mapToInt(Coords2D::getX).max().orElseThrow()
                - cells.stream().mapToInt(Coords2D::getX).min().orElseThrow();
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
        List<BlockPlacement> out = build(room, new PillarEntry(new GridPillarLayout(4, 2), PILLAR), new BasicPillarGenerator());

        Set<Integer> ys = out.stream().map(BlockPlacement::getY).collect(Collectors.toSet());
        assertEquals(Set.of(61, 62, 63, 64, 65, 66), ys,
                "a column fills exactly the interior air rows the hollow step cleared");
        assertEquals(6, out.size() / distinctCells(out).size(), "every column is the full height");
    }

    @Test
    void theBaseAndCapRowsTakeTheirOwnBlocks() {
        PillarEntry entry = new PillarEntry(new GridPillarLayout(), PILLAR, Optional.of("minecraft:polished_andesite"), Optional.of("minecraft:chiseled_stone_bricks"), Map.of(), Optional.of(Map.of("base", "up")), Optional.of(Map.of("base", "down")),
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
        PillarEntry entry = new PillarEntry(new GridPillarLayout(4, 2), PILLAR, Optional.of("minecraft:polished_andesite"), Optional.of("minecraft:chiseled_stone_bricks"), Map.of(), Optional.empty(), Optional.empty(), SizeGate.UNBOUNDED);
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
        List<BlockPlacement> before = build(room, new PillarEntry(new GridPillarLayout(4, 2), PILLAR), new BasicPillarGenerator());
        Set<String> cells = distinctCells(before);

        // Put a door directly outside one of the columns the lattice placed.
        Coords2D column = distinctCoords(before).iterator().next();
        room.getDoorways().add(new Coords2D(column.getX(), column.getY() - 1));

        List<BlockPlacement> after = build(room, new PillarEntry(new GridPillarLayout(), PILLAR), new BasicPillarGenerator());
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
        List<BlockPlacement> out = build(room, new PillarEntry(new GridPillarLayout(), PILLAR), gen);

        assertEquals(distinctCells(out), gen.occupiedFloorCells().stream()
                .map(PillarGeneratorTest::key).collect(Collectors.toSet()));
    }

    /** Two overlapping layouts emit one column, not two writes into the same cell. */
    @Test
    void overlappingLayoutsDoNotEmitACellTwice() {
        PillarPatternEntry entry = new PillarPatternEntry(List.of(
                new PillarEntry(new GridPillarLayout(), PILLAR),
                new PillarEntry(new GridPillarLayout(), "minecraft:polished_andesite")));
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
        assertEquals(0, build(room(3, 3, 3), new PillarEntry(new GridPillarLayout(), PILLAR),
                new BasicPillarGenerator()).size(), "3-high room has one interior row and one cell");
        assertEquals(0, build(room(2, 9, 8), new PillarEntry(new GridPillarLayout(), PILLAR),
                new BasicPillarGenerator()).size(), "no interior at all");
    }

    // ---------- the selector ----------

    /**
     * <strong>Replaces {@code anUnrecognizedTypeIsSkippedAndTheRestStillDraw}.</strong> That
     * behaviour is gone by design: since the layouts moved to a registry, an unregistered type is
     * a LOAD ERROR at decode and so can never reach the selector. Asserting the skip here would
     * now require hand-constructing a layout the codec would have rejected, i.e. testing a state
     * the mod cannot be in.
     *
     * <p>The rule it protected &mdash; one bad pattern must not take the others down with it
     * &mdash; is unchanged and still covered, by {@code anUnresolvableShaftBlockDropsThatLayoutOnly}
     * below. A block id is still resolved at draw time, so that one is genuinely a runtime skip.</p>
     */
    @Test
    void anUnregisteredLayoutTypeIsALoadErrorRatherThanASilentSkip() {
        DataResult<PillarEntry> result = PillarEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\": \"dungeons2:obelisks\", \"block\": \"" + PILLAR + "\"}"));
        assertTrue(result.result().isEmpty(), "an unregistered layout must not decode");
        assertTrue(result.error().orElseThrow().message().contains("dungeons2:obelisks"),
                "the error must name the id it could not find");
    }

    @Test
    void anUnresolvableShaftBlockDropsThatLayoutOnly() {
        List<PillarLayout> layouts = PillarPatternSelector.toLayouts(new PillarPatternEntry(List.of(
                new PillarEntry(new GridPillarLayout(), "minecraft:not_a_real_block"),
                new PillarEntry(new GridPillarLayout(), PILLAR))));
        assertEquals(1, layouts.size());
        assertEquals(PILLAR, layouts.get(0).entry().block());
    }

    @Test
    void aGatedPatternDropsOutOfSmallRooms() {
        PillarPatternEntry entry = new PillarPatternEntry(List.of(
                new PillarEntry(new GridPillarLayout(), PILLAR, Optional.empty(), Optional.empty(), Map.of(),
                        Optional.empty(), Optional.empty(),
                        new SizeGate(0, 11, Optional.empty(), Optional.empty()))));

        assertEquals(0, PillarPatternSelector.layoutsFor(Optional.of(entry), 9, 9, 8).size());
        assertEquals(1, PillarPatternSelector.layoutsFor(Optional.of(entry), 13, 13, 8).size());
    }

    @Test
    void aColonnadeIsSelectedAndDrawsFullColumns() {
        List<BlockPlacement> out = build(room(17, 11, 8),
                new PillarEntry(new ColonnadePillarLayout(), PILLAR), new BasicPillarGenerator());

        assertFalse(out.isEmpty(), "the colonnade should have drawn");
        assertEquals(Set.of(61, 62, 63, 64, 65, 66),
                out.stream().map(BlockPlacement::getY).collect(Collectors.toSet()));
        assertEquals(2, distinctCoords(out).stream().map(Coords2D::getY).distinct().count(),
                "two rows in the room, drawn as full columns");
    }

    /**
     * Grid and colonnade in one slot, deduplicated where they meet. Note this composes rather than
     * choosing -- in a room where both fit, both draw. It is NOT a way to say "colonnade in long
     * rooms, grid in square ones"; the room here is elongated so both are present at once.
     */
    @Test
    void aGridAndAColonnadeCanShareASlot() {
        PillarPatternEntry entry = new PillarPatternEntry(List.of(
                new PillarEntry(new ColonnadePillarLayout(), PILLAR),
                new PillarEntry(new GridPillarLayout(), "minecraft:polished_andesite")));
        List<BlockPlacement> out = new ArrayList<>();
        new BasicPillarGenerator().withPillarLayouts(PillarPatternSelector.toLayouts(entry))
                .build(room(23, 13, 8), 60, DungeonMotif.CLASSIC, RandomSource.create(3L), out);

        assertEquals(out.size(), out.stream()
                .map(bp -> bp.getX() + "," + bp.getY() + "," + bp.getZ()).distinct().count());
        assertTrue(out.stream().anyMatch(bp -> PILLAR.equals(bp.getBlockId())));
        assertTrue(out.stream().anyMatch(bp -> "minecraft:polished_andesite".equals(bp.getBlockId())));
    }

    @Test
    void anAbsentSlotDrawsNothing() {
        assertEquals(0, PillarPatternSelector.layoutsFor(Optional.empty(), 13, 13, 8).size());
    }

    // ---------- helpers ----------

    private static void assertArray(int[] expected, int[] actual) {
        assertEquals(java.util.Arrays.toString(expected), java.util.Arrays.toString(actual));
    }

    // ---------- #58: a column must not stand over a pit ----------

    /**
     * Backlog #58. A column is drawn from the walking plane <em>upward</em>, so one rolled onto a
     * cell the pit excavated stands in mid-air over the hole.
     *
     * <h2>Ordering did not already prevent this</h2>
     * <p>{@code BasicRoomGenerator} runs the pit first and its comment claimed that was enough,
     * because "a cell that is now a hole" is not one a later step would stand on. Running first
     * only makes the pit's cells <em>available</em>; each later step still has to ask. The props
     * asked, via {@code taken}. This generator did not, because it builds from a layout, and a
     * layout knows the room's dimensions and nothing about what has been carved out of it.</p>
     *
     * <h2>The unguarded run is the point</h2>
     * <p>Asserting first that a column DOES land in the pit without the exclusion is what stops
     * this passing on a room where the layout never overlapped the pit to begin with &mdash; the
     * trap #46's tautological test fell into.</p>
     */
    @Test
    void aColumnIsNotBuiltStandingOverAPit() {
        RoomData room = room(15, 15, 8);
        int floorY = 60;
        Set<Coords2D> dug = RoomPitGenerator.excavate(room, floorY,
                new PitPatternEntry(new CentrePitShape(7, 3)), 5,
                new FloorConfig(PILLAR, PILLAR), RandomSource.create(0xD2_58L), new ArrayList<>());
        assertFalse(dug.isEmpty(), "the pit did not excavate, so this test proves nothing");

        PillarEntry entry = new PillarEntry(new GridPillarLayout(2, 1), PILLAR);

        Set<Coords2D> unguarded = distinctCoords(build(room, entry, new BasicPillarGenerator()));
        Set<Coords2D> overlap = new LinkedHashSet<>(unguarded);
        overlap.retainAll(dug);
        assertFalse(overlap.isEmpty(),
                "this room and layout never put a column in the pit, so the guarded run below would"
                        + " pass whether or not the exclusion works -- widen the pit or tighten the"
                        + " grid");

        List<BlockPlacement> out = new ArrayList<>();
        BasicPillarGenerator gen = new BasicPillarGenerator();
        gen.withPillarLayouts(PillarPatternSelector.toLayouts(new PillarPatternEntry(List.of(entry))))
                .build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(1L), out, dug);

        for (Coords2D cell : distinctCoords(out)) {
            assertFalse(dug.contains(cell),
                    "a column was built at " + key(cell) + ", which the pit excavated -- it stands"
                            + " on nothing");
        }

        // Per-cell, not all-or-nothing. A guard that dropped the whole layout would satisfy the
        // loop above and ruin the room; a colonnade missing the columns that fell in the hole is
        // still a colonnade. This is the same granularity the doorway exclusion has always had.
        assertFalse(out.isEmpty(),
                "the whole colonnade was dropped because part of it fell in the pit -- the exclusion"
                        + " skips cells, not layouts");
    }

    /**
     * The no-exclusion overload must be exactly an empty exclusion set. This is what lets #58 ship
     * without re-rolling a single existing seed: a room with no pit lays out as it always has.
     */
    @Test
    void theConvenienceOverloadIsTheSameAsExcludingNothing() {
        RoomData room = room(15, 15, 8);
        PillarEntry entry = new PillarEntry(new GridPillarLayout(2, 1), PILLAR);

        List<BlockPlacement> viaOverload = build(room, entry, new BasicPillarGenerator());

        List<BlockPlacement> viaEmptySet = new ArrayList<>();
        new BasicPillarGenerator()
                .withPillarLayouts(PillarPatternSelector.toLayouts(
                        new PillarPatternEntry(List.of(entry))))
                .build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), viaEmptySet, Set.of());

        assertFalse(viaOverload.isEmpty(), "nothing built, so the comparison is vacuous");
        assertEquals(distinctCoords(viaOverload), distinctCoords(viaEmptySet),
                "the no-exclusion overload and an empty exclusion set built different rooms");
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
