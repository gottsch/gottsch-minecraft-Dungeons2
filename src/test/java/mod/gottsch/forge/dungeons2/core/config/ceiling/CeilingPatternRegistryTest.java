/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.config.ceiling;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfacePatternEntry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ceiling half of the pattern-type registry rollout.
 *
 * <p>The shared machinery is {@code PatternTypeRegistry} and is covered by
 * {@code FloorPatternRegistryTest}. Specific to ceilings: the five registered ids (four types, one
 * of them under two spellings), the {@code projection}-stays-on-the-entry split, and the
 * {@code orient} rule that <em>survived</em> beside the two that became schema errors.
 */
class CeilingPatternRegistryTest {

    private static final String BLOCK = "minecraft:polished_andesite";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CeilingPatternRegistry.registerBuiltIns();
    }

    private static DataResult<SurfacePatternEntry> parse(String json) {
        return SurfacePatternEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static SurfacePatternEntry decode(String json) {
        DataResult<SurfacePatternEntry> result = parse(json);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected a decode, got: " + result.error().map(Object::toString).orElse("?")));
    }

    private static String errorOf(String json) {
        DataResult<SurfacePatternEntry> result = parse(json);
        assertTrue(result.result().isEmpty(), "expected a load error, but this decoded: " + json);
        return result.error().orElseThrow().message();
    }

    @Test
    void theBuiltInPatternsAreRegisteredUnderThisModsNamespace() {
        for (String path : new String[] {"border", "coffers", "joists", "centre", "center"}) {
            assertTrue(CeilingPatternRegistry.ids().contains(new ResourceLocation("dungeons2", path)),
                    "dungeons2:" + path + " should be registered");
        }
    }

    /**
     * The flat switch accepted {@code centre} and {@code center} as one case. A ResourceLocation
     * cannot carry an alias, so both are registered over the SAME codec rather than one spelling
     * silently breaking.
     */
    @Test
    void bothSpellingsOfCentreResolveToTheSamePattern() {
        assertEquals(
                decode("{\"type\": \"dungeons2:centre\", \"config\": {\"block\": \"" + BLOCK + "\"}}").pattern(),
                decode("{\"type\": \"dungeons2:center\", \"config\": {\"block\": \"" + BLOCK + "\"}}").pattern());
    }

    @Test
    void anUnregisteredPatternIsALoadErrorThatNamesWhatIsRegistered() {
        String message = errorOf("{\"type\": \"yourmod:vault\","
                + " \"config\": {\"block\": \"" + BLOCK + "\"}}");
        assertTrue(message.contains("yourmod:vault"), message);
        assertTrue(message.contains("dungeons2:coffers"),
                "the error must list what IS registered: " + message);
    }

    // ---------- the two rules that became schema errors, and the one that did not ----------

    /** Was "orient is only meaningful on a border or joists". Now a stray key on the others. */
    @Test
    void orientOnATypeWithNoDirectionIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:coffers\", \"config\": {"
                + "\"block\": \"" + BLOCK + "\", \"orient\": \"outward\"}}")
                .toLowerCase().contains("orient"));
    }

    /** Was "bracket_block is a joists field". Now a stray key on the others. */
    @Test
    void aBracketOnATypeWithNoEndsIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:border\", \"config\": {"
                + "\"block\": \"" + BLOCK + "\", \"bracket_block\": \"dungeonblocks:spruce_corbel_block\"}}")
                .contains("bracket_block"));
    }

    /**
     * <strong>And the one that could not become a schema error.</strong> It is a relationship
     * between two fields of the SAME type &mdash; orient turns the end bracket, so an oriented
     * joists with no bracketBlock has nothing to turn &mdash; which no key set can express. It
     * still lives in {@code CeilingPatternEntry.validate}.
     */
    @Test
    void anOrientedJoistsWithNoBracketIsStillACheckedError() {
        DataResult<CeilingPatternEntry> result = CeilingPatternEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"patterns\": [{\"type\": \"dungeons2:joists\", \"config\": {"
                        + "\"block\": \"minecraft:spruce_log\", \"orient\": \"inward\"}}]}"));
        assertTrue(result.result().isEmpty());
        assertTrue(result.error().orElseThrow().message().contains("bracket_block"));
    }

    // ---------- the entry/pattern split ----------

    /**
     * {@code projection} stays on the ENTRY, unlike every other field. It positions the pattern in
     * the ceiling's layer stack rather than describing the pattern's own shape.
     */
    @Test
    void projectionStaysOnTheEntryAndEverythingElseMovesIntoConfig() {
        SurfacePatternEntry entry = decode("{\"type\": \"dungeons2:coffers\", \"projection\": 1,"
                + " \"config\": {\"block\": \"" + BLOCK + "\", \"spacing\": 4}}");
        assertEquals(1, entry.projection());
        assertEquals(4, assertInstanceOf(CoffersCeilingPattern.class, entry.pattern()).spacing());
    }

    /**
     * Backlog #28b: the ceiling's projection bound is its OWN, no longer
     * {@code WallPatternEntry.MAX_PROJECTION}. A wall course caps at 2 because past one cell it
     * stops reading as trim and starts reading as a ledge at head height; a ceiling ring hangs at
     * the room's edge where nobody walks, and a deep one reads as a dome, which is the feature.
     *
     * <p>Asserted as the two bounds DIFFERING rather than as "the ceiling accepts 4", because the
     * failure this guards against is someone re-sharing the constant to tidy up the duplication --
     * which would compile, pass a bare "accepts 4" test if the wall bound were the one raised, and
     * quietly put ledges back on the walls.</p>
     */
    @Test
    void theCeilingsProjectionBoundIsIndependentOfTheWalls() {
        assertTrue(CeilingPatternEntry.MAX_PROJECTION > WallPatternEntry.MAX_PROJECTION,
                "the ceiling bound must be its own; sharing the wall's is what #28b undid");

        assertEquals(4, decode("{\"type\": \"dungeons2:border\", \"projection\": 4,"
                + " \"config\": {\"block\": \"" + BLOCK + "\"}}").projection(),
                "a four-step vault must decode");

        assertTrue(errorOf("{\"type\": \"dungeons2:border\", \"projection\": 5,"
                        + " \"config\": {\"block\": \"" + BLOCK + "\"}}")
                        .toLowerCase().contains("projection"),
                "past the bound is still a load error, and it must name the field");
    }

    @Test
    void projectionInsideConfigIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:coffers\", \"config\": {"
                + "\"block\": \"" + BLOCK + "\", \"projection\": 1}}")
                .toLowerCase().contains("projection"));
    }

    @Test
    void aPatternMissingItsRequiredBlockIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:border\"}").contains("block"));
    }

    @Test
    void anEntryRoundTrips() {
        SurfacePatternEntry original = new SurfacePatternEntry(
                new JoistsCeilingPattern("minecraft:spruce_log"), 1,
                mod.gottsch.forge.dungeons2.core.config.SizeGate.UNBOUNDED);
        DataResult<com.google.gson.JsonElement> encoded =
                SurfacePatternEntry.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(),
                "encode failed: " + encoded.error().map(Object::toString).orElse("?"));
        assertEquals(original, SurfacePatternEntry.CODEC
                .parse(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow());
    }
}
