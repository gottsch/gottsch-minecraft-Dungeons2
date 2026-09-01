package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #65 phase 1 &mdash; the material-role palette exists and is validated, and <strong>no block field
 * reads a role yet</strong>.
 *
 * <p>Two halves, and the second is the one that earns the phase. The palette itself is inert: a
 * motif may declare roles, a band may overlay them, and nothing resolves against either. What makes
 * shipping that alone safe is {@link Codecs#BLOCK_ID}, installed on <em>every</em> block-valued
 * field up front &mdash; because roles arrive one record at a time, and a record that does not read
 * one yet would otherwise hand {@code "$shaft"} to {@code BlockStateCodec#blockOrNull}, get
 * {@code null} back, and draw <strong>nothing</strong>. No error, no log line, a dressed wall coming
 * out plain, and only in the half-converted state where nobody is looking. Rejecting a role
 * everywhere first means that state cannot exist.</p>
 *
 * <p>{@link #everyBlockFieldRejectsARole} is therefore exhaustive rather than representative: every
 * field that has NOT yet been converted, named one at a time. A field added later that forgets
 * {@code BLOCK_ID} is a hole this test is the only thing watching for.</p>
 *
 * <p><strong>The two lists are the phase boundary.</strong> Converting a record moves its fields
 * from {@link #everyBlockFieldRejectsARole} to
 * {@link MaterialRolesPillarsTest} &mdash; 46 still reject, 3 (the pillar slot, phase 2) accept.
 * Nothing else tracks which records have been converted, and nothing else needs to.</p>
 */
class MaterialRolePaletteTest {

    private static final Gson GSON = new Gson();

    private static <A> DataResult<A> parse(Codec<A> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static <A> A decode(Codec<A> codec, String json) {
        DataResult<A> result = parse(codec, json);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected this to decode: " + result.error().orElseThrow().message() + " -- " + json));
    }

    private static <A> String errorOf(Codec<A> codec, String json) {
        return parse(codec, json).error()
                .orElseThrow(() -> new AssertionError("expected a load error, but this decoded: " + json))
                .message();
    }

    // ---- the palette itself ---------------------------------------------------------------------

    @Test
    void aMotifDeclaresRolesAndTheySurviveTheFold() {
        MotifConfig motif = fold("""
                {"palette": {"shaft": "dungeonblocks:stone_bricks_pillar_block",
                             "footing": "dungeonblocks:stone_bricks_pillar_base_block"}}""");
        assertEquals(Optional.of("dungeonblocks:stone_bricks_pillar_block"), motif.role("shaft"));
        assertEquals(Optional.empty(), motif.role("nosuchrole"),
                "an undeclared role is absent, for the caller to fail on rather than draw");
    }

    /**
     * Merged by key across a motif's files, like {@code template_limits} and unlike the depth tables.
     * A palette is a set of INDEPENDENT roles, so a second file retuning one must not wipe the rest
     * &mdash; that is what lets an addon repaint a motif from a file of its own.
     */
    @Test
    void fragmentsMergeThePaletteByRoleRatherThanReplacingIt() {
        MotifConfig motif = fold(
                """
                {"palette": {"shaft": "minecraft:stone_bricks", "stair": "minecraft:stone_brick_stairs"}}""",
                """
                {"palette": {"shaft": "minecraft:deepslate_bricks"}}""");
        assertEquals(Optional.of("minecraft:deepslate_bricks"), motif.role("shaft"), "later file wins");
        assertEquals(Optional.of("minecraft:stone_brick_stairs"), motif.role("stair"),
                "and leaves the roles it did not name alone");
    }

    /**
     * The band OVERLAYS. This is the one section that does not whole-replace, and it has to be: a
     * band typically repaints two or three roles (classic to mud is four), so replacing would make
     * it restate the entire vocabulary to change one entry.
     */
    @Test
    void aBandsPaletteOverlaysTheMotifsRatherThanReplacingIt() {
        MotifConfig motif = fold("""
                {"palette": {"shaft": "dungeonblocks:stone_bricks_pillar_block",
                             "stair": "minecraft:stone_brick_stairs",
                             "ornament": "minecraft:chiseled_stone_bricks"},
                 "strata_by_floor_index": [
                   {"min_floor_index": 0,
                    "palette": {"shaft": "minecraft:spruce_log",
                                "stair": "minecraft:mud_brick_stairs"}},
                   {"min_floor_index": 1}]}""");

        MotifConfig mud = motif.forFloor(0);
        assertEquals(Optional.of("minecraft:spruce_log"), mud.role("shaft"), "the band repaints");
        assertEquals(Optional.of("minecraft:mud_brick_stairs"), mud.role("stair"));
        assertEquals(Optional.of("minecraft:chiseled_stone_bricks"), mud.role("ornament"),
                "and inherits every role it did not name");

        MotifConfig below = motif.forFloor(1);
        assertEquals(Optional.of("dungeonblocks:stone_bricks_pillar_block"), below.role("shaft"),
                "a band declaring no palette leaves the motif's untouched");
    }

    @Test
    void aMotifWithNoPaletteIsSimplyEmpty() {
        assertEquals(Map.of(), fold("{}").palette());
        assertEquals(Optional.empty(), fold("{}").role("shaft"));
    }

    // ---- palette validation ---------------------------------------------------------------------

    /**
     * The sigil belongs at the USE site, not the declaration. Writing it on both is the likelier
     * slip and would define a role named {@code $shaft} that {@code "$shaft"} never matches.
     */
    @Test
    void declaringARoleWithTheSigilIsALoadError() {
        String message = errorOf(MotifConfigFragment.CODEC,
                "{\"palette\": {\"$shaft\": \"minecraft:stone_bricks\"}}");
        assertTrue(message.contains("$shaft"), () -> message);
        assertTrue(message.contains("\"shaft\""), () -> "and should show the intended spelling: " + message);
    }

    /** A dot groups a coordinated set -- a beam and the bracket that carries it -- and is legal. */
    @Test
    void aDottedRoleNameIsLegalBecauseASetIsNamedTogether() {
        MotifConfig motif = fold("""
                {"palette": {"joist.beam": "minecraft:spruce_log",
                             "joist.bracket": "dungeonblocks:spruce_corbel_block"}}""");
        assertEquals(Optional.of("minecraft:spruce_log"), motif.role("joist.beam"));
        assertEquals(Optional.of("dungeonblocks:spruce_corbel_block"), motif.role("joist.bracket"));
    }

    @Test
    void aRoleNameThatIsNotUsableIsALoadError() {
        assertTrue(errorOf(MotifConfigFragment.CODEC,
                "{\"palette\": {\"joist beam\": \"minecraft:spruce_log\"}}").contains("joist beam"));
    }

    /** A role pointing at a role is indirection nobody asked for, and a cycle nothing checks. */
    @Test
    void aPaletteValueThatIsItselfARoleIsALoadError() {
        assertTrue(errorOf(MotifConfigFragment.CODEC,
                "{\"palette\": {\"shaft\": \"$footing\"}}").contains("$footing"));
    }

    // ---- reject mode: no block field reads a role yet --------------------------------------------

    /**
     * All forty-nine block-valued fields across twenty-six records, one at a time. Exhaustive on
     * purpose &mdash; see the class doc: a field that misses {@code BLOCK_ID} fails silently and
     * only while the conversion is half done, so there is no later moment at which this gets caught.
     */
    @Test
    void everyBlockFieldRejectsARole() {
        // floor patterns (5 records, 9 fields)
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:border\",\"config\":{\"corner_block\":\"$r\",\"edge_left_block\":\"minecraft:stone\",\"edge_right_block\":\"minecraft:stone\"}}");
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:border\",\"config\":{\"corner_block\":\"minecraft:stone\",\"edge_left_block\":\"$r\",\"edge_right_block\":\"minecraft:stone\"}}");
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:border\",\"config\":{\"corner_block\":\"minecraft:stone\",\"edge_left_block\":\"minecraft:stone\",\"edge_right_block\":\"$r\"}}");
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:checkerboard\",\"config\":{\"primary_block\":\"$r\",\"secondary_block\":\"minecraft:stone\"}}");
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:checkerboard\",\"config\":{\"primary_block\":\"minecraft:stone\",\"secondary_block\":\"$r\"}}");
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:speckle\",\"config\":{\"primary_block\":\"$r\",\"secondary_block\":\"minecraft:stone\"}}");
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:speckle\",\"config\":{\"primary_block\":\"minecraft:stone\",\"secondary_block\":\"$r\"}}");
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:cross\",\"config\":{\"block\":\"$r\"}}");
        rejects(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:spokes\",\"config\":{\"block\":\"$r\"}}");

        // wall patterns (4 records, 8 fields) -- pilasters and end_pilasters share PilasterShape
        rejects(WallPatternEntry.PatternEntry.CODEC, "{\"type\":\"dungeons2:panels\",\"config\":{\"block\":\"$r\"}}");
        rejects(WallPatternEntry.PatternEntry.CODEC, "{\"type\":\"dungeons2:diamond\",\"config\":{\"block\":\"$r\"}}");
        rejects(WallPatternEntry.PatternEntry.CODEC, "{\"type\":\"dungeons2:gradient\",\"config\":{\"bottom_block\":\"$r\",\"top_block\":\"minecraft:stone\"}}");
        rejects(WallPatternEntry.PatternEntry.CODEC, "{\"type\":\"dungeons2:gradient\",\"config\":{\"bottom_block\":\"minecraft:stone\",\"top_block\":\"$r\"}}");
        rejects(WallPatternEntry.PatternEntry.CODEC, "{\"type\":\"dungeons2:pilasters\",\"config\":{\"block\":\"$r\"}}");
        rejects(WallPatternEntry.PatternEntry.CODEC, "{\"type\":\"dungeons2:pilasters\",\"config\":{\"block\":\"minecraft:stone\",\"base_block\":\"$r\"}}");
        rejects(WallPatternEntry.PatternEntry.CODEC, "{\"type\":\"dungeons2:pilasters\",\"config\":{\"block\":\"minecraft:stone\",\"cap_block\":\"$r\"}}");
        rejects(WallPatternEntry.CourseEntry.CODEC, "{\"block\":\"$r\"}");
        rejects(WallPatternEntry.CourseEntry.CODEC, "{\"block\":\"minecraft:stone\",\"alternate_block\":\"$r\"}");
        rejects(WallPatternEntry.CourseEntry.CODEC, "{\"block\":\"minecraft:stone\",\"corner_block\":\"$r\"}");

        // ceiling patterns (4 records, 6 fields)
        rejects(CeilingPatternEntry.SurfacePatternEntry.CODEC, "{\"type\":\"dungeons2:border\",\"config\":{\"block\":\"$r\"}}");
        rejects(CeilingPatternEntry.SurfacePatternEntry.CODEC, "{\"type\":\"dungeons2:border\",\"config\":{\"block\":\"minecraft:stone\",\"corner_block\":\"$r\"}}");
        rejects(CeilingPatternEntry.SurfacePatternEntry.CODEC, "{\"type\":\"dungeons2:centre\",\"config\":{\"block\":\"$r\"}}");
        rejects(CeilingPatternEntry.SurfacePatternEntry.CODEC, "{\"type\":\"dungeons2:coffers\",\"config\":{\"block\":\"$r\"}}");
        rejects(CeilingPatternEntry.SurfacePatternEntry.CODEC, "{\"type\":\"dungeons2:joists\",\"config\":{\"block\":\"$r\"}}");
        rejects(CeilingPatternEntry.SurfacePatternEntry.CODEC, "{\"type\":\"dungeons2:joists\",\"config\":{\"block\":\"minecraft:stone\",\"bracket_block\":\"$r\"}}");

        // platforms (4)
        // pillars (3) are CONVERTED -- phase 2. See acceptsARoleOnEveryConvertedField below; the
        // move of these three lines from this list to that one is what a phase IS.
        rejects(PlatformPatternEntry.PlatformEntry.CODEC, "{\"type\":\"dais\",\"layout\":\"dungeons2:centre\",\"block\":\"$r\"}");
        rejects(PlatformPatternEntry.PlatformEntry.CODEC, "{\"type\":\"dais\",\"layout\":\"dungeons2:centre\",\"block\":\"minecraft:stone\",\"stair_block\":\"$r\"}");
        rejects(PlatformPatternEntry.PlatformEntry.CODEC, "{\"type\":\"dais\",\"layout\":\"dungeons2:centre\",\"block\":\"minecraft:stone\",\"centre_block\":\"$r\"}");
        rejects(PlatformPatternEntry.PlatformEntry.CODEC, "{\"type\":\"dais\",\"layout\":\"dungeons2:centre\",\"block\":\"minecraft:stone\",\"top_block\":\"$r\"}");

        // pit (3 records, 4 fields)
        rejects(PitPatternEntry.CODEC, "{\"type\":\"dungeons2:centre\",\"floor_block\":\"$r\"}");
        rejects(PitPatternEntry.CODEC, "{\"type\":\"dungeons2:centre\",\"config\":{\"rim_block\":\"$r\"}}");
        rejects(PitPatternEntry.CODEC, "{\"type\":\"dungeons2:hazard\",\"config\":{\"spike_block\":\"$r\"}}");
        rejects(PitPatternEntry.CODEC, "{\"type\":\"dungeons2:hazard\",\"config\":{\"rim_block\":\"$r\"}}");

        // chest variant (1)
        rejects(ChestConfig.CODEC, "{\"variants\":[{\"block\":\"$r\"}]}");

        // the shell and the corridor (12) -- phase 7, but guarded from now
        rejects(WallConfig.CODEC, "{\"wall\":\"$r\"}");
        rejects(CeilingConfig.CODEC, "{\"ceiling\":\"$r\"}");
        rejects(FloorConfig.CODEC, "{\"base\":\"$r\",\"alternate_base\":\"minecraft:stone\"}");
        rejects(FloorConfig.CODEC, "{\"base\":\"minecraft:stone\",\"alternate_base\":\"$r\"}");
        rejects(DoorConfig.CODEC, "{\"door\":\"$r\",\"lintel\":\"minecraft:stone\",\"floor\":\"minecraft:stone\"}");
        rejects(DoorConfig.CODEC, "{\"door\":\"minecraft:oak_door\",\"lintel\":\"$r\",\"floor\":\"minecraft:stone\"}");
        rejects(DoorConfig.CODEC, "{\"door\":\"minecraft:oak_door\",\"lintel\":\"minecraft:stone\",\"floor\":\"$r\"}");
        rejects(CorridorConfig.CODEC, "{\"floor\":\"$r\",\"alternate_floor\":\"minecraft:stone\",\"ceiling\":\"minecraft:stone\"}");
        rejects(CorridorConfig.CODEC, "{\"floor\":\"minecraft:stone\",\"alternate_floor\":\"$r\",\"ceiling\":\"minecraft:stone\"}");
        rejects(CorridorConfig.CODEC, "{\"floor\":\"minecraft:stone\",\"alternate_floor\":\"minecraft:stone\",\"ceiling\":\"$r\"}");
        rejects(CorridorConfig.CODEC, "{\"floor\":\"minecraft:stone\",\"alternate_floor\":\"minecraft:stone\",\"ceiling\":\"minecraft:stone\",\"profile\":\"arched\",\"arch_block\":\"$r\"}");
        rejects(CorridorStyle.CODEC, "{\"name\":\"s\",\"profile\":\"arched\",\"arch_block\":\"$r\"}");
    }

    /** The error has to name the role, or the author is hunting a string across a large file. */
    @Test
    void theRejectionNamesTheRoleAndSaysWhy() {
        String message = errorOf(WallConfig.CODEC, "{\"wall\":\"$shaft\"}");
        assertTrue(message.contains("$shaft"), () -> message);
        assertTrue(message.contains("material role"), () -> message);
        assertTrue(message.contains("literal block id"), () -> "and say what to write instead: " + message);
    }

    /** Whitespace does not smuggle one past: {@code blockOrNull} trims, so this check must too. */
    @Test
    void aRoleWithLeadingWhitespaceIsStillRejected() {
        assertTrue(parse(WallConfig.CODEC, "{\"wall\":\"  $shaft\"}").error().isPresent());
    }

    /** And the whole point of reject mode is that literals are untouched. */
    @Test
    void aLiteralBlockIdIsUnaffected() {
        assertEquals("minecraft:stone_bricks", decode(WallConfig.CODEC,
                "{\"wall\":\"minecraft:stone_bricks\"}").wall());
        // A block id containing a $ anywhere but the front was never legal anyway; only the
        // leading position is claimed.
        assertEquals("minecraft:cobblestone", decode(CeilingConfig.CODEC,
                "{\"ceiling\":\"minecraft:cobblestone\"}").ceiling());
    }

    private static void rejects(Codec<?> codec, String json) {
        String message = errorOf(codec, json);
        assertTrue(message.contains("material role"),
                () -> "this should have been rejected as a role, but failed for another reason: "
                        + message + " -- " + json);
    }

    private static MotifConfig fold(String... fragments) {
        return MotifConfigFragment.resolve(java.util.Arrays.stream(fragments)
                .map(json -> decode(MotifConfigFragment.CODEC, json))
                .toList());
    }
}
