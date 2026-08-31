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
package mod.gottsch.forge.dungeons2.core.config.wall;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.PatternEntry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wall half of the pattern-type registry rollout &mdash; <strong>the slot the flat record cost
 * the most</strong>, at fourteen fields for four types.
 *
 * <p>The shared machinery is {@code PatternTypeRegistry} and is covered by
 * {@code FloorPatternRegistryTest}. What is asserted here is what is specific to walls: the four
 * built-ins, the nested {@code courses} list, and above all <strong>the three validation rules that
 * became schema errors</strong>, each of which existed only because every type shared one record.
 */
class WallPatternRegistryTest {

    private static final String BLOCK = "minecraft:stone_bricks";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        WallPatternRegistry.registerBuiltIns();
    }

    private static DataResult<PatternEntry> parse(String json) {
        return PatternEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static PatternEntry decode(String json) {
        DataResult<PatternEntry> result = parse(json);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected a decode, got: " + result.error().map(Object::toString).orElse("?")));
    }

    private static String errorOf(String json) {
        DataResult<PatternEntry> result = parse(json);
        assertTrue(result.result().isEmpty(), "expected a load error, but this decoded: " + json);
        return result.error().orElseThrow().message();
    }

    @Test
    void theBuiltInPatternsAreRegisteredUnderThisModsNamespace() {
        for (String path : new String[] {"courses", "pilasters", "end_pilasters", "panels",
                "gradient", "diamond"}) {
            assertTrue(WallPatternRegistry.ids().contains(new ResourceLocation("dungeons2", path)),
                    "dungeons2:" + path + " should be registered");
        }
    }

    /**
     * {@code diamond} is the first purely GEOMETRIC wall pattern, so its config shares nothing with
     * the architectural four. These pin that its own keys decode and that the closed schema still
     * rejects a key belonging to one of the others.
     */
    @Test
    void theDiamondPatternDecodesItsOwnKeys() {
        PatternEntry entry = decode("{\"type\": \"dungeons2:diamond\", \"config\": {"
                + " \"block\": \"" + BLOCK + "\", \"size\": 3, \"spacing\": 9,"
                + " \"filled\": true}}");
        DiamondWallPattern diamond = (DiamondWallPattern) entry.pattern();
        assertEquals(3, diamond.size());
        assertEquals(9, diamond.spacing());
        assertTrue(diamond.filled());
    }

    /** Absent size/spacing/filled give a 5x5 outline every six cells. */
    @Test
    void theDiamondPatternDefaultsToAnOutline() {
        DiamondWallPattern diamond = (DiamondWallPattern) decode(
                "{\"type\": \"dungeons2:diamond\", \"config\": {\"block\": \"" + BLOCK + "\"}}")
                .pattern();
        assertEquals(2, diamond.size());
        assertEquals(6, diamond.spacing());
        assertFalse(diamond.filled());
    }

    /** It needs a block like the other single-material patterns, and says so. */
    @Test
    void aDiamondMissingItsBlockIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:diamond\"}").contains("block"));
    }

    /**
     * A size of 0 is one cell, which is a speck rather than a diamond; a spacing of 0 would stack
     * every diamond on the same centre. Both are the schema's job, not the provider's.
     */
    @Test
    void aDegenerateDiamondIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:diamond\", \"config\": {"
                + " \"block\": \"" + BLOCK + "\", \"size\": 0}}").contains("size"));
        assertTrue(errorOf("{\"type\": \"dungeons2:diamond\", \"config\": {"
                + " \"block\": \"" + BLOCK + "\", \"spacing\": 0}}").contains("spacing"));
    }

    /** An architectural key on the geometric pattern is a stray key, not a silent no-op. */
    @Test
    void anArchitecturalKeyOnTheDiamondIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:diamond\", \"config\": {"
                + " \"block\": \"" + BLOCK + "\", \"projection\": 1}}").length() > 0);
    }

    @Test
    void anUnregisteredPatternIsALoadErrorThatNamesWhatIsRegistered() {
        String message = errorOf("{\"type\": \"yourmod:rustication\","
                + " \"config\": {\"block\": \"" + BLOCK + "\"}}");
        assertTrue(message.contains("yourmod:rustication"), message);
        assertTrue(message.contains("dungeons2:panels"),
                "the error must list what IS registered: " + message);
    }

    @Test
    void anUnmigratedBareTypeIsALoadError() {
        assertTrue(errorOf("{\"type\": \"panels\", \"config\": {\"block\": \"" + BLOCK + "\"}}")
                .contains("minecraft:panels"));
    }

    // ---------- the three rules that became schema errors ----------

    /**
     * Was {@code WallPatternEntry.validate}'s "'courses' is only meaningful on a 'courses'
     * pattern". Now a stray key, because {@code panels} does not declare {@code courses}.
     */
    @Test
    void coursesOnANonCoursesPatternIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:panels\", \"config\": {"
                + "\"block\": \"" + BLOCK + "\", \"courses\": []}}")
                .toLowerCase().contains("courses"));
    }

    /**
     * Was "'block' belongs on each entry of 'courses', not on the pattern itself". Now a stray key,
     * because {@code courses} has no {@code block} of its own to write.
     */
    @Test
    void blockOnACoursesPatternIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:courses\", \"config\": {"
                + "\"block\": \"" + BLOCK + "\", \"courses\": ["
                + "{\"block\": \"" + BLOCK + "\"}]}}")
                .toLowerCase().contains("block"));
    }

    /**
     * Was "'block' is required -- there is no default material for it". Now a required
     * {@code fieldOf} on the types that take one, which the flat record could not express: it had
     * to be Optional, because {@code courses} always left it out.
     */
    @Test
    void aPatternMissingItsRequiredBlockIsALoadError() {
        assertTrue(errorOf("{\"type\": \"dungeons2:pilasters\"}").contains("block"));
        assertTrue(errorOf("{\"type\": \"dungeons2:panels\"}").contains("block"));
    }

    // ---------- shape ----------

    @Test
    void aCoursesPatternNestsWholeCourseRecords() {
        PatternEntry entry = decode("{\"type\": \"dungeons2:courses\", \"config\": {\"courses\": ["
                + "{\"block\": \"minecraft:andesite\", \"anchor\": \"top\", \"offset\": 2}]}}");
        assertTrue(entry.isCourses());
        assertEquals(1, entry.coursesOrEmpty().size());
        assertEquals(2, entry.coursesOrEmpty().get(0).offset());
    }

    /**
     * The two pilaster layouts are distinct ids over the SAME field set &mdash; they share
     * {@link PilasterShape}, so their configs are identical and only the id tells them apart.
     */
    @Test
    void theTwoPilasterLayoutsAreDistinctIdsOverOneShape() {
        String config = " \"config\": {\"block\": \"" + BLOCK + "\", \"spacing\": 4}}";
        PatternEntry even = decode("{\"type\": \"dungeons2:pilasters\"," + config);
        PatternEntry ends = decode("{\"type\": \"dungeons2:end_pilasters\"," + config);

        assertTrue(even.isPilasters());
        assertTrue(ends.isEndPilasters());
        assertEquals(even.pilasterShape().orElseThrow(), ends.pilasterShape().orElseThrow(),
                "same authored shape, different layout");
        assertInstanceOf(PilastersWallPattern.class, even.pattern());
        assertInstanceOf(EndPilastersWallPattern.class, ends.pattern());
    }

    /** The gate stays flat beside {@code type}, as it does in every other slot. */
    @Test
    void theSizeGateIsStillAuthoredFlat() {
        assertEquals(7, decode("{\"type\": \"dungeons2:panels\", \"minSize\": 7,"
                + " \"config\": {\"block\": \"" + BLOCK + "\"}}").gate().minSize());
    }

    @Test
    void anEntryRoundTrips() {
        PatternEntry original = new PatternEntry(new PanelsWallPattern(
                BLOCK, 3, 3, 0, 0,
                mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient.NONE,
                java.util.Map.of()));
        DataResult<com.google.gson.JsonElement> encoded =
                PatternEntry.CODEC.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(),
                "encode failed: " + encoded.error().map(Object::toString).orElse("?"));
        assertEquals(original, PatternEntry.CODEC
                .parse(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow());
    }
}
