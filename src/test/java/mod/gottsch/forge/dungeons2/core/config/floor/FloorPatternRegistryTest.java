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
package mod.gottsch.forge.dungeons2.core.config.floor;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor-pattern registry pilot: {@code type} is a {@link ResourceLocation} naming a registered
 * pattern, and <strong>an unknown one is a load error</strong> (Gottsch, 2026-08-26).
 *
 * <p>That policy is the entire point of the change, so it is asserted from the outside &mdash;
 * through the codec, on JSON text &mdash; rather than by calling the lookup directly.</p>
 */
class FloorPatternRegistryTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FloorPatternRegistry.registerBuiltIns();
    }

    private static DataResult<FloorPatternEntry> parse(String json) {
        return FloorPatternEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static FloorPatternEntry decode(String json) {
        DataResult<FloorPatternEntry> result = parse(json);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected a decode, got: " + result.error().map(Object::toString).orElse("?")));
    }

    private static String errorOf(String json) {
        DataResult<FloorPatternEntry> result = parse(json);
        assertTrue(result.result().isEmpty(), "expected a load error, but this decoded: " + json);
        return result.error().orElseThrow().message();
    }

    // ---------- the shipped set ----------

    @Test
    void theBuiltInPatternsAreRegisteredUnderThisModsNamespace() {
        for (String path : new String[] {"plain", "border", "checkerboard", "speckle", "cross",
                "spokes", "composite"}) {
            assertTrue(FloorPatternRegistry.ids().contains(new ResourceLocation("dungeons2", path)),
                    "dungeons2:" + path + " should be registered");
        }
    }

    // ---------- an unknown type is a LOAD ERROR ----------

    @Test
    void anUnregisteredTypeIsALoadErrorThatNamesWhatIsRegistered() {
        String message = errorOf("{\"type\": \"yourmod:mosaic\"}");
        assertTrue(message.contains("yourmod:mosaic"), "the error must name the id: " + message);
        assertTrue(message.contains("dungeons2:speckle"),
                "the error must list what IS registered, because the usual cause is a typo or a"
                        + " missing mod: " + message);
    }

    /**
     * The old {@code type} values were bare words. They are now unqualified resource locations,
     * which resolve to {@code minecraft:} and so are not registered &mdash; a pack that missed the
     * migration fails loudly instead of silently drawing plain floors.
     */
    @Test
    void anUnmigratedBareTypeIsALoadError() {
        assertTrue(errorOf("{\"type\": \"speckle\"}").contains("minecraft:speckle"));
    }

    // ---------- the closed schema, on both levels ----------

    @Test
    void aStrayKeyBesideTypeIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:plain\", \"probability\": 0.5}")
                .toLowerCase().contains("probability"),
                "a pattern's own field written at the entry level must not be accepted -- that is"
                        + " exactly the flat-record mistake this replaced");
    }

    @Test
    void aStrayKeyInsideConfigIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:speckle\", \"config\": {"
                + "\"primary_block\": \"minecraft:cobblestone\","
                + "\"secondary_block\": \"minecraft:packed_mud\","
                + "\"spokes\": 4}}").toLowerCase().contains("spokes"),
                "a field belonging to a DIFFERENT pattern must be rejected, which the flat record"
                        + " could never do");
    }

    /** A required block is now genuinely required -- the flat record had to make every slot optional. */
    @Test
    void aMissingRequiredBlockIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:speckle\", \"config\": {"
                + "\"primary_block\": \"minecraft:cobblestone\"}}").contains("secondary_block"));
    }

    // ---------- decoding, and the shape the shipped strata.json uses ----------

    @Test
    void theMudBandsAuthoredPatternDecodes() {
        FloorPatternEntry entry = decode("{\"type\": \"dungeons2:speckle\", \"config\": {"
                + "\"primary_block\": \"minecraft:cobblestone\","
                + "\"secondary_block\": \"minecraft:packed_mud\","
                + "\"probability\": 0.12}}");
        SpeckleFloorPattern speckle = assertInstanceOf(SpeckleFloorPattern.class, entry.pattern());
        assertEquals("minecraft:cobblestone", speckle.primaryBlock());
        assertEquals("minecraft:packed_mud", speckle.secondaryBlock());
        assertEquals(0.12, speckle.probability());
    }

    /** A type with no fields may omit {@code config} entirely. */
    @Test
    void aFieldlessPatternNeedsNoConfigObject() {
        assertInstanceOf(PlainFloorPattern.class, decode("{\"type\": \"dungeons2:plain\"}").pattern());
    }

    @Test
    void aCompositeNestsWholePatternsAndClosesEachOfThem() {
        FloorPatternEntry entry = decode("{\"type\": \"dungeons2:composite\", \"config\": {"
                + "\"generators\": ["
                + "{\"type\": \"dungeons2:checkerboard\", \"config\": {"
                + "\"primary_block\": \"minecraft:stone_bricks\","
                + "\"secondary_block\": \"minecraft:polished_andesite\"}},"
                + "{\"type\": \"dungeons2:cross\", \"config\": {"
                + "\"block\": \"minecraft:polished_andesite\"}}]}}");
        CompositeFloorPattern composite =
                assertInstanceOf(CompositeFloorPattern.class, entry.pattern());
        assertEquals(2, composite.generators().size());
        assertInstanceOf(CrossFloorPattern.class, composite.generators().get(1));

        assertTrue(errorOf("{\"type\": \"dungeons2:composite\", \"config\": {"
                + "\"generators\": [{\"type\": \"dungeons2:cross\", \"config\": {"
                + "\"block\": \"minecraft:polished_andesite\", \"inset\": 2}}]}}")
                .toLowerCase().contains("inset"),
                "a nested pattern must be closed too -- that is where the old codec's strict-optional"
                        + " bug hid");
    }

    /** The gate keys stay flat beside {@code type}, as they are everywhere else. */
    @Test
    void theSizeGateIsStillAuthoredFlat() {
        assertEquals(7, decode("{\"type\": \"dungeons2:plain\", \"min_size\": 7}").gate().minSize());
    }

    // ---------- round trip ----------

    @Test
    void anEntryRoundTrips() {
        FloorPatternEntry original = new FloorPatternEntry(
                new SpeckleFloorPattern("minecraft:cobblestone", "minecraft:packed_mud", 0.12));
        DataResult<com.google.gson.JsonElement> encoded =
                FloorPatternEntry.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(),
                "encode failed: " + encoded.error().map(Object::toString).orElse("?"));
        assertEquals(original, FloorPatternEntry.CODEC
                .parse(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow());
    }
}
