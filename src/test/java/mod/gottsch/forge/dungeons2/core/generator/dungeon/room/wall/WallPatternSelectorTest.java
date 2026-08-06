package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAlternate;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseEntry;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entry &rarr; provider mapping, and the anchor codec. Like {@code FloorPatternSelector} this does
 * not roll -- the scheme roll already happened once for the whole room.
 */
class WallPatternSelectorTest {

    private static final Gson GSON = new Gson();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static WallPatternEntry courses(CourseEntry... entries) {
        return new WallPatternEntry("courses", List.of(entries));
    }

    /**
     * The bands of the entry's single pattern. Every test here builds a one-pattern slot, which is
     * what the whole slot used to be before it became an ordered list of patterns.
     */
    private static List<CourseEntry> bands(WallPatternEntry entry) {
        return entry.patterns().isEmpty() ? List.of() : entry.patterns().get(0).courses();
    }

    /** A one-pattern courses slot in its authored form, which is what the codec now expects. */
    private static WallPatternEntry parse(String coursesJson) {
        JsonElement json = GSON.fromJson(
                "{\"patterns\": [{\"type\": \"courses\", \"courses\": " + coursesJson + "}]}",
                JsonElement.class);
        return WallPatternEntry.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
    }

    /** As {@link #parse}, for the cases that are supposed to fail. */
    private static boolean parseFails(String coursesJson) {
        JsonElement json = GSON.fromJson(
                "{\"patterns\": [{\"type\": \"courses\", \"courses\": " + coursesJson + "}]}",
                JsonElement.class);
        return WallPatternEntry.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent();
    }

    @Test
    void anAbsentSlotMeansPlainWall() {
        assertNull(WallPatternSelector.providerFor(Optional.empty()));
    }

    @Test
    void aCoursesEntryMapsToTheCoursesProvider() {
        ISurfacePatternProvider provider = WallPatternSelector.providerFor(Optional.of(
                courses(new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 0))));
        assertInstanceOf(CoursesWallPatternProvider.class, provider);
        assertEquals(4, provider.plan(4, 5, Direction.SOUTH).markedCells());
    }

    @Test
    void anUnrecognizedTypeMeansPlainWall() {
        assertNull(WallPatternSelector.toProvider(new WallPatternEntry("pilasters", List.of())));
    }

    @Test
    void anEmptyCourseListMeansPlainWall() {
        assertNull(WallPatternSelector.toProvider(courses()));
    }

    /**
     * One unresolvable block degrades the WHOLE entry, not just its own course. A half-drawn
     * pattern -- a crown with no plinth under it -- reads as a bug; a plain wall reads as a plain
     * wall. Same degrade-the-whole-entry rule the floor patterns follow.
     */
    @Test
    void oneBadBlockDegradesTheWholeEntry() {
        assertNull(WallPatternSelector.toProvider(courses(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 0),
                new CourseEntry("minecraft:not_a_real_block", CourseAnchor.TOP, 0))));
    }

    /** Same rule for the two optional slots: a band that silently loses its quoins is worse. */
    @Test
    void anUnresolvableCornerOrAlternateDegradesTheWholeEntry() {
        assertNull(WallPatternSelector.toProvider(courses(new CourseEntry(
                "minecraft:polished_andesite", Optional.empty(), Optional.of("minecraft:not_a_real_block"),
                CourseAnchor.BOTTOM, 0, 0, CourseOrient.NONE, Map.of()))));

        assertNull(WallPatternSelector.toProvider(courses(new CourseEntry(
                "minecraft:polished_andesite", Optional.of("minecraft:not_a_real_block"), Optional.empty(),
                CourseAnchor.BOTTOM, 0, 0, CourseOrient.NONE, Map.of()))));
    }

    /**
     * The author-supplied properties apply to all three blocks. A cornice of upside-down stairs
     * whose corner stair came out right side up would be a very quiet defect.
     */
    @Test
    void authoredPropertiesApplyToTheCornerBlockToo() {
        ISurfacePatternProvider provider = WallPatternSelector.toProvider(courses(new CourseEntry(
                "minecraft:stone_brick_stairs", Optional.empty(), Optional.of("minecraft:stone_stairs"),
                CourseAnchor.BOTTOM, 0, 0, CourseOrient.NONE, Map.of("half", "top"))));

        SurfacePlan plan = provider.plan(5, 5, Direction.SOUTH);
        assertEquals(Half.TOP, plan.get(0, 0).getValue(StairBlock.HALF), "the corner stair");
        assertEquals(Half.TOP, plan.get(2, 0).getValue(StairBlock.HALF), "the band stair");
    }

    @Test
    void anchorDefaultsToBottomWhenAbsent() {
        WallPatternEntry entry = parse("[{\"block\": \"minecraft:andesite\"}]");
        assertEquals(CourseAnchor.BOTTOM, bands(entry).get(0).anchor());
        assertEquals(0, bands(entry).get(0).offset());
    }

    /**
     * A typo'd anchor must FAIL, not silently read as BOTTOM -- that would put crown molding on the
     * floor with no error anywhere, the exact silent-default failure the config work exists to stop.
     */
    @Test
    void aMisspelledAnchorFailsToDecode() {
        assertTrue(parseFails("[{\"block\": \"minecraft:andesite\", \"anchor\": \"topp\"}]"));
    }

    @Test
    void bothAnchorsRoundTrip() {
        WallPatternEntry entry = parse("["
                + "{\"block\": \"minecraft:andesite\", \"anchor\": \"bottom\"},"
                + "{\"block\": \"minecraft:andesite\", \"anchor\": \"top\", \"offset\": 2}]");
        assertEquals(CourseAnchor.BOTTOM, bands(entry).get(0).anchor());
        assertEquals(CourseAnchor.TOP, bands(entry).get(1).anchor());
        assertEquals(2, bands(entry).get(1).offset());
    }

    /** A course block is required; a course with none is a broken entry, not a defaulted one. */
    @Test
    void aCourseWithoutABlockFailsToDecode() {
        assertTrue(parseFails("[{\"anchor\": \"top\"}]"));
    }

    // ---------- per-course gates ----------

    private static CourseEntry gatedCourse(String block, CourseAnchor anchor, int minHeight) {
        return new CourseEntry(block, Optional.empty(), Optional.empty(), anchor, 0, 0,
                CourseOrient.NONE, Map.of(), CourseAlternate.RANDOM,
                new SizeGate(minHeight, 0, Optional.empty(), Optional.empty()));
    }

    /**
     * The case a slot-level gate cannot express: a plinth belongs on every wall in the dungeon,
     * while the crown above it needs headroom a 5-high room hasn't got. One entry, two fates.
     */
    @Test
    void aCourseOutsideItsGateIsDroppedWhileTheRestOfTheBandStays() {
        WallPatternEntry entry = courses(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 0),
                gatedCourse("minecraft:stone_bricks", CourseAnchor.TOP, 6));

        assertEquals(1, bands(entry.forRoom(9, 9, 5)).size(), "the crown gates out at height 5");
        assertEquals("minecraft:polished_andesite", bands(entry.forRoom(9, 9, 5)).get(0).block(),
                "the plinth is the one that survives");
        assertEquals(2, bands(entry.forRoom(9, 9, 6)).size(), "one block taller and both draw");
    }

    /** Rendered through the selector: the surviving course still draws, on its own row. */
    @Test
    void aGatedBandRendersOnlyItsEligibleCourses() {
        WallPatternEntry entry = courses(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 0),
                gatedCourse("minecraft:chiseled_stone_bricks", CourseAnchor.TOP, 6));

        // height 5 -> wall is 3 rows; only the plinth.
        SurfacePlan shortRoom = WallPatternSelector
                .providerFor(Optional.of(entry), 9, 9, 5).plan(4, 3, Direction.SOUTH);
        assertEquals(4, shortRoom.markedCells(), "just the plinth row");
        assertNull(shortRoom.get(0, 2), "the crown must not appear on the lintel row");

        // height 8 -> wall is 6 rows; both.
        SurfacePlan tallRoom = WallPatternSelector
                .providerFor(Optional.of(entry), 9, 9, 8).plan(4, 6, Direction.SOUTH);
        assertEquals(8, tallRoom.markedCells(), "plinth and crown");
    }

    /** Every course gating out is the same as an empty list: plain wall, not a broken one. */
    @Test
    void allCoursesGatingOutMeansPlainWall() {
        WallPatternEntry entry = courses(gatedCourse("minecraft:stone_bricks", CourseAnchor.TOP, 9));
        assertNull(WallPatternSelector.providerFor(Optional.of(entry), 9, 9, 5));
    }

    /** An ungated course is unchanged -- forRoom returns the same instance, not a copy. */
    @Test
    void anUngatedBandIsUntouched() {
        WallPatternEntry entry = courses(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 0));
        assertSame(entry, entry.forRoom(5, 5, 5));
        assertSame(entry, entry.forRoom(17, 17, 10));
    }

    // ---------- alternate mode ----------

    private static CourseEntry alternating(String block, String alternate, CourseAlternate mode) {
        return new CourseEntry(block, Optional.of(alternate), Optional.empty(),
                CourseAnchor.BOTTOM, 0, 0, CourseOrient.NONE, Map.of(), mode, SizeGate.UNBOUNDED);
    }

    /**
     * The case `random` gets wrong. A mirrored pair is two halves of one wide brick, so adjacent
     * left-left runs stop it reading as whole bricks -- strict alternation is the only thing that
     * keeps the halves paired.
     */
    @Test
    void strictAlternationLaysTheTwoBlocksDownEveryOtherCell() {
        ISurfacePatternProvider provider = WallPatternSelector.toProvider(courses(
                alternating("minecraft:polished_andesite", "minecraft:andesite",
                        CourseAlternate.STRICT)));

        SurfacePlan plan = provider.plan(8, 3, Direction.SOUTH, RandomSource.create(99L));
        for (int u = 0; u < 8; u++) {
            String expected = u % 2 == 0 ? "polished_andesite" : "andesite";
            assertTrue(plan.get(u, 0).getBlock().toString().contains(expected),
                    "u=" + u + " should be " + expected + ", got " + plan.get(u, 0));
        }
    }

    /** Strict is deterministic: the seed cannot change it, which random's whole point is that it does. */
    @Test
    void strictAlternationIgnoresTheSeed() {
        ISurfacePatternProvider provider = WallPatternSelector.toProvider(courses(
                alternating("minecraft:polished_andesite", "minecraft:andesite",
                        CourseAlternate.STRICT)));

        SurfacePlan a = provider.plan(9, 3, Direction.SOUTH, RandomSource.create(1L));
        SurfacePlan b = provider.plan(9, 3, Direction.SOUTH, RandomSource.create(999L));
        for (int u = 0; u < 9; u++) {
            assertSame(a.get(u, 0), b.get(u, 0), "u=" + u + " differed between seeds");
        }
    }

    /** Random stays the default and stays random -- a long band carries runs strict never would. */
    @Test
    void randomIsStillTheDefaultAndStillMixes() {
        assertEquals(CourseAlternate.RANDOM,
                new CourseEntry("minecraft:andesite", CourseAnchor.BOTTOM, 0).alternate());

        ISurfacePatternProvider provider = WallPatternSelector.toProvider(courses(
                alternating("minecraft:polished_andesite", "minecraft:andesite",
                        CourseAlternate.RANDOM)));

        SurfacePlan plan = provider.plan(64, 3, Direction.SOUTH, RandomSource.create(7L));
        boolean sawRun = false;
        for (int u = 1; u < 64; u++) {
            if (plan.get(u, 0) == plan.get(u - 1, 0)) {
                sawRun = true;
                break;
            }
        }
        assertTrue(sawRun, "a random mix should produce adjacent repeats somewhere in 64 cells");
    }

    @Test
    void aMisspelledAlternateModeFailsToDecode() {
        assertTrue(parseFails(
                "[{\"block\": \"minecraft:andesite\", \"alternate\": \"strictt\"}]"));
    }

    /**
     * The pre-Aug-2026 shape -- a single typed entry, no {@code patterns} -- must FAIL rather than
     * decode to a slot with nothing in it.
     *
     * <p>This is the reason {@code patterns} is a required field. A codec ignores keys it does not
     * recognise, so with the key optional an unmigrated datapack would parse cleanly, produce no
     * error, and render every one of its schemes as a plain wall. Nothing in game distinguishes
     * "the author never wrote trim" from "the author's trim was silently dropped", which makes it
     * exactly the silent-default failure the strict codecs exist to prevent.</p>
     */
    // ---------- several patterns in one slot ----------

    private static WallPatternEntry.PatternEntry pilasters(String block, int spacing, int projection) {
        return strips("pilasters", block, spacing, projection);
    }

    private static WallPatternEntry.PatternEntry strips(String type, String block, int spacing,
                                                        int projection) {
        return new WallPatternEntry.PatternEntry(type, List.of(),
                Optional.of(block), Optional.empty(), Optional.empty(), spacing, projection,
                CourseOrient.NONE, Map.of(), PilastersWallPatternProvider.DEFAULT_INSET,
                SizeGate.UNBOUNDED);
    }

    private static WallPatternEntry.PatternEntry band(String block) {
        return new WallPatternEntry.PatternEntry("courses",
                List.of(new CourseEntry(block, CourseAnchor.BOTTOM, 0)));
    }

    /** One pattern is handed back unwrapped, so the common case renders exactly as it always did. */
    @Test
    void aSinglePatternIsNotWrappedInAComposite() {
        ISurfacePatternProvider provider = WallPatternSelector.toProvider(
                new WallPatternEntry(List.of(band("minecraft:andesite"))));
        assertInstanceOf(CoursesWallPatternProvider.class, provider);
    }

    /**
     * Ordering is execution order: the later pattern wins the cells the two share. That is how an
     * author says whether a pilaster interrupts a band or the band runs across it, and it is the
     * reason the slot is an ordered list rather than a set.
     */
    @Test
    void alaterPatternWinsTheCellsTheTwoShare() {
        // A plinth on row 0 across the wall, and pilasters crossing it flush.
        WallPatternEntry bandFirst = new WallPatternEntry(List.of(
                band("minecraft:andesite"), pilasters("minecraft:chiseled_stone_bricks", 4, 0)));
        WallPatternEntry pilastersFirst = new WallPatternEntry(List.of(
                pilasters("minecraft:chiseled_stone_bricks", 4, 0), band("minecraft:andesite")));

        int u = PilastersWallPatternProvider.columns(9, 4, 0, Direction.SOUTH).get(0);
        SurfacePlan bandUnder = WallPatternSelector.toProvider(bandFirst).plan(9, 5, Direction.SOUTH);
        SurfacePlan bandOver = WallPatternSelector.toProvider(pilastersFirst).plan(9, 5, Direction.SOUTH);

        assertEquals(Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), bandUnder.get(u, 0),
                "pilasters listed last interrupt the band");
        assertEquals(Blocks.ANDESITE.defaultBlockState(), bandOver.get(u, 0),
                "the band listed last runs across the pilaster");
    }

    /**
     * Two patterns projecting to the same depth are merged into one layer, in list order. Handing
     * the surface two plans at one depth would instead let map iteration order decide the winner.
     */
    @Test
    void patternsProjectingToTheSameDepthMergeInOrder() {
        WallPatternEntry entry = new WallPatternEntry(List.of(
                new WallPatternEntry.PatternEntry("courses", List.of(new CourseEntry(
                        "minecraft:andesite", Optional.empty(), Optional.empty(),
                        CourseAnchor.BOTTOM, 0, 1, CourseOrient.NONE, Map.of()))),
                pilasters("minecraft:chiseled_stone_bricks", 4, 1)));

        var layers = ((IProjectingPatternProvider) WallPatternSelector.toProvider(entry))
                .projectedPlans(11, 5, Direction.SOUTH);
        assertEquals(1, layers.size(), "both project one cell out, so there is one layer");

        int u = PilastersWallPatternProvider.columns(11, 4, 1, Direction.SOUTH).get(0);
        assertEquals(Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), layers.get(1).get(u, 0),
                "the pilaster is listed later, so it wins the shared cell");
    }

    /**
     * A pattern that will not resolve is dropped on its own, and the rest of the list still draws.
     * Two patterns are two authored decisions: silently losing the pilasters because a course names
     * a typo'd block would hide which one is actually broken.
     */
    @Test
    void anUnresolvablePatternDoesNotTakeTheOthersWithIt() {
        ISurfacePatternProvider provider = WallPatternSelector.toProvider(new WallPatternEntry(List.of(
                band("minecraft:not_a_real_block"),
                pilasters("minecraft:chiseled_stone_bricks", 4, 0))));

        assertInstanceOf(PilastersWallPatternProvider.class, provider,
                "the good pattern survives, unwrapped now that it is the only one");
    }

    /**
     * The plinth and the capital take their block properties <strong>separately</strong>.
     *
     * <p>This is the one place the pilaster schema deliberately differs from a course, which shares
     * one {@code properties} map across its three block slots. A course's three slots are one block
     * family wanting the same state; a pilaster's plinth and capital are typically the <em>same
     * block at opposite values</em> of a vertical property, so a shared map cannot describe a column
     * at all. Checked with vanilla stairs because {@code dungeonblocks} ids do not resolve headless
     * &mdash; the property that matters there is {@code base}, but the mechanism is the same.</p>
     */
    @Test
    void thePlinthAndCapitalTakeTheirOwnProperties() {
        WallPatternEntry.PatternEntry entry = new WallPatternEntry.PatternEntry("pilasters",
                List.of(), Optional.of("minecraft:stone_brick_stairs"),
                Optional.of("minecraft:stone_brick_stairs"), Optional.of("minecraft:stone_brick_stairs"),
                4, 0, CourseOrient.NONE, Map.of(),
                Optional.of(Map.of("half", "bottom")), Optional.of(Map.of("half", "top")),
                0, SizeGate.UNBOUNDED);

        SurfacePlan plan = WallPatternSelector.toProvider(new WallPatternEntry(List.of(entry)))
                .plan(11, 5, Direction.SOUTH);
        int u = PilastersWallPatternProvider.columns(11, 4, 0, Direction.SOUTH).get(0);

        assertEquals(Half.BOTTOM, plan.get(u, 0).getValue(StairBlock.HALF), "the plinth row");
        assertEquals(Half.TOP, plan.get(u, 4).getValue(StairBlock.HALF), "the capital row, inverted");
    }

    /** Unauthored, both fall back to the strip's own properties -- the behaviour before the split. */
    @Test
    void anUnauthoredBaseAndCapInheritTheStripProperties() {
        WallPatternEntry.PatternEntry entry = new WallPatternEntry.PatternEntry("pilasters",
                List.of(), Optional.of("minecraft:stone_brick_stairs"),
                Optional.empty(), Optional.empty(), 4, 0, CourseOrient.NONE,
                Map.of("half", "top"), 0, SizeGate.UNBOUNDED);

        SurfacePlan plan = WallPatternSelector.toProvider(new WallPatternEntry(List.of(entry)))
                .plan(11, 5, Direction.SOUTH);
        int u = PilastersWallPatternProvider.columns(11, 4, 0, Direction.SOUTH).get(0);

        assertEquals(Half.TOP, plan.get(u, 0).getValue(StairBlock.HALF));
        assertEquals(Half.TOP, plan.get(u, 2).getValue(StairBlock.HALF));
        assertEquals(Half.TOP, plan.get(u, 4).getValue(StairBlock.HALF));
    }

    /**
     * {@code end_pilasters} is its own type and maps to the ends layout, not to the even one.
     * Composing the two in a slot is what gives corner piers with an even rhythm between.
     */
    @Test
    void endPilastersIsADistinctTypeFromPilasters() {
        ISurfacePatternProvider ends = WallPatternSelector.toProvider(new WallPatternEntry(
                List.of(strips("end_pilasters", "minecraft:chiseled_stone_bricks", 4, 0))));
        ISurfacePatternProvider even = WallPatternSelector.toProvider(new WallPatternEntry(
                List.of(strips("pilasters", "minecraft:chiseled_stone_bricks", 4, 0))));

        assertInstanceOf(PilastersWallPatternProvider.class, ends);
        // Two strips on a long wall whatever its length, against the stride's several.
        assertEquals(2, ends.plan(15, 5, Direction.SOUTH).markedCells() / 5,
                "end_pilasters is exactly one strip per end");
        assertTrue(even.plan(15, 5, Direction.SOUTH).markedCells() / 5 > 2,
                "pilasters is a rhythm, so a 15-wide wall carries more than two");
    }

    /** Every pattern failing leaves nothing to draw, which is the plain wall. */
    @Test
    void everyPatternFailingIsAPlainWall() {
        assertNull(WallPatternSelector.toProvider(new WallPatternEntry(List.of(
                band("minecraft:not_a_real_block")))));
    }

    @Test
    void theLegacySingleEntryShapeFailsToDecode() {
        JsonElement json = GSON.fromJson(
                "{\"type\": \"courses\", \"courses\": [{\"block\": \"minecraft:andesite\"}]}",
                JsonElement.class);
        assertTrue(WallPatternEntry.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(),
                "the old wall-slot shape must fail loudly, not decode to an empty slot");
    }
}
