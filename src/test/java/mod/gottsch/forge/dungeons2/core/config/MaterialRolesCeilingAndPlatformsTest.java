package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.ceiling.BorderCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CentreCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CoffersCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.JoistsCeilingPattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #65 phase 4 &mdash; the {@code ceiling} and {@code platforms} slots read a material role.
 *
 * <p>This is the phase the dotted role name was designed for. A joist's {@code block} and its
 * {@code bracket_block} are a <strong>coordinated pair</strong> &mdash; the beam moves with the
 * bracket that carries it, and classic ships three such pairs at once (stone, timber, salvage) with
 * two of them live in the same band. {@code $joist.beam} and {@code $joist.bracket} are how a
 * palette says which pair it means, and {@link #aCoordinatedSetIsNamedTogetherByItsDottedRoles} is
 * the case that motivated the naming scheme.</p>
 *
 * <p>{@code platforms} rides along: its blocks sit on the entry rather than on a layout, so all four
 * convert with one method.</p>
 */
class MaterialRolesCeilingAndPlatformsTest {

    private static final Gson GSON = new Gson();

    private static final String PALETTE =
            "\"palette\": {\"a\": \"minecraft:stone\", \"b\": \"minecraft:cobblestone\"}";

    // ---- every ceiling field ---------------------------------------------------------------------

    @Test
    void aBorderResolvesItsBlockAndItsCornerBlock() {
        BorderCeilingPattern border = (BorderCeilingPattern) ceiling(
                "{\"type\": \"dungeons2:border\", \"config\": {"
                        + "\"block\": \"$a\", \"corner_block\": \"$b\"}}");
        assertEquals("minecraft:stone", border.block());
        assertEquals(Optional.of("minecraft:cobblestone"), border.cornerBlock());
    }

    @Test
    void aCentreResolvesItsBlockAndKeepsItsSize() {
        CentreCeilingPattern centre = (CentreCeilingPattern) ceiling(
                "{\"type\": \"dungeons2:centre\", \"config\": {\"block\": \"$a\", \"size\": 3}}");
        assertEquals("minecraft:stone", centre.block());
        assertEquals(3, centre.size());
    }

    @Test
    void aCoffersResolvesItsBlockAndKeepsItsSpacing() {
        CoffersCeilingPattern coffers = (CoffersCeilingPattern) ceiling(
                "{\"type\": \"dungeons2:coffers\", \"config\": {\"block\": \"$a\", \"spacing\": 5}}");
        assertEquals("minecraft:stone", coffers.block());
        assertEquals(5, coffers.spacing());
    }

    @Test
    void aJoistsResolvesItsBeamAndItsBracket() {
        JoistsCeilingPattern joists = (JoistsCeilingPattern) ceiling(
                "{\"type\": \"dungeons2:joists\", \"config\": {"
                        + "\"block\": \"$a\", \"bracket_block\": \"$b\", \"spacing\": 3}}");
        assertEquals("minecraft:stone", joists.block());
        assertEquals(Optional.of("minecraft:cobblestone"), joists.bracketBlock());
        assertEquals(3, joists.spacing());
    }

    /**
     * <strong>The case the dotted names exist for.</strong> A beam and the bracket carrying it move
     * together, so they are named together and a band repaints the pair in one place. Numbering them
     * ({@code $corbel_1}, {@code $corbel_2}) was rejected precisely because it lets half a pair be
     * repainted without the other half: a spruce beam on a stone bracket is a different room, and an
     * invisible mistake.
     */
    @Test
    void aCoordinatedSetIsNamedTogetherByItsDottedRoles() {
        MotifConfig motif = fold("""
                {"palette": {"joist.beam": "minecraft:polished_andesite",
                             "joist.bracket": "dungeonblocks:polished_andesite_corbel_block"},
                 "schemes": [{"name": "hall", "ceiling": {"patterns": [
                     {"type": "dungeons2:joists",
                      "config": {"block": "$joist.beam", "bracket_block": "$joist.bracket"}}]}}],
                 "strata_by_floor_index": [
                   {"min_floor_index": 0,
                    "palette": {"joist.beam": "minecraft:spruce_log",
                                "joist.bracket": "dungeonblocks:spruce_corbel_block"}},
                   {"min_floor_index": 1}]}""");

        JoistsCeilingPattern mud = (JoistsCeilingPattern) firstCeiling(motif.forFloor(0));
        assertEquals("minecraft:spruce_log", mud.block());
        assertEquals(Optional.of("dungeonblocks:spruce_corbel_block"), mud.bracketBlock());

        JoistsCeilingPattern stone = (JoistsCeilingPattern) firstCeiling(motif.forFloor(1));
        assertEquals("minecraft:polished_andesite", stone.block());
        assertEquals(Optional.of("dungeonblocks:polished_andesite_corbel_block"),
                stone.bracketBlock());
    }

    /** Every entry of the ceiling's list is walked, not only the first. */
    @Test
    void everyPatternInTheCeilingListIsResolved() {
        MotifConfig motif = fold("""
                {"palette": {"a": "minecraft:stone", "b": "minecraft:cobblestone"},
                 "schemes": [{"name": "hall", "ceiling": {"patterns": [
                     {"type": "dungeons2:coffers", "config": {"block": "$a"}},
                     {"type": "dungeons2:centre",  "config": {"block": "$b"}}]}}]}""");
        List<String> blocks = new ArrayList<>();
        motif.forFloor(0).schemes().stream()
                .filter(scheme -> "hall".equals(scheme.name())).findFirst().orElseThrow()
                .ceiling().orElseThrow().patterns()
                .forEach(entry -> blocks.add(blockOf(entry.pattern())));
        assertEquals(List.of("minecraft:stone", "minecraft:cobblestone"), blocks);
    }

    // ---- platforms -------------------------------------------------------------------------------

    /** All four of the dais's block fields, including the brazier standing on top of it. */
    @Test
    void aDaisResolvesAllFourOfItsBlocks() {
        PlatformPatternEntry.PlatformEntry dais = fold("""
                {"palette": {"a": "minecraft:stone", "b": "minecraft:cobblestone",
                             "c": "minecraft:andesite", "d": "dungeonblocks:brazier_block"},
                 "schemes": [{"name": "dais", "platforms": {"patterns": [
                     {"type": "dais", "layout": "dungeons2:centre", "size": 3,
                      "block": "$a", "stair_block": "$b",
                      "centre_block": "$c", "top_block": "$d"}]}}]}""")
                .forFloor(0).schemes().stream()
                .filter(scheme -> "dais".equals(scheme.name())).findFirst().orElseThrow()
                .platforms().orElseThrow().patterns().get(0);
        assertEquals("minecraft:stone", dais.block());
        assertEquals(Optional.of("minecraft:cobblestone"), dais.stairBlock());
        assertEquals(Optional.of("minecraft:andesite"), dais.centreBlock());
        assertEquals(Optional.of("dungeonblocks:brazier_block"), dais.topBlock());
        assertEquals(3, dais.size(), "and keeps the fields it did not resolve");
    }

    // ---- the second place a CeilingPatternEntry lives --------------------------------------------

    /**
     * The {@code ceiling} SECTION's own {@code pattern}, checked deliberately this time. Phase 3
     * found the equivalent hole on {@code FloorConfig} by accident, which is what turned it into a
     * rule: converting a record makes a role authorable everywhere that record appears.
     */
    @Test
    void theCeilingSECTIONsOwnPatternIsResolvedToo() {
        MotifConfig motif = fold("""
                {"palette": {"a": "minecraft:stone"},
                 "ceiling": {"ceiling": "minecraft:stone_bricks",
                             "pattern": {"patterns": [
                                 {"type": "dungeons2:coffers", "config": {"block": "$a"}}]}}}""");
        CeilingPattern pattern = motif.forFloor(0).ceiling().pattern().orElseThrow()
                .patterns().get(0).pattern();
        assertEquals("minecraft:stone", blockOf(pattern));
    }

    @Test
    void anUndeclaredRoleInTheCeilingSectionIsReportedAtLoad() {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC, """
                {"ceiling": {"ceiling": "minecraft:stone_bricks",
                             "pattern": {"patterns": [
                                 {"type": "dungeons2:coffers", "config": {"block": "$vault"}}]}}}""")),
                problems::add);
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$vault"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("ceiling section"), () -> problems.get(0));
    }

    // ---- identity --------------------------------------------------------------------------------

    @Test
    void aCeilingOfLiteralsIsNotEvenCopied() {
        CeilingPatternEntry entry = decode(CeilingPatternEntry.CODEC,
                "{\"patterns\": [{\"type\": \"dungeons2:coffers\","
                        + " \"config\": {\"block\": \"minecraft:stone\"}}]}");
        assertSame(entry, entry.withRoles(role -> "minecraft:dirt"));
    }

    @Test
    void aPlatformOfLiteralsIsNotEvenCopied() {
        PlatformPatternEntry entry = decode(PlatformPatternEntry.CODEC,
                "{\"patterns\": [{\"type\": \"dais\", \"layout\": \"dungeons2:centre\","
                        + " \"block\": \"minecraft:stone\"}]}");
        assertSame(entry, entry.withRoles(role -> "minecraft:dirt"));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static String blockOf(CeilingPattern pattern) {
        if (pattern instanceof CoffersCeilingPattern coffers) {
            return coffers.block();
        }
        if (pattern instanceof CentreCeilingPattern centre) {
            return centre.block();
        }
        if (pattern instanceof BorderCeilingPattern border) {
            return border.block();
        }
        if (pattern instanceof JoistsCeilingPattern joists) {
            return joists.block();
        }
        throw new AssertionError("unhandled pattern: " + pattern);
    }

    private static CeilingPattern firstCeiling(MotifConfig motif) {
        return motif.schemes().stream().filter(scheme -> "hall".equals(scheme.name()))
                .findFirst().orElseThrow().ceiling().orElseThrow().patterns().get(0).pattern();
    }

    /** One ceiling pattern, decoded and then resolved against {@link #PALETTE}. */
    private static CeilingPattern ceiling(String patternJson) {
        MotifConfig motif = fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"hall\","
                + " \"ceiling\": {\"patterns\": [" + patternJson + "]}}]}");
        return firstCeiling(motif.forFloor(0));
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
