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
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor-to-floor pitch as a datapack knob, and the {@code _comment} that warns about it.
 *
 * <h2>Why this knob is different from the others in the file</h2>
 * <p>{@code corridorWidth} and {@code roomTemplateAttemptsPerFloor} are tuning: turn them and the
 * generator does something else, correctly. The pitch is not &mdash; it is the exact distance every
 * shipped entrance and transition {@code .nbt} was cut for, so changing it makes those templates
 * wrong, and no amount of code can fix that from inside the file. Hence three separate warnings for
 * one number: the {@code _comment} in the shipped file, {@code [D2-PITCH]} at load, and
 * {@code [D2-SPAN]} per assembled transition that cannot reach.</p>
 */
class DungeonGenerationPitchTest {

    private static DataResult<DungeonGenerationConfig> parse(String json) {
        return DungeonGenerationConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static DungeonGenerationConfig ok(String json) {
        DataResult<DungeonGenerationConfig> parsed = parse(json);
        return parsed.result().orElseThrow(() -> new AssertionError(
                parsed.error().orElseThrow().message()));
    }

    // ---------- the knob ----------

    @Test
    void theShippedPitchIsTwelve() {
        assertEquals(12, DungeonGenerationConfig.DEFAULT.pitch());
        assertTrue(DungeonGenerationConfig.DEFAULT.pitchIsShipped());
    }

    /**
     * The planner must not carry its own copy of the default. It used to, and a second literal for
     * the same number is precisely what let {@code MIN_TRANSITION_HEIGHT} drift out of step in #52.
     */
    @Test
    void thePlannerDefaultIsTheConfigDefault() {
        DungeonStackPlanner planner = new DungeonStackPlanner(0L, new Coords(0, 0, 0), 72,
                "classic", new TemplateCatalog());
        assertEquals(DungeonGenerationConfig.DEFAULT.pitch(), planner.pitch(),
                "the planner's default pitch and the datapack default have drifted apart");
    }

    @Test
    void thePitchIsInjectableAndReachesTheplanner() {
        DungeonStackPlanner planner = new DungeonStackPlanner(0L, new Coords(0, 0, 0), 72,
                "classic", new TemplateCatalog())
                .withFloorHeight(16)
                .withGapBetweenFloors(2);
        assertEquals(18, planner.pitch());
        assertEquals(16, planner.floorHeight());
    }

    /**
     * The knob has to move the actual dungeon, not just the planner's field. Asserted on the
     * planned floor Ys, which is the only place the pitch is observable.
     */
    @Test
    void theInjectedPitchIsTheDistanceBetweenPlannedFloors() {
        for (int floorHeight : new int[] {10, 13, 16}) {
            int expected = floorHeight + 2;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    0xD2_29_0001L, new Coords(0, 0, 0), 200, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.LARGE)
                    .withFloorCount(4)
                    .withFloorHeight(floorHeight)
                    .withGapBetweenFloors(2)
                    .plan();
            List<FloorLayout> floors = planned.orElseThrow(
                    () -> new AssertionError("no plan at floorHeight " + floorHeight)).getFloors();
            assertTrue(floors.size() >= 2, "need at least two floors to measure a pitch");
            for (int i = 1; i < floors.size(); i++) {
                assertEquals(expected,
                        floors.get(i - 1).getFloorY() - floors.get(i).getFloorY(),
                        "floors " + (i - 1) + "->" + i + " are not " + expected + " apart at "
                                + "floorHeight " + floorHeight);
            }
        }
    }

    @Test
    void aNonDefaultPitchReportsItself() {
        assertFalse(ok("{ \"floorHeight\": 16 }").pitchIsShipped(),
                "a changed pitch must be detectable, or nothing can warn about it");
    }

    @Test
    void anOutOfRangePitchIsALoadError() {
        assertTrue(parse("{ \"floorHeight\": 2 }").error().isPresent(),
                "floorHeight 2 leaves no room for a floor block, a body and a ceiling");
        assertTrue(parse("{ \"floorHeight\": 99 }").error().isPresent());
        assertTrue(parse("{ \"gapBetweenFloors\": -1 }").error().isPresent());
    }

    // ---------- the note in the file ----------

    /** The shipped file's warning is present and reachable, not just something I meant to add. */
    @Test
    void theShippedFileCarriesItsWarning() throws Exception {
        String json;
        try (InputStream in = DungeonGenerationPitchTest.class.getResourceAsStream(
                "/data/dungeons2/dungeons2/generation_config/default.json")) {
            assertTrue(in != null, "generation_config/default.json is not on the test classpath");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(json.contains("_comment"),
                "the shipped config carries no _comment -- the pitch warning has been dropped");
        assertTrue(json.contains("floorHeight") && json.contains("gapBetweenFloors"),
                "the shipped config no longer states the pitch explicitly, so the _comment has "
                        + "nothing to point at");
        // And it still loads, which is the half that a plain text assertion cannot cover.
        assertEquals(DungeonGenerationConfig.DEFAULT, ok(json));
    }

    /** {@code _comment} is accepted and carries no state. */
    @Test
    void theCommentIsIgnoredRatherThanStored() {
        assertEquals(DungeonGenerationConfig.DEFAULT,
                ok("{ \"_comment\": [\"anything at all\"] }"));
        assertEquals(DungeonGenerationConfig.DEFAULT, ok("{ \"_comment\": \"a bare string too\" }"));
    }

    /**
     * The schema is still closed. Declaring {@code _comment} opens exactly one door and no others
     * &mdash; if it opened the record generally, every typo would go back to being silent.
     */
    @Test
    void declaringACommentDidNotOpenTheSchema() {
        DataResult<DungeonGenerationConfig> parsed = parse("{ \"floorHieght\": 12 }");
        assertTrue(parsed.error().isPresent(), "a misspelled key decoded without complaint");
        assertTrue(parsed.error().orElseThrow().message().contains("floorHieght"),
                parsed.error().orElseThrow().message());
    }

    // ---------- the budget interaction ----------

    /**
     * #51's bands are clamped into whatever the pitch leaves, not rejected in favour of the shipped
     * table. The fallback was wrong in exactly this case: lower {@code floorHeight} below 10 and
     * the shipped table does not fit either, so falling back to it hands the planner the table just
     * refused.
     */
    @Test
    void loweringTheFloorHeightClampsTheBandsRatherThanFallingBack() {
        List<RoomHeightBand> clamped = RoomHeightBand.clampToBudget(
                DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS, 8);
        assertTrue(RoomHeightBand.validateAgainstBudget(clamped, 8),
                "a clamped table still exceeds the budget: " + clamped);
        assertEquals(8, RoomHeightBand.forLongSide(clamped, 7).maxHeight());
        // The bands that already fit are untouched.
        assertEquals(7, RoomHeightBand.forLongSide(clamped, 19).maxHeight());
    }

    @Test
    void clampingLeavesAFittingTableAlone() {
        List<RoomHeightBand> table = DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS;
        assertTrue(table == RoomHeightBand.clampToBudget(table, 10),
                "a table that already fits should not be rebuilt");
    }

    /** A band whose whole range is above the budget collapses rather than inverting. */
    @Test
    void aBandEntirelyAboveTheBudgetCollapses() {
        List<RoomHeightBand> clamped = RoomHeightBand.clampToBudget(
                List.of(new RoomHeightBand(Optional.empty(), 12, 15)), 9);
        RoomHeightBand only = clamped.get(0);
        assertEquals(9, only.minHeight());
        assertEquals(9, only.maxHeight());
        assertEquals(9, only.clamp(5), "an inverted band would have produced nonsense here");
    }
}
