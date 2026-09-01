package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.wall.CoursesWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.DiamondWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.EndPilastersWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.GradientWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.PanelsWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.PilastersWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.WallPattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #65 phase 5 &mdash; the {@code wall} slot reads a material role, and so do the two sections that
 * hold wall content without being a scheme.
 *
 * <p>Deliberately last of the big four. Two of the ten fields belong to records that are
 * <strong>shared</strong>: {@link mod.gottsch.forge.dungeons2.core.config.wall.PilasterShape} by
 * {@code pilasters} and {@code end_pilasters}, and {@code CourseEntry} by every course-bearing
 * record in the schema. One mistake here reaches the most authored content of any phase.</p>
 *
 * <p>{@code CourseEntry} has <strong>three homes</strong>, which is more than any other record and
 * is the thing this phase existed to get right: inside a {@code courses} wall pattern, and directly
 * on {@code CorridorConfig} and {@code CorridorStyle} as a bare list. Phases 3 and 4 each had a
 * record with one second home nesting a whole entry; a bare list one level shallower is a shape
 * neither had to handle.</p>
 */
class MaterialRolesWallTest {

    private static final Gson GSON = new Gson();

    private static final String PALETTE =
            "\"palette\": {\"a\": \"minecraft:stone\", \"b\": \"minecraft:cobblestone\","
                    + " \"c\": \"minecraft:andesite\"}";

    // ---- every wall field ------------------------------------------------------------------------

    /** The shared strip. Both types that wrap it must resolve, or the two can drift. */
    @Test
    void bothPilasterTypesResolveTheSharedStrip() {
        String config = "\"config\": {\"block\": \"$a\", \"base_block\": \"$b\","
                + " \"cap_block\": \"$c\", \"spacing\": 5}";

        PilastersWallPattern even = (PilastersWallPattern)
                wall("{\"type\": \"dungeons2:pilasters\", " + config + "}");
        assertEquals("minecraft:stone", even.shape().block());
        assertEquals(Optional.of("minecraft:cobblestone"), even.shape().baseBlock());
        assertEquals(Optional.of("minecraft:andesite"), even.shape().capBlock());
        assertEquals(5, even.shape().spacing(), "and keeps what it did not resolve");

        EndPilastersWallPattern ends = (EndPilastersWallPattern)
                wall("{\"type\": \"dungeons2:end_pilasters\", " + config + "}");
        assertEquals("minecraft:stone", ends.shape().block());
        assertEquals(Optional.of("minecraft:cobblestone"), ends.shape().baseBlock());
        assertEquals(Optional.of("minecraft:andesite"), ends.shape().capBlock());
    }

    @Test
    void panelsResolvesItsBlockAndKeepsItsWidth() {
        PanelsWallPattern panels = (PanelsWallPattern)
                wall("{\"type\": \"dungeons2:panels\", \"config\": {\"block\": \"$a\","
                        + " \"width\": 4}}");
        assertEquals("minecraft:stone", panels.block());
        assertEquals(4, panels.width());
    }

    @Test
    void diamondResolvesItsBlockAndKeepsItsSize() {
        DiamondWallPattern diamond = (DiamondWallPattern)
                wall("{\"type\": \"dungeons2:diamond\", \"config\": {\"block\": \"$a\","
                        + " \"size\": 5}}");
        assertEquals("minecraft:stone", diamond.block());
        assertEquals(5, diamond.size());
    }

    /** Both ends of the gradient, and the four tuning fields between them survive the rebuild. */
    @Test
    void aGradientResolvesBothEndsAndKeepsItsRamp() {
        GradientWallPattern gradient = (GradientWallPattern)
                wall("{\"type\": \"dungeons2:gradient\", \"config\": {"
                        + "\"bottom_block\": \"$a\", \"top_block\": \"$b\","
                        + " \"bottom_probability\": 0.9, \"top_probability\": 0.05,"
                        + " \"hold_rows\": 2}}");
        assertEquals("minecraft:stone", gradient.bottomBlock());
        assertEquals("minecraft:cobblestone", gradient.topBlock());
        assertEquals(0.9D, gradient.bottomProbability(), 1.0e-9);
        assertEquals(0.05D, gradient.topProbability(), 1.0e-9);
        assertEquals(2, gradient.holdRows());
    }

    @Test
    void aCourseResolvesAllThreeOfItsBlocks() {
        CoursesWallPattern courses = (CoursesWallPattern)
                wall("{\"type\": \"dungeons2:courses\", \"config\": {\"courses\": ["
                        + "{\"block\": \"$a\", \"alternate_block\": \"$b\","
                        + " \"corner_block\": \"$c\", \"anchor\": \"top\"}]}}");
        WallPatternEntry.CourseEntry course = courses.courses().get(0);
        assertEquals("minecraft:stone", course.block());
        assertEquals(Optional.of("minecraft:cobblestone"), course.alternateBlock());
        assertEquals(Optional.of("minecraft:andesite"), course.cornerBlock());
    }

    /** Every pattern in the wall's list, not only the first -- walls COMPOSE, so all of them draw. */
    @Test
    void everyPatternInTheWallListIsResolved() {
        MotifConfig motif = fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"hall\","
                + " \"wall\": {\"patterns\": ["
                + "{\"type\": \"dungeons2:panels\", \"config\": {\"block\": \"$a\"}},"
                + "{\"type\": \"dungeons2:diamond\", \"config\": {\"block\": \"$b\"}}]}}]}");
        List<String> blocks = new ArrayList<>();
        motif.forFloor(0).schemes().stream()
                .filter(scheme -> "hall".equals(scheme.name())).findFirst().orElseThrow()
                .wall().orElseThrow().patterns()
                .forEach(entry -> blocks.add(blockOf(entry.pattern())));
        assertEquals(List.of("minecraft:stone", "minecraft:cobblestone"), blocks);
    }

    // ---- CourseEntry's three homes ---------------------------------------------------------------

    /** Home two: the {@code wall} SECTION's own pattern. */
    @Test
    void theWallSECTIONsOwnPatternIsResolved() {
        MotifConfig motif = fold("""
                {"palette": {"a": "minecraft:stone"},
                 "wall": {"wall": "minecraft:stone_bricks",
                          "pattern": {"patterns": [
                              {"type": "dungeons2:panels", "config": {"block": "$a"}}]}}}""");
        assertEquals("minecraft:stone",
                blockOf(motif.forFloor(0).wall().pattern().orElseThrow().patterns().get(0).pattern()));
    }

    /**
     * Home three: a bare {@code CourseEntry} list on the corridor section &mdash; one level
     * shallower than anywhere else in the schema, and the shape no earlier phase had to handle.
     */
    @Test
    void theCorridorSECTIONsOwnCoursesAreResolved() {
        MotifConfig motif = fold("""
                {"palette": {"a": "minecraft:stone"},
                 "corridor": {"floor": "minecraft:cobblestone",
                              "alternate_floor": "minecraft:cobblestone",
                              "ceiling": "minecraft:stone_bricks",
                              "courses": [{"block": "$a", "anchor": "bottom"}]}}""");
        assertEquals("minecraft:stone",
                motif.forFloor(0).corridor().courses().get(0).block());
    }

    /** And a named corridor STYLE's courses, which are rolled per floor. */
    @Test
    void aCorridorStylesCoursesAreResolvedToo() {
        MotifConfig motif = fold("""
                {"palette": {"a": "minecraft:stone"},
                 "corridor": {"floor": "minecraft:cobblestone",
                              "alternate_floor": "minecraft:cobblestone",
                              "ceiling": "minecraft:stone_bricks",
                              "styles": [{"name": "dressed",
                                          "courses": [{"block": "$a", "anchor": "top"}]}]}}""");
        assertEquals("minecraft:stone",
                motif.forFloor(0).corridor().styles().get(0).courses().get(0).block());
    }

    @Test
    void anUndeclaredRoleInACorridorCourseIsReportedAtLoad() {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC, """
                {"corridor": {"floor": "minecraft:cobblestone",
                              "alternate_floor": "minecraft:cobblestone",
                              "ceiling": "minecraft:stone_bricks",
                              "courses": [{"block": "$plinth", "anchor": "bottom"}]}}""")),
                problems::add);
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$plinth"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("corridor section"), () -> problems.get(0));
    }

    @Test
    void anUndeclaredRoleInTheWallSectionIsReportedAtLoad() {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC, """
                {"wall": {"wall": "minecraft:stone_bricks",
                          "pattern": {"patterns": [
                              {"type": "dungeons2:diamond", "config": {"block": "$inlay"}}]}}}""")),
                problems::add);
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$inlay"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("wall section"), () -> problems.get(0));
    }

    // ---- a band repaints all of it ---------------------------------------------------------------

    @Test
    void aBandRepaintsTheWallSlotAndTheCorridorTogether() {
        MotifConfig motif = fold("""
                {"palette": {"plinth": "dungeonblocks:left_large_stone_brick"},
                 "corridor": {"floor": "minecraft:cobblestone",
                              "alternate_floor": "minecraft:cobblestone",
                              "ceiling": "minecraft:stone_bricks",
                              "courses": [{"block": "$plinth", "anchor": "bottom"}]},
                 "strata_by_floor_index": [
                   {"min_floor_index": 0, "palette": {"plinth": "minecraft:mud_bricks"}},
                   {"min_floor_index": 1}]}""");
        assertEquals("minecraft:mud_bricks", motif.forFloor(0).corridor().courses().get(0).block());
        assertEquals("dungeonblocks:left_large_stone_brick",
                motif.forFloor(1).corridor().courses().get(0).block());
    }

    // ---- identity --------------------------------------------------------------------------------

    @Test
    void aWallOfLiteralsIsNotEvenCopied() {
        WallPatternEntry entry = decode(WallPatternEntry.CODEC,
                "{\"patterns\": [{\"type\": \"dungeons2:panels\","
                        + " \"config\": {\"block\": \"minecraft:stone\"}}]}");
        assertSame(entry, entry.withRoles(role -> "minecraft:dirt"));
    }

    @Test
    void aCorridorOfLiteralsIsNotEvenCopied() {
        CorridorConfig corridor = decode(CorridorConfig.CODEC,
                "{\"floor\": \"minecraft:cobblestone\","
                        + " \"alternate_floor\": \"minecraft:cobblestone\","
                        + " \"ceiling\": \"minecraft:stone_bricks\","
                        + " \"courses\": [{\"block\": \"minecraft:stone\", \"anchor\": \"top\"}]}");
        assertSame(corridor, corridor.withRoles(role -> "minecraft:dirt"));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static String blockOf(WallPattern pattern) {
        if (pattern instanceof PanelsWallPattern panels) {
            return panels.block();
        }
        if (pattern instanceof DiamondWallPattern diamond) {
            return diamond.block();
        }
        throw new AssertionError("unhandled pattern: " + pattern);
    }

    /** One wall pattern, decoded in a scheme and then resolved against {@link #PALETTE}. */
    private static WallPattern wall(String patternJson) {
        MotifConfig motif = fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"hall\","
                + " \"wall\": {\"patterns\": [" + patternJson + "]}}]}");
        return motif.forFloor(0).schemes().stream()
                .filter(scheme -> "hall".equals(scheme.name())).findFirst().orElseThrow()
                .wall().orElseThrow().patterns().get(0).pattern();
    }

    private static MotifConfig fold(String json) {
        List<String> problems = new ArrayList<>();
        MotifConfig motif = MotifConfigFragment.resolve(
                List.of(decode(MotifConfigFragment.CODEC, json)), problems::add);
        assertEquals(List.of(), problems, "expected this motif to fold cleanly");
        return motif;
    }

    private static <A> A decode(Codec<A> codec, String json) {
        DataResult<A> result = codec.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
        return result.result().orElseThrow(() -> new AssertionError(
                "expected this to decode: " + result.error().orElseThrow().message() + " -- " + json));
    }
}
