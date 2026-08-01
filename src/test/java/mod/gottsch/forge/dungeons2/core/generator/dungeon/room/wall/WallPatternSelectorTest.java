package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
