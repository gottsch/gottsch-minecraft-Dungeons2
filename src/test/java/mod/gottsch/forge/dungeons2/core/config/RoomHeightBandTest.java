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
package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #51's table: {@link RoomHeightBand}'s codec, its order/totality validation, and the
 * matching rule.
 *
 * <p>The validation rules exist because both of the ways a table can be wrong are <em>silent</em>:
 * an open-ended band in the middle makes every band after it unreachable while still loading
 * cleanly, and a table with no open-ended band at all leaves the largest rooms matching nothing.
 * Neither produces a broken dungeon &mdash; they produce a differently-proportioned one, with
 * nothing anywhere saying so.</p>
 */
class RoomHeightBandTest {

    private static DataResult<List<RoomHeightBand>> parse(String json) {
        return RoomHeightBand.LIST_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static String error(DataResult<?> result) {
        return result.error().orElseThrow(() -> new AssertionError("expected a load error, got "
                + result.result().orElseThrow())).message();
    }

    @Test
    void theShippedTableRoundTrips() {
        DataResult<List<RoomHeightBand>> parsed = parse("""
                [
                  { "maxLongSide": 7, "minHeight": 6, "maxHeight": 10 },
                  { "maxLongSide": 11, "minHeight": 5, "maxHeight": 9 },
                  { "maxLongSide": 15, "minHeight": 5, "maxHeight": 8 },
                  { "minHeight": 5, "maxHeight": 7 }
                ]""");
        assertEquals(DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS,
                parsed.result().orElseThrow(() -> new AssertionError(error(parsed))),
                "the shipped JSON and DEFAULT_ROOM_HEIGHT_BANDS have drifted apart");
    }

    /**
     * The shipped {@code default.json} is the config the Java default claims it is. Reads the real
     * resource rather than an inline copy: a duplicated literal would pass happily while the file
     * on disk said something else, which is the only failure this test exists to catch.
     */
    @Test
    void theShippedFileMatchesTheJavaDefault() throws Exception {
        String json;
        try (InputStream in = RoomHeightBandTest.class.getResourceAsStream(
                "/data/dungeons2/dungeons2/generation_config/default.json")) {
            assertTrue(in != null, "generation_config/default.json is not on the test classpath");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        DataResult<DungeonGenerationConfig> parsed =
                DungeonGenerationConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        assertEquals(DungeonGenerationConfig.DEFAULT,
                parsed.result().orElseThrow(() -> new AssertionError(error(parsed))),
                "generation_config/default.json and DungeonGenerationConfig.DEFAULT have drifted");
    }

    /**
     * Omitting the key keeps the taper rather than removing the cap. "Absent" must not mean "off":
     * an uncapped roll is exactly the tall-box outcome #51 exists to prevent.
     */
    @Test
    void omittingTheKeyKeepsTheShippedTaper() {
        DataResult<DungeonGenerationConfig> parsed = DungeonGenerationConfig.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString("{ \"corridorWidth\": 3 }"));
        assertEquals(DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS,
                parsed.result().orElseThrow(() -> new AssertionError(error(parsed))).roomHeightBands());
    }

    @Test
    void anOpenEndedBandInTheMiddleIsALoadError() {
        DataResult<List<RoomHeightBand>> parsed = parse("""
                [
                  { "minHeight": 6, "maxHeight": 10 },
                  { "maxLongSide": 11, "minHeight": 5, "maxHeight": 9 }
                ]""");
        assertTrue(error(parsed).contains("unreachable"), error(parsed));
    }

    @Test
    void aTableWithNoOpenEndedBandIsALoadError() {
        DataResult<List<RoomHeightBand>> parsed = parse("""
                [
                  { "maxLongSide": 7, "minHeight": 6, "maxHeight": 10 },
                  { "maxLongSide": 11, "minHeight": 5, "maxHeight": 9 }
                ]""");
        assertTrue(error(parsed).contains("must omit"), error(parsed));
    }

    @Test
    void outOfOrderBandsAreALoadError() {
        DataResult<List<RoomHeightBand>> parsed = parse("""
                [
                  { "maxLongSide": 11, "minHeight": 5, "maxHeight": 9 },
                  { "maxLongSide": 7, "minHeight": 6, "maxHeight": 10 },
                  { "minHeight": 5, "maxHeight": 7 }
                ]""");
        assertTrue(error(parsed).contains("strictly increase"), error(parsed));
    }

    @Test
    void anInvertedHeightRangeIsALoadError() {
        DataResult<List<RoomHeightBand>> parsed = parse("""
                [ { "minHeight": 9, "maxHeight": 5 } ]""");
        assertTrue(error(parsed).contains("greater than maxHeight"), error(parsed));
    }

    @Test
    void anEmptyTableIsALoadError() {
        assertTrue(error(parse("[]")).contains("at least one band"));
    }

    /** Closed schema: a misspelled key is a load error, not a silently different taper. */
    @Test
    void anUndeclaredKeyIsALoadError() {
        DataResult<List<RoomHeightBand>> parsed = parse("""
                [ { "maxLongSide": 7, "minHeight": 6, "maxHeight": 10, "maxShortSide": 7 },
                  { "minHeight": 5, "maxHeight": 7 } ]""");
        assertTrue(error(parsed).contains("maxShortSide"), error(parsed));
    }

    /** And a malformed value is one too -- the strictOptionalFieldOf hole, closed here as well. */
    @Test
    void aMalformedOptionalValueIsALoadError() {
        DataResult<List<RoomHeightBand>> parsed = parse("""
                [ { "maxLongSide": -3, "minHeight": 6, "maxHeight": 10 },
                  { "minHeight": 5, "maxHeight": 7 } ]""");
        assertTrue(parsed.error().isPresent(), "maxLongSide -3 decoded without complaint");
    }

    @Test
    void matchingIsFirstWinsOnTheLongSide() {
        List<RoomHeightBand> table = DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS;
        assertEquals(10, RoomHeightBand.forLongSide(table, 7).maxHeight());
        assertEquals(9, RoomHeightBand.forLongSide(table, 9).maxHeight());
        assertEquals(9, RoomHeightBand.forLongSide(table, 11).maxHeight());
        assertEquals(8, RoomHeightBand.forLongSide(table, 13).maxHeight());
        assertEquals(8, RoomHeightBand.forLongSide(table, 15).maxHeight());
        assertEquals(7, RoomHeightBand.forLongSide(table, 17).maxHeight());
        assertEquals(7, RoomHeightBand.forLongSide(table, 19).maxHeight());
        // Past anything the planner can produce, so the open-ended band has to hold.
        assertEquals(7, RoomHeightBand.forLongSide(table, 999).maxHeight());
    }

    @Test
    void clampRaisesAndLowersIntoTheBand() {
        RoomHeightBand band = new RoomHeightBand(Optional.of(7), 6, 10);
        assertEquals(6, band.clamp(5), "a roll below the band should be raised to its floor");
        assertEquals(8, band.clamp(8));
        assertEquals(10, band.clamp(10));
        RoomHeightBand low = new RoomHeightBand(Optional.empty(), 5, 7);
        assertEquals(7, low.clamp(10), "a roll above the band should be lowered to its ceiling");
    }

    @Test
    void theBudgetCheckRejectsATableTallerThanAFloor() {
        assertTrue(RoomHeightBand.validateAgainstBudget(
                DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS, 10));
        assertTrue(!RoomHeightBand.validateAgainstBudget(
                        List.of(new RoomHeightBand(Optional.empty(), 5, 14)), 10),
                "a band taller than the floor budget would push a ceiling into the floor above");
    }
}
