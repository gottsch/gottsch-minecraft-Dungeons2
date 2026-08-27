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

    /** Was "bracketBlock is a joists field". Now a stray key on the others. */
    @Test
    void aBracketOnATypeWithNoEndsIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:border\", \"config\": {"
                + "\"block\": \"" + BLOCK + "\", \"bracketBlock\": \"dungeonblocks:spruce_corbel_block\"}}")
                .toLowerCase().contains("bracketblock"));
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
        assertTrue(result.error().orElseThrow().message().contains("bracketBlock"));
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
