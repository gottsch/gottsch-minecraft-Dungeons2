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
package mod.gottsch.forge.dungeons2.core.config.pillar;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry.PillarEntry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pillar half of the pattern-type registry rollout.
 *
 * <p>The shared machinery &mdash; dispatch, the closed schema on both levels, the load error for an
 * unknown id &mdash; is {@code PatternTypeRegistry} and is covered by
 * {@code FloorPatternRegistryTest}. What is asserted here is what is specific to pillars: the three
 * built-ins, and <strong>the split between the entry's material fields and the layout's
 * geometry</strong>, which is the one place pillars deliberately differ from floors.</p>
 */
class PillarLayoutRegistryTest {

    private static final String BLOCK = "dungeonblocks:stone_bricks_pillar_block";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PillarLayoutRegistry.registerBuiltIns();
    }

    private static DataResult<PillarEntry> parse(String json) {
        return PillarEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static PillarEntry decode(String json) {
        DataResult<PillarEntry> result = parse(json);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected a decode, got: " + result.error().map(Object::toString).orElse("?")));
    }

    private static String errorOf(String json) {
        DataResult<PillarEntry> result = parse(json);
        assertTrue(result.result().isEmpty(), "expected a load error, but this decoded: " + json);
        return result.error().orElseThrow().message();
    }

    @Test
    void theBuiltInLayoutsAreRegisteredUnderThisModsNamespace() {
        for (String path : new String[] {"grid", "colonnade", "quartet", "centre", "center"}) {
            assertTrue(PillarLayoutRegistry.ids().contains(new ResourceLocation("dungeons2", path)),
                    "dungeons2:" + path + " should be registered");
        }
    }

    /**
     * {@code centre} takes {@code inset} and NOT {@code spacing}. A lone column has nothing to be
     * spaced from, so the field would read as meaningful and do nothing -- and the closed schema is
     * what turns that into a load error naming the key instead of a silent no-op.
     */
    @Test
    void theCentreLayoutTakesInsetButNotSpacing() {
        PillarEntry entry = decode("{\"type\": \"dungeons2:centre\", \"block\": \"" + BLOCK + "\","
                + " \"config\": {\"inset\": 3}}");
        assertEquals(3, ((CentrePillarLayout) entry.layout()).inset());

        assertTrue(errorOf("{\"type\": \"dungeons2:centre\", \"block\": \"" + BLOCK + "\","
                        + " \"config\": {\"spacing\": 4}}").contains("spacing"),
                "spacing means nothing to a single pier and must not be silently accepted");
    }

    /** Both spellings decode, over one codec, to the same layout. */
    @Test
    void theAmericanSpellingIsTheSameLayout() {
        assertEquals(decode("{\"type\": \"dungeons2:centre\", \"block\": \"" + BLOCK + "\"}").layout(),
                decode("{\"type\": \"dungeons2:center\", \"block\": \"" + BLOCK + "\"}").layout());
    }

    /**
     * {@code thickness} is an ENTRY field, not a layout one, so it decodes the same beside any
     * layout. That is the whole argument for where it lives: one declaration, four layouts.
     */
    @Test
    void thicknessIsAnEntryFieldAvailableToEveryLayout() {
        for (String type : new String[] {"grid", "colonnade", "quartet", "centre"}) {
            assertEquals(2, decode("{\"type\": \"dungeons2:" + type + "\","
                    + " \"block\": \"" + BLOCK + "\", \"thickness\": 2}").thickness(),
                    type + " should accept thickness");
        }
    }

    /** Absent means 1 -- the single-cell column every layout drew before the field existed. */
    @Test
    void thicknessDefaultsToOne() {
        assertEquals(1, decode("{\"type\": \"dungeons2:centre\", \"block\": \"" + BLOCK + "\"}")
                .thickness());
    }

    /** A shaft zero cells across is not a column. */
    @Test
    void aThicknessBelowOneIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:centre\", \"block\": \"" + BLOCK + "\","
                + " \"thickness\": 0}").contains("thickness"));
    }

    /**
     * It belongs on the entry beside the material fields, NOT inside a layout's {@code config}.
     * Authored there it would be a per-layout geometry field, which is the duplication this design
     * avoids -- and the closed schema says so rather than ignoring it.
     */
    @Test
    void thicknessInsideConfigIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:centre\", \"block\": \"" + BLOCK + "\","
                + " \"config\": {\"thickness\": 2}}").contains("thickness"));
    }

    @Test
    void anUnregisteredLayoutIsALoadErrorThatNamesWhatIsRegistered() {
        String message = errorOf("{\"type\": \"yourmod:spiral\", \"block\": \"" + BLOCK + "\"}");
        assertTrue(message.contains("yourmod:spiral"), message);
        assertTrue(message.contains("dungeons2:colonnade"),
                "the error must list what IS registered: " + message);
    }

    /** The old values were bare words, so an unmigrated pack fails loudly rather than drawing nothing. */
    @Test
    void anUnmigratedBareTypeIsALoadError() {
        assertTrue(errorOf("{\"type\": \"grid\", \"block\": \"" + BLOCK + "\"}")
                .contains("minecraft:grid"));
    }

    // ---------- the entry/layout split ----------

    /**
     * Materials stay FLAT on the entry; only the geometry nests. A pillar layout is a bare footprint
     * whose blocks travel alongside it to draw time, so unlike a floor pattern it cannot absorb them.
     */
    @Test
    void materialsStayOnTheEntryAndGeometryMovesIntoConfig() {
        PillarEntry entry = decode("{\"type\": \"dungeons2:grid\", \"block\": \"" + BLOCK + "\","
                + " \"baseProperties\": {\"base\": \"up\"},"
                + " \"config\": {\"spacing\": 4, \"inset\": 2}}");

        assertEquals(BLOCK, entry.block());
        assertEquals("up", entry.basePropertiesOrBase().get("base"));
        GridPillarLayout grid = assertInstanceOf(GridPillarLayout.class, entry.layout());
        assertEquals(4, grid.spacing());
        assertEquals(2, grid.inset());
    }

    /** Geometry written at the entry level is now a stray key, not a silently-honoured one. */
    @Test
    void spacingAtTheEntryLevelIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:grid\", \"block\": \"" + BLOCK + "\","
                + " \"spacing\": 4}").toLowerCase().contains("spacing"));
    }

    /** A material written inside config is the same mistake in the other direction. */
    @Test
    void aBlockInsideConfigIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:grid\","
                + " \"config\": {\"block\": \"" + BLOCK + "\"}}").toLowerCase().contains("block"));
    }

    /** Every built-in takes the same two knobs, and both default. */
    @Test
    void configMayBeOmittedEntirely() {
        assertInstanceOf(ColonnadePillarLayout.class,
                decode("{\"type\": \"dungeons2:colonnade\", \"block\": \"" + BLOCK + "\"}").layout());
    }

    @Test
    void anEntryRoundTrips() {
        PillarEntry original = new PillarEntry(new QuartetPillarLayout(6, 1), BLOCK);
        DataResult<com.google.gson.JsonElement> encoded =
                PillarEntry.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(),
                "encode failed: " + encoded.error().map(Object::toString).orElse("?"));
        assertEquals(original, PillarEntry.CODEC
                .parse(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow());
    }
}
