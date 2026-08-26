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
package mod.gottsch.forge.dungeons2.core.config.platform;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry.PlatformEntry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The platform half of the pattern-type registry rollout, and <strong>the one slot whose dispatch
 * axis is not called {@code type}</strong>.
 *
 * <p>The shared machinery is {@code PatternTypeRegistry} and is covered by
 * {@code FloorPatternRegistryTest}. What is asserted here is what is specific to platforms: the
 * {@code layout} key, the {@code type}/{@code layout} split, and the {@code size}/{@code inset}
 * split between the entry and the layout.</p>
 */
class PlatformLayoutRegistryTest {

    private static final String BLOCK = "minecraft:stone_bricks";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PlatformLayoutRegistry.registerBuiltIns();
    }

    private static DataResult<PlatformEntry> parse(String json) {
        return PlatformEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static PlatformEntry decode(String json) {
        DataResult<PlatformEntry> result = parse(json);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected a decode, got: " + result.error().map(Object::toString).orElse("?")));
    }

    private static String errorOf(String json) {
        DataResult<PlatformEntry> result = parse(json);
        assertTrue(result.result().isEmpty(), "expected a load error, but this decoded: " + json);
        return result.error().orElseThrow().message();
    }

    @Test
    void theBuiltInLayoutsAreRegisteredUnderThisModsNamespace() {
        for (String path : new String[] {"centre", "corners", "grid", "quartet", "colonnade"}) {
            assertTrue(PlatformLayoutRegistry.ids().contains(new ResourceLocation("dungeons2", path)),
                    "dungeons2:" + path + " should be registered");
        }
    }

    /** The whole point of this slot being different: the dispatch key is {@code layout}. */
    @Test
    void theDispatchKeyIsLayoutNotType() {
        assertEquals(PlatformLayoutRegistry.LAYOUT_KEY, "layout");
        assertInstanceOf(CornersPlatformLayout.class,
                decode("{\"type\": \"dais\", \"layout\": \"dungeons2:corners\","
                        + " \"block\": \"" + BLOCK + "\"}").layout());
    }

    @Test
    void anUnregisteredLayoutIsALoadErrorThatNamesWhatIsRegistered() {
        String message = errorOf("{\"type\": \"dais\", \"layout\": \"yourmod:spiral\","
                + " \"block\": \"" + BLOCK + "\"}");
        assertTrue(message.contains("yourmod:spiral"), message);
        assertTrue(message.contains("dungeons2:corners"),
                "the error must list what IS registered: " + message);
    }

    @Test
    void anUnmigratedBareLayoutIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dais\", \"layout\": \"centre\", \"block\": \"" + BLOCK + "\"}")
                .contains("minecraft:centre"));
    }

    /**
     * {@code type} keeps its own meaning &mdash; what the platform IS, as against where the copies
     * go. An unknown one used to make the selector drop the platform silently; it is a load error
     * now, so this is the last of that family closed.
     */
    @Test
    void anUnknownPlatformTypeIsALoadError() {
        DataResult<PlatformPatternEntry> result = PlatformPatternEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"patterns\": [{\"type\": \"gazebo\","
                        + " \"layout\": \"dungeons2:centre\", \"block\": \"" + BLOCK + "\"}]}"));
        assertTrue(result.result().isEmpty());
        assertTrue(result.error().orElseThrow().message().contains("gazebo"));
    }

    // ---------- the entry/layout split ----------

    /**
     * {@code size} is the dais's own side and stays on the entry; {@code inset} is pure placement
     * and moves into the layout. The asymmetry is real: {@code centre} and {@code corners} ignore
     * {@code size} entirely, which is exactly what would be hidden if it lived in the layout.
     */
    @Test
    void sizeStaysOnTheEntryAndInsetMovesIntoConfig() {
        PlatformEntry entry = decode("{\"type\": \"dais\", \"layout\": \"dungeons2:grid\","
                + " \"block\": \"" + BLOCK + "\", \"size\": 3, \"config\": {\"inset\": 2}}");
        assertEquals(3, entry.size());
        assertEquals(2, assertInstanceOf(GridPlatformLayout.class, entry.layout()).inset());
    }

    @Test
    void insetAtTheEntryLevelIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dais\", \"layout\": \"dungeons2:centre\","
                + " \"block\": \"" + BLOCK + "\", \"inset\": 1}").toLowerCase().contains("inset"));
    }

    @Test
    void sizeInsideConfigIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dais\", \"layout\": \"dungeons2:centre\","
                + " \"block\": \"" + BLOCK + "\", \"config\": {\"size\": 3}}")
                .toLowerCase().contains("size"));
    }

    @Test
    void configMayBeOmittedEntirely() {
        assertEquals(PlatformLayoutPattern.DEFAULT_INSET,
                assertInstanceOf(CentrePlatformLayout.class,
                        decode("{\"type\": \"dais\", \"layout\": \"dungeons2:centre\","
                                + " \"block\": \"" + BLOCK + "\"}").layout()).inset());
    }

    @Test
    void anEntryRoundTrips() {
        PlatformEntry original = new PlatformEntry(BLOCK);
        DataResult<com.google.gson.JsonElement> encoded =
                PlatformEntry.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(),
                "encode failed: " + encoded.error().map(Object::toString).orElse("?"));
        assertEquals(original, PlatformEntry.CODEC
                .parse(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow());
    }
}
