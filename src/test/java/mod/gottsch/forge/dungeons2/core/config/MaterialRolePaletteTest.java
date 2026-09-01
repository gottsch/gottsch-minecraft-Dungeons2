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
 * #65 &mdash; the material-role palette: how it is declared, merged, overlaid and validated.
 *
 * <p>Phase 1 shipped the palette with <strong>no consumers</strong>, and put
 * {@link Codecs#BLOCK_ID} on every block field in reject mode to make that safe: roles arrived one
 * record at a time, and an unconverted field would otherwise have handed {@code "$shaft"} to
 * {@code BlockStateCodec#blockOrNull}, got {@code null} back, and drawn <strong>nothing</strong> --
 * no error, no log line, a dressed wall coming out plain, and only in the half-converted state where
 * nobody is looking. That state is over; the reasoning is kept because it is why the phases could be
 * shipped one at a time at all.</p>
 *
 * <p><strong>As of phase 7 this class is history plus one live rule.</strong> All forty-nine block
 * fields accept a role; the exhaustive rejection list this test used to hold is gone, replaced by
 * {@link #everyBlockFieldNowAcceptsARoleAndOnlyThePaletteRejectsOne}, which pins the inverse. The
 * palette's own validation is the part that still matters day to day, and it is all above.</p>
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

    // ---- reject mode, now down to one user ------------------------------------------------------

    /**
     * <strong>Reject mode has exactly one user left, and it is not a record field.</strong>
     *
     * <p>Phases 2-7 converted all forty-nine block fields, so this test replaces the exhaustive
     * rejection list it used to hold. What that list guarded was the half-converted state: a field
     * still on {@link Codecs#BLOCK_ID} would hand {@code "$shaft"} to
     * {@code BlockStateCodec#blockOrNull} and draw nothing. There is no half-converted state now,
     * so the guard inverts &mdash; a NEW field added on {@code BLOCK_ID} would be the inconsistency,
     * and an author would have to remember that one field of forty-nine is special.</p>
     *
     * <p>The one place reject mode still belongs is a palette VALUE: a role resolving to another
     * role is indirection nobody asked for and a cycle nothing checks. {@link #aPaletteValueThatIsItselfARoleIsALoadError}
     * covers that, and this test states the rule the codebase now holds to.</p>
     */
    @Test
    void everyBlockFieldNowAcceptsARoleAndOnlyThePaletteRejectsOne() {
        // One representative per converted family. The exhaustive per-field coverage lives in the
        // MaterialRoles*Test classes, which assert the substitution as well as the decode.
        decodes(FloorPatternEntry.CODEC, "{\"type\":\"dungeons2:cross\",\"config\":{\"block\":\"$r\"}}");
        decodes(WallPatternEntry.CourseEntry.CODEC, "{\"block\":\"$r\"}");
        decodes(CeilingPatternEntry.SurfacePatternEntry.CODEC,
                "{\"type\":\"dungeons2:coffers\",\"config\":{\"block\":\"$r\"}}");
        decodes(PillarPatternEntry.PillarEntry.CODEC, "{\"type\":\"dungeons2:centre\",\"block\":\"$r\"}");
        decodes(PlatformPatternEntry.PlatformEntry.CODEC,
                "{\"type\":\"dais\",\"layout\":\"dungeons2:centre\",\"block\":\"$r\"}");
        decodes(PitPatternEntry.CODEC, "{\"type\":\"dungeons2:centre\",\"floor_block\":\"$r\"}");
        decodes(ChestConfig.CODEC, "{\"variants\":[{\"block\":\"$r\"}]}");
        // ...and the shell, phase 7.
        decodes(WallConfig.CODEC, "{\"wall\":\"$r\"}");
        decodes(CeilingConfig.CODEC, "{\"ceiling\":\"$r\"}");
        decodes(FloorConfig.CODEC, "{\"base\":\"$r\",\"alternate_base\":\"$r\"}");
        decodes(DoorConfig.CODEC,
                "{\"door\":\"$r\",\"lintel\":\"$r\",\"floor\":\"$r\"}");
        decodes(CorridorConfig.CODEC,
                "{\"floor\":\"$r\",\"alternate_floor\":\"$r\",\"ceiling\":\"$r\"}");
        decodes(CorridorStyle.CODEC, "{\"name\":\"s\",\"profile\":\"arched\",\"height\":7,\"arch_block\":\"$r\"}");

        // The one remaining rejection, and the reason it stays.
        assertTrue(parse(MotifConfigFragment.CODEC,
                "{\"palette\": {\"shaft\": \"$footing\"}}").error().isPresent(),
                "a palette value must be a literal; a role pointing at a role is a cycle "
                        + "nothing checks");
    }

    private static <A> void decodes(Codec<A> codec, String json) {
        DataResult<A> result = parse(codec, json);
        assertTrue(result.result().isPresent(),
                () -> "every block field accepts a role since phase 7, but this did not: "
                        + result.error().map(DataResult.PartialResult::message).orElse("")
                        + " -- " + json);
    }

    /**
     * The error has to name the role, or the author is hunting a string across a large file.
     *
     * <p>Pointed at a palette VALUE since phase 7. It used to test a shell field
     * ({@code "wall": "$shaft"}), which was the last kind of field still in reject mode; that field
     * now accepts a role like every other, so the assertion moved to the one place rejection
     * remains rather than being deleted.</p>
     */
    @Test
    void theRejectionNamesTheRoleAndSaysWhy() {
        String message = errorOf(MotifConfigFragment.CODEC,
                "{\"palette\": {\"shaft\": \"$footing\"}}");
        assertTrue(message.contains("$footing"), () -> message);
        assertTrue(message.contains("material role"), () -> message);
        assertTrue(message.contains("literal block id"), () -> "and say what to write instead: " + message);
    }

    /** Whitespace does not smuggle one past: {@code blockOrNull} trims, so this check must too. */
    @Test
    void aRoleWithLeadingWhitespaceIsStillRejected() {
        assertTrue(parse(MotifConfigFragment.CODEC,
                "{\"palette\": {\"shaft\": \"  $footing\"}}").error().isPresent());
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
