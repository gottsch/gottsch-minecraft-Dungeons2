package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.floor.BorderFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.CheckerboardFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.CompositeFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.CrossFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.PlainFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.SpeckleFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.SpokesFloorPattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #65 phase 3 &mdash; the {@code floor} slot reads a material role, across all five pattern types
 * that carry a block, and through {@code composite}'s nesting.
 *
 * <p>Second on purpose. {@code CompositeFloorPattern} holds other patterns, so it is the only place
 * in the whole walk that recurses &mdash; the one remaining shape the phase-2 architecture had not
 * been asked to handle. Better found here, on the second slot, than on the fifth.</p>
 *
 * <p>The per-type tests are dull and there are five of them. That is the point: the substitution is
 * written out once per record by hand, so the thing worth pinning is that <strong>no field was
 * missed</strong> &mdash; a pattern whose {@code corner_block} resolves and whose
 * {@code edge_right_block} does not is a floor that draws two-thirds correctly, which reads as an
 * authoring mistake rather than a bug.</p>
 */
class MaterialRolesFloorTest {

    private static final Gson GSON = new Gson();

    private static final String PALETTE =
            "\"palette\": {\"a\": \"minecraft:stone\", \"b\": \"minecraft:cobblestone\","
                    + " \"c\": \"minecraft:andesite\"}";

    // ---- every field of every type ---------------------------------------------------------------

    @Test
    void aBorderResolvesAllThreeOfItsBlocks() {
        BorderFloorPattern border = (BorderFloorPattern) resolved(
                "{\"type\": \"dungeons2:border\", \"config\": {"
                        + "\"corner_block\": \"$a\", \"edge_left_block\": \"$b\","
                        + " \"edge_right_block\": \"$c\"}}");
        assertEquals("minecraft:stone", border.cornerBlock());
        assertEquals("minecraft:cobblestone", border.edgeLeftBlock());
        assertEquals("minecraft:andesite", border.edgeRightBlock());
    }

    @Test
    void aCheckerboardResolvesBothOfItsBlocks() {
        CheckerboardFloorPattern board = (CheckerboardFloorPattern) resolved(
                "{\"type\": \"dungeons2:checkerboard\", \"config\": {"
                        + "\"primary_block\": \"$a\", \"secondary_block\": \"$b\"}}");
        assertEquals("minecraft:stone", board.primaryBlock());
        assertEquals("minecraft:cobblestone", board.secondaryBlock());
    }

    /** And keeps the field that is not a block: a resolved record must not reset its own tuning. */
    @Test
    void aSpeckleResolvesBothBlocksAndKeepsItsProbability() {
        SpeckleFloorPattern speckle = (SpeckleFloorPattern) resolved(
                "{\"type\": \"dungeons2:speckle\", \"config\": {"
                        + "\"primary_block\": \"$a\", \"secondary_block\": \"$b\","
                        + " \"probability\": 0.25}}");
        assertEquals("minecraft:stone", speckle.primaryBlock());
        assertEquals("minecraft:cobblestone", speckle.secondaryBlock());
        assertEquals(0.25D, speckle.probability(), 1.0e-9,
                "rebuilding the record must carry every field it did not resolve");
    }

    @Test
    void aCrossResolvesItsBlockAndKeepsItsThickness() {
        CrossFloorPattern cross = (CrossFloorPattern) resolved(
                "{\"type\": \"dungeons2:cross\", \"config\": {\"block\": \"$a\", \"thickness\": 3}}");
        assertEquals("minecraft:stone", cross.block());
        assertEquals(3, cross.thickness());
    }

    @Test
    void aSpokesResolvesItsBlockAndKeepsItsCount() {
        SpokesFloorPattern spokes = (SpokesFloorPattern) resolved(
                "{\"type\": \"dungeons2:spokes\", \"config\": {\"block\": \"$a\", \"spokes\": 6}}");
        assertEquals("minecraft:stone", spokes.block());
        assertEquals(6, spokes.spokes());
    }

    // ---- the recursion ---------------------------------------------------------------------------

    /**
     * The one recursion in the walk. {@code joisted_hall_2} ships a composite of a checkerboard
     * under a border, which is four block fields nested one level down.
     */
    @Test
    void aCompositeResolvesEveryGeneratorInsideIt() {
        CompositeFloorPattern composite = (CompositeFloorPattern) resolved(
                "{\"type\": \"dungeons2:composite\", \"config\": {\"generators\": ["
                        + "{\"type\": \"dungeons2:checkerboard\", \"config\": {"
                        + "  \"primary_block\": \"$a\", \"secondary_block\": \"$b\"}},"
                        + "{\"type\": \"dungeons2:cross\", \"config\": {\"block\": \"$c\"}}]}}");
        assertEquals(List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:andesite"),
                blocksOf(composite));
    }

    /** Nesting is not depth-limited, and a composite inside a composite is legal JSON. */
    @Test
    void aCompositeInsideACompositeIsResolvedAllTheWayDown() {
        CompositeFloorPattern composite = (CompositeFloorPattern) resolved(
                "{\"type\": \"dungeons2:composite\", \"config\": {\"generators\": ["
                        + "{\"type\": \"dungeons2:composite\", \"config\": {\"generators\": ["
                        + "  {\"type\": \"dungeons2:cross\", \"config\": {\"block\": \"$a\"}}]}},"
                        + "{\"type\": \"dungeons2:spokes\", \"config\": {\"block\": \"$b\"}}]}}");
        assertEquals(List.of("minecraft:stone", "minecraft:cobblestone"), blocksOf(composite));
    }

    /** A composite of literals is not rebuilt, and neither is the list inside it. */
    @Test
    void aCompositeOfLiteralsIsNotEvenCopied() {
        FloorPattern composite = decode(FloorPatternEntry.CODEC,
                "{\"type\": \"dungeons2:composite\", \"config\": {\"generators\": ["
                        + "{\"type\": \"dungeons2:cross\", \"config\": {\"block\": \"minecraft:stone\"}}]}}")
                .pattern();
        assertSame(composite, composite.withRoles(role -> "minecraft:dirt"));
    }

    // ---- the type with no blocks -----------------------------------------------------------------

    /**
     * {@code plain} declares no block at all, so it takes {@link FloorPattern}'s default. That
     * default is what keeps the registry open to other mods &mdash; an abstract method here would
     * break every third-party pattern on upgrade &mdash; and it is safe because a field only ever
     * carries a role if its codec is {@code BLOCK_ID_OR_ROLE}; the default is {@code BLOCK_ID},
     * which rejects one at load.
     */
    @Test
    void aPatternWithNoBlocksTakesTheDefaultAndIsUnchanged() {
        FloorPattern plain = PlainFloorPattern.INSTANCE;
        assertSame(plain, plain.withRoles(role -> "minecraft:dirt"));
    }

    // ---- through the scheme ----------------------------------------------------------------------

    /** End to end: a band repaints a floor pattern the same way it repaints a column. */
    @Test
    void aBandRepaintsTheFloorSlot() {
        MotifConfig motif = fold("""
                {"palette": {"paving": "dungeonblocks:square_stone_brick"},
                 "schemes": [{"name": "paved", "floor":
                     {"type": "dungeons2:cross", "config": {"block": "$paving"}}}],
                 "strata_by_floor_index": [
                   {"min_floor_index": 0, "palette": {"paving": "minecraft:cobblestone"}},
                   {"min_floor_index": 1}]}""");
        assertEquals("minecraft:cobblestone", crossBlock(motif.forFloor(0)));
        assertEquals("dungeonblocks:square_stone_brick", crossBlock(motif.forFloor(1)));
    }

    /** A role in a floor pattern is checked at load like any other. */
    @Test
    void anUndeclaredRoleInAFloorPatternIsReportedAtLoad() {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC, """
                {"schemes": [{"name": "paved", "floor":
                    {"type": "dungeons2:cross", "config": {"block": "$paving"}}}]}""")),
                problems::add);
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$paving"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("paved"), () -> problems.get(0));
    }

    // ---- the SECOND place a FloorPatternEntry lives ---------------------------------------------

    /**
     * A {@link FloorPatternEntry} sits in a scheme's {@code floor} slot <strong>and</strong> in
     * the {@code floor} SECTION's {@code pattern}. Converting the record made a role authorable
     * in both at once, and a walk that only visited schemes would have left a role written here
     * decoding cleanly and then drawing nothing. The mud band ships a {@code speckle} in exactly
     * this position, so it is not a hypothetical path.
     */
    @Test
    void theFloorSECTIONsOwnPatternIsResolvedToo() {
        MotifConfig motif = fold("""
                {"palette": {"speck": "minecraft:packed_mud"},
                 "floor": {"base": "minecraft:cobblestone",
                           "alternate_base": "minecraft:cobblestone",
                           "pattern": {"type": "dungeons2:speckle",
                                       "config": {"primary_block": "$speck",
                                                  "secondary_block": "minecraft:cobblestone"}}}}""");
        SpeckleFloorPattern speckle = (SpeckleFloorPattern)
                motif.forFloor(0).floor().pattern().orElseThrow().pattern();
        assertEquals("minecraft:packed_mud", speckle.primaryBlock());
    }

    /** And a band that repaints the section gets the band's palette, not the motif's. */
    @Test
    void aBandsOwnFloorSectionIsResolvedAgainstTheBandsPalette() {
        MotifConfig motif = fold("""
                {"palette": {"speck": "minecraft:andesite"},
                 "strata_by_floor_index": [
                   {"min_floor_index": 0,
                    "palette": {"speck": "minecraft:packed_mud"},
                    "floor": {"base": "minecraft:cobblestone",
                              "alternate_base": "minecraft:cobblestone",
                              "pattern": {"type": "dungeons2:speckle",
                                          "config": {"primary_block": "$speck",
                                                     "secondary_block": "minecraft:cobblestone"}}}}]}""");
        SpeckleFloorPattern speckle = (SpeckleFloorPattern)
                motif.forFloor(0).floor().pattern().orElseThrow().pattern();
        assertEquals("minecraft:packed_mud", speckle.primaryBlock());
    }

    /** A motif with no schemes at all still has its floor section checked. */
    @Test
    void anUndeclaredRoleInTheFloorSectionIsReportedAtLoad() {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC, """
                {"floor": {"base": "minecraft:cobblestone",
                           "alternate_base": "minecraft:cobblestone",
                           "pattern": {"type": "dungeons2:cross",
                                       "config": {"block": "$paving"}}}}""")),
                problems::add);
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$paving"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("floor section"),
                () -> "and say WHERE, since there is no scheme to name: " + problems.get(0));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Every block named anywhere in a composite, in walk order. */
    private static List<String> blocksOf(FloorPattern pattern) {
        List<String> blocks = new ArrayList<>();
        collect(pattern, blocks);
        return blocks;
    }

    private static void collect(FloorPattern pattern, List<String> into) {
        if (pattern instanceof CompositeFloorPattern composite) {
            composite.generators().forEach(child -> collect(child, into));
        } else if (pattern instanceof CheckerboardFloorPattern board) {
            into.add(board.primaryBlock());
            into.add(board.secondaryBlock());
        } else if (pattern instanceof CrossFloorPattern cross) {
            into.add(cross.block());
        } else if (pattern instanceof SpokesFloorPattern spokes) {
            into.add(spokes.block());
        } else {
            throw new AssertionError("unhandled pattern in this test's collector: " + pattern);
        }
    }

    private static String crossBlock(MotifConfig motif) {
        FloorPattern pattern = motif.schemes().stream()
                .filter(scheme -> "paved".equals(scheme.name())).findFirst().orElseThrow()
                .floor().orElseThrow().pattern();
        return ((CrossFloorPattern) pattern).block();
    }

    /** One pattern, decoded and then resolved against {@link #PALETTE}. */
    private static FloorPattern resolved(String patternJson) {
        MotifConfig motif = fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"f\", \"floor\": "
                + patternJson + "}]}");
        return motif.forFloor(0).schemes().stream()
                .filter(scheme -> "f".equals(scheme.name())).findFirst().orElseThrow()
                .floor().orElseThrow().pattern();
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
