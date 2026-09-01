package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #65 phase 2 &mdash; the {@code pillars} slot reads a material role, and the walk that resolves it.
 *
 * <p>The first slot converted, chosen because it is the cheapest end-to-end proof: one record,
 * three fields, and a shipped scheme ({@code mud_timber_centre_pillar}) already shaped exactly like
 * {@code $shaft} + {@code $footing}. If the architecture is wrong this is where it shows, for half a
 * day's work rather than four.</p>
 *
 * <p>Three properties are worth pinning separately, and only the first is obvious:</p>
 * <ol>
 *   <li><strong>The same authored scheme paints differently per band.</strong> That is the whole
 *       feature &mdash; one option library serving every motif instead of being re-authored.</li>
 *   <li><strong>An unresolvable role is a LOAD ERROR.</strong> The substitution itself is silent by
 *       necessity (it runs during worldgen, where throwing kills a chunk), so the check has to
 *       happen at fold time or the failure mode is a column that quietly is not there.</li>
 *   <li><strong>A motif that names no role allocates nothing.</strong> The walk is on the per-piece
 *       path, so the unconverted case has to come out of it by identity.</li>
 * </ol>
 */
class MaterialRolesPillarsTest {

    private static final Gson GSON = new Gson();

    private static final String PILLARS = """
            "pillars": {"patterns": [{"type": "dungeons2:centre",
                                      "block": "$shaft",
                                      "base_block": "$footing",
                                      "cap_block": "$footing"}]}""";

    // ---- the feature ----------------------------------------------------------------------------

    /**
     * One scheme, two bands, two materials. Written with literals this is two schemes that differ
     * only in their block ids, which is exactly the duplication roles exist to remove.
     */
    @Test
    void oneAuthoredSchemeIsPaintedByWhicheverBandDrawsIt() {
        MotifConfig motif = fold("""
                {"palette": {"shaft": "dungeonblocks:stone_bricks_pillar_block",
                             "footing": "dungeonblocks:stone_bricks_pillar_base_block"},
                 "schemes": [{"name": "pier", %s}],
                 "strata_by_floor_index": [
                   {"min_floor_index": 0,
                    "palette": {"shaft": "minecraft:spruce_log",
                                "footing": "dungeonblocks:square_stone_brick"}},
                   {"min_floor_index": 1}]}""".formatted(PILLARS));

        PillarPatternEntry.PillarEntry mud = onlyColumn(motif.forFloor(0));
        assertEquals("minecraft:spruce_log", mud.block(), "the mud band's timber pier");
        assertEquals(Optional.of("dungeonblocks:square_stone_brick"), mud.baseBlock());
        assertEquals(Optional.of("dungeonblocks:square_stone_brick"), mud.capBlock());

        PillarPatternEntry.PillarEntry below = onlyColumn(motif.forFloor(1));
        assertEquals("dungeonblocks:stone_bricks_pillar_block", below.block(),
                "and the same scheme in dressed stone one floor down");
        assertEquals(Optional.of("dungeonblocks:stone_bricks_pillar_base_block"), below.baseBlock());
    }

    /** A literal beside a role in the same column is left exactly as authored. */
    @Test
    void aLiteralBesideARoleIsUntouched() {
        MotifConfig motif = fold("""
                {"palette": {"shaft": "minecraft:spruce_log"},
                 "schemes": [{"name": "pier", "pillars": {"patterns": [
                     {"type": "dungeons2:centre",
                      "block": "$shaft",
                      "base_block": "dungeonblocks:square_stone_brick"}]}}],
                 "strata_by_floor_index": [{"min_floor_index": 0}]}""");
        PillarPatternEntry.PillarEntry column = onlyColumn(motif.forFloor(0));
        assertEquals("minecraft:spruce_log", column.block());
        assertEquals(Optional.of("dungeonblocks:square_stone_brick"), column.baseBlock());
    }

    /** Every option of an unresolved slot is walked, not merely the one a room would roll. */
    @Test
    void everyAlternativeInAnOptionListIsPainted() {
        MotifConfig motif = fold("""
                {"palette": {"shaft": "minecraft:spruce_log", "footing": "minecraft:stone"},
                 "schemes": [{"name": "pier", "pillars": [
                     {"weight": 1, "patterns": [{"type": "dungeons2:centre", "block": "$shaft"}]},
                     {"weight": 1, "patterns": [{"type": "dungeons2:grid",   "block": "$footing"}]},
                     {"weight": 1, "none": true}]}],
                 "strata_by_floor_index": [{"min_floor_index": 0}]}""");
        List<String> blocks = new ArrayList<>();
        byName(motif.forFloor(0), "pier").pillars().all()
                .forEach(entry -> entry.patterns().forEach(column -> blocks.add(column.block())));
        assertEquals(List.of("minecraft:spruce_log", "minecraft:stone"), blocks,
                "the slot is not resolved until the scheme is rolled, so the walk must see both");
    }

    /**
     * A motif with a palette and <strong>no bands at all</strong>. {@code forFloor}
     * short-circuits on an empty strata table -- an early return that predates the palette
     * and, left alone, handed {@code $shaft} straight to the generator, which draws nothing.
     * Roles are not only for bands.
     */
    @Test
    void aMotifWithNoBandsStillResolvesItsOwnRoles() {
        MotifConfig motif = fold("""
                {"palette": {"shaft": "minecraft:spruce_log"},
                 "schemes": [{"name": "pier", "pillars": {"patterns": [
                     {"type": "dungeons2:centre", "block": "$shaft"}]}}]}""");
        assertEquals("minecraft:spruce_log", onlyColumn(motif.forFloor(0)).block());
        assertEquals("minecraft:spruce_log", onlyColumn(motif.forFloor(7)).block());
    }

    // ---- the load error --------------------------------------------------------------------------

    /**
     * The substitution cannot fail loudly &mdash; it runs inside worldgen, where throwing takes the
     * chunk with it &mdash; so a role nothing declares has to be caught at fold time, or the whole
     * failure mode is a column that silently is not there.
     */
    @Test
    void aRoleNoPaletteDeclaresIsReportedAtLoad() {
        List<String> problems = foldProblems("""
                {"palette": {"shaft": "minecraft:spruce_log"},
                 "schemes": [{"name": "pier", %s}]}""".formatted(PILLARS));
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$footing"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("pier"), () -> "and name the scheme: " + problems.get(0));
        assertTrue(problems.get(0).contains("shaft"),
                () -> "and list what IS declared: " + problems.get(0));
    }

    /**
     * <strong>Per band.</strong> A role only a band declares leaves the motif's own scheme list
     * unanswerable, and a check that looked only at the motif's palette would pass a pack that draws
     * nothing below the band. This is the case that makes the check per-band rather than global.
     */
    @Test
    void aRoleOnlyOneBandDeclaresIsReportedForTheBandsThatDoNot() {
        List<String> problems = foldProblems("""
                {"schemes": [{"name": "pier", "pillars": {"patterns": [
                     {"type": "dungeons2:centre", "block": "$shaft"}]}}],
                 "strata_by_floor_index": [
                   {"min_floor_index": 0, "palette": {"shaft": "minecraft:spruce_log"}}]}""");
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$shaft"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("No palette is declared at all"),
                () -> "the motif's own list has no palette to resolve against: " + problems.get(0));
    }

    /** A band's own scheme is checked against the band's palette, and passes on it. */
    @Test
    void aBandsSchemeResolvesAgainstTheBandsPalette() {
        assertEquals(List.of(), foldProblems("""
                {"strata_by_floor_index": [
                   {"min_floor_index": 0,
                    "palette": {"shaft": "minecraft:spruce_log"},
                    "schemes": [{"name": "pier", "pillars": {"patterns": [
                        {"type": "dungeons2:centre", "block": "$shaft"}]}}]}]}"""));
    }

    /** A malformed role name fails at DECODE, before any palette is consulted. */
    @Test
    void aMalformedRoleNameIsALoadError() {
        DataResult<PillarPatternEntry.PillarEntry> result =
                parse(PillarPatternEntry.PillarEntry.CODEC,
                        "{\"type\": \"dungeons2:centre\", \"block\": \"$joist beam\"}");
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("joist beam"),
                () -> result.error().orElseThrow().message());
    }

    // ---- the per-piece path ----------------------------------------------------------------------

    /**
     * {@code forFloor} runs once per room piece per chunk, so an unconverted motif has to come out
     * of the role walk <strong>by identity</strong>. Not a micro-optimisation: it is why the walk
     * could be put on that path at all instead of needing a cache.
     */
    @Test
    void aSchemeThatNamesNoRoleIsNotEvenCopied() {
        RoomScheme literal = decode(RoomScheme.CODEC, """
                {"name": "pier", "pillars": {"patterns": [
                    {"type": "dungeons2:centre", "block": "minecraft:spruce_log"}]}}""");
        assertSame(literal, literal.withRoles(role -> "minecraft:stone"));
    }

    /** And a slot the author left empty is nothing to walk either. */
    @Test
    void aSchemeWithNoPillarSlotIsNotEvenCopied() {
        RoomScheme bare = decode(RoomScheme.CODEC, "{\"name\": \"plain\", \"weight\": 3}");
        assertSame(bare, bare.withRoles(role -> "minecraft:stone"));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static PillarPatternEntry.PillarEntry onlyColumn(MotifConfig motif) {
        List<PillarPatternEntry.PillarEntry> columns =
                byName(motif, "pier").pillars().orElseThrow().patterns();
        assertEquals(1, columns.size());
        return columns.get(0);
    }

    private static RoomScheme byName(MotifConfig motif, String name) {
        return motif.schemes().stream().filter(scheme -> name.equals(scheme.name())).findFirst()
                .orElseThrow(() -> new AssertionError("no scheme '" + name + "' in " + motif.schemes()));
    }

    private static MotifConfig fold(String json) {
        List<String> problems = new ArrayList<>();
        MotifConfig motif = MotifConfigFragment.resolve(
                List.of(decode(MotifConfigFragment.CODEC, json)), problems::add);
        assertEquals(List.of(), problems, "expected this motif to fold cleanly");
        return motif;
    }

    private static List<String> foldProblems(String json) {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC, json)), problems::add);
        return problems;
    }

    private static <A> DataResult<A> parse(Codec<A> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static <A> A decode(Codec<A> codec, String json) {
        DataResult<A> result = parse(codec, json);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected this to decode: " + result.error().orElseThrow().message() + " -- " + json));
    }
}
