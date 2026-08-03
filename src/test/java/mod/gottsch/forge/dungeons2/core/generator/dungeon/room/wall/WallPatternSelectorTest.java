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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
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
        JsonElement json = GSON.fromJson(
                "{\"type\": \"courses\", \"courses\": [{\"block\": \"minecraft:andesite\"}]}",
                JsonElement.class);
        WallPatternEntry entry = WallPatternEntry.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(CourseAnchor.BOTTOM, entry.courses().get(0).anchor());
        assertEquals(0, entry.courses().get(0).offset());
    }

    /**
     * A typo'd anchor must FAIL, not silently read as BOTTOM -- that would put crown molding on the
     * floor with no error anywhere, the exact silent-default failure the config work exists to stop.
     */
    @Test
    void aMisspelledAnchorFailsToDecode() {
        JsonElement json = GSON.fromJson(
                "{\"type\": \"courses\", \"courses\": ["
                        + "{\"block\": \"minecraft:andesite\", \"anchor\": \"topp\"}]}",
                JsonElement.class);
        assertTrue(WallPatternEntry.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void bothAnchorsRoundTrip() {
        JsonElement json = GSON.fromJson(
                "{\"type\": \"courses\", \"courses\": ["
                        + "{\"block\": \"minecraft:andesite\", \"anchor\": \"bottom\"},"
                        + "{\"block\": \"minecraft:andesite\", \"anchor\": \"top\", \"offset\": 2}]}",
                JsonElement.class);
        WallPatternEntry entry = WallPatternEntry.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(CourseAnchor.BOTTOM, entry.courses().get(0).anchor());
        assertEquals(CourseAnchor.TOP, entry.courses().get(1).anchor());
        assertEquals(2, entry.courses().get(1).offset());
    }

    /** A course block is required; a course with none is a broken entry, not a defaulted one. */
    @Test
    void aCourseWithoutABlockFailsToDecode() {
        JsonElement json = GSON.fromJson(
                "{\"type\": \"courses\", \"courses\": [{\"anchor\": \"top\"}]}", JsonElement.class);
        assertTrue(WallPatternEntry.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
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

        assertEquals(1, entry.forRoom(9, 9, 5).courses().size(), "the crown gates out at height 5");
        assertEquals("minecraft:polished_andesite", entry.forRoom(9, 9, 5).courses().get(0).block(),
                "the plinth is the one that survives");
        assertEquals(2, entry.forRoom(9, 9, 6).courses().size(), "one block taller and both draw");
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
        JsonElement json = GSON.fromJson(
                "{\"type\": \"courses\", \"courses\": [{\"block\": \"minecraft:andesite\", "
                        + "\"alternate\": \"strictt\"}]}", JsonElement.class);
        assertTrue(WallPatternEntry.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }
}
