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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code footing} is the base of a column; {@code floor_accent} is the flat stone a floor pattern
 * sets into the paving. They were one role until a big chamber came out with a pillar base set flat
 * into each corner of its floor border, reading as an upside-down column (Mark, 2026-10-30).
 *
 * @author Mark Gottschling on 10/30/2026
 */
class FloorAccentRoleTest {

    private static final String ROOT = "/data/dungeons2/dungeons2/motif_config/classic/";

    /** No shipped floor pattern, in any scheme or band, lays a column base into the paving. */
    @Test
    void noShippedFloorPatternUsesFooting() throws Exception {
        List<String> found = new ArrayList<>();
        for (String file : List.of("base.json", "schemes.json", "strata.json")) {
            try (InputStream in = FloorAccentRoleTest.class.getResourceAsStream(ROOT + file)) {
                assertTrue(in != null, file + " is not on the test classpath");
                JsonElement json = new Gson().fromJson(
                        new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
                walk(json, file, false, found);
            }
        }
        assertTrue(found.isEmpty(), "floor patterns naming $footing, a column's base: " + found);
    }

    private static void walk(JsonElement json, String path, boolean inFloor, List<String> found) {
        if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                walk(entry.getValue(), path + "." + entry.getKey(),
                        inFloor || entry.getKey().equals("floor"), found);
            }
        } else if (json.isJsonArray()) {
            for (int i = 0; i < json.getAsJsonArray().size(); i++) {
                walk(json.getAsJsonArray().get(i), path + "[" + i + "]", inFloor, found);
            }
        } else if (inFloor && json.isJsonPrimitive() && "$footing".equals(json.getAsString())) {
            found.add(path);
        }
    }

    /** Each band answers the new role in its own stone; mud keeps the square stone it always laid. */
    @Test
    void everyBandAnswersFloorAccentInItsOwnStone() {
        MotifConfig classic = MotifConfigs.load("classic");
        assertEquals(Optional.of("minecraft:chiseled_stone_bricks"), classic.forFloor(1).role("floor_accent"));
        assertEquals(Optional.of("minecraft:chiseled_deepslate"), classic.forFloor(4).role("floor_accent"));
        assertEquals(Optional.of("dungeonblocks:square_stone_brick"), classic.forFloor(0).role("floor_accent"));
    }
}
