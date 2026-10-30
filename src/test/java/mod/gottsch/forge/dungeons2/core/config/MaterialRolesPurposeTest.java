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
 * One role per purpose (Mark, 2026-10-30). {@code footing} is the base of a column;
 * {@code floor_accent} is the flat stone a floor pattern sets into the paving; {@code springing_inlay}
 * is the course a rising vault springs from. The first two were one role until a big chamber came out
 * with a pillar base set flat into each corner of its floor border, reading as an upside-down column;
 * vault springing was {@code inlay} (shared with every flush border and trim course) or
 * {@code footing}. A shared role repaints things nobody meant to change together.
 *
 * @author Mark Gottschling on 10/30/2026
 */
class MaterialRolesPurposeTest {

    private static final String ROOT = "/data/dungeons2/dungeons2/motif_config/classic/";

    /** No shipped floor pattern, in any scheme or band, lays a column base into the paving. */
    @Test
    void noShippedFloorPatternUsesFooting() throws Exception {
        List<String> found = new ArrayList<>();
        for (JsonElement json : shipped()) {
            walk(json, "", false, found);
        }
        assertTrue(found.isEmpty(), "floor patterns naming $footing, a column's base: " + found);
    }

    /** Every vault that springs from a role springs from its own: not trim's, not a column's. */
    @Test
    void everyRoleSpringingCourseUsesSpringingInlay() throws Exception {
        List<String> found = new ArrayList<>();
        for (JsonElement json : shipped()) {
            springing(json, "", found);
        }
        assertTrue(found.isEmpty(), "springing courses on a shared role: " + found);
    }

    private static List<JsonElement> shipped() throws Exception {
        List<JsonElement> files = new ArrayList<>();
        for (String file : List.of("base.json", "schemes.json", "strata.json")) {
            try (InputStream in = MaterialRolesPurposeTest.class.getResourceAsStream(ROOT + file)) {
                assertTrue(in != null, file + " is not on the test classpath");
                files.add(new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                        JsonElement.class));
            }
        }
        return files;
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

    /** A literal springing block is fine -- it shares nothing. A role must be springing_inlay. */
    private static void springing(JsonElement json, String path, List<String> found) {
        if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (entry.getKey().equals("springing_block") && value.isJsonPrimitive()
                        && value.getAsString().startsWith("$")
                        && !value.getAsString().equals("$springing_inlay")) {
                    found.add(path + "." + entry.getKey() + " = " + value.getAsString());
                }
                springing(value, path + "." + entry.getKey(), found);
            }
        } else if (json.isJsonArray()) {
            for (int i = 0; i < json.getAsJsonArray().size(); i++) {
                springing(json.getAsJsonArray().get(i), path + "[" + i + "]", found);
            }
        }
    }

    /** Each band answers the new roles in its own stone; mud keeps the square stone it always laid. */
    @Test
    void everyBandAnswersTheNewRolesInItsOwnStone() {
        MotifConfig classic = MotifConfigs.load("classic");
        assertEquals(Optional.of("minecraft:chiseled_stone_bricks"), classic.forFloor(1).role("floor_accent"));
        assertEquals(Optional.of("minecraft:chiseled_deepslate"), classic.forFloor(4).role("floor_accent"));
        assertEquals(Optional.of("dungeonblocks:square_stone_brick"), classic.forFloor(0).role("floor_accent"));
        // springing_inlay was introduced at exactly what each band's vaults already sprang from
        assertEquals(classic.forFloor(1).role("inlay"), classic.forFloor(1).role("springing_inlay"));
        assertEquals(classic.forFloor(4).role("inlay"), classic.forFloor(4).role("springing_inlay"));
        assertEquals(classic.forFloor(0).role("footing"), classic.forFloor(0).role("springing_inlay"));
    }
}
