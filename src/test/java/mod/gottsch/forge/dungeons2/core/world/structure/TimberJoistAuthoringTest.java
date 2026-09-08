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
package mod.gottsch.forge.dungeons2.core.world.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <strong>The rule and the palette have to agree, per band.</strong> Backlog #15 gave the timber
 * chain a {@code joist} gate, which is only ever as good as where the palette actually puts a
 * {@code minecraft:spruce_log}: a log the motif uses as a beam decays to a legible gap, and the
 * identical log used as a post would be left hanging. The gate now decides that <em>structurally</em>
 * rather than by an author's discipline &mdash; but only for logs it can see, and only in the file
 * that carries the rule.
 *
 * <h2>The two bands make opposite choices, and both are on purpose</h2>
 * <ul>
 *   <li><strong>classic</strong> uses spruce for beams and brackets and nothing else, and ages the
 *       beam at 15%. That is what {@code joist} now pins.</li>
 *   <li><strong>the mud stratum</strong> uses spruce as a PILLAR SHAFT, and deliberately ages no log
 *       at all &mdash; <em>"the timber is SHORING"</em> (Mark, 2026-08-31). The band is the dungeon
 *       being reclaimed from underneath and the wood is what someone recently put in to hold it up,
 *       so fresh timber against rotten brick reads as repair. Do not "fix" that absence by adding a
 *       rule; spruce staying crisp is also half the reason {@code mud_diamond_inlay}'s accent is
 *       spruce.</li>
 * </ul>
 *
 * <p>Both halves are invisible in game if they drift: a post quietly starts gapping, or a band's
 * timber quietly starts aging. Neither logs anything.</p>
 *
 * @author Mark Gottschling on Sep 4, 2026
 */
class TimberJoistAuthoringTest {

    private static final String LOG = "minecraft:spruce_log";
    private static final String JOISTS = "dungeons2:joists";
    private static final String SURFACE_AGING = "dungeons2:surface_aging";

    @Test
    void classicUsesSpruceLogOnlyAsAJoist() {
        // A joists pattern hangs its beams under the ceiling, which is exactly the geometry
        // PieceSurfaceMap calls a joist. Any OTHER use of the log in this motif -- a pillar shaft, a
        // wall course, a prop -- would be a cell the shipped rule silently no longer covers, and the
        // beams would go on decaying while it looked as though timber aging had simply been retuned.
        List<String> uses = new ArrayList<>();
        find(read("/data/dungeons2/dungeons2/motif_config/classic/base.json"), "", null, uses);

        assertFalse(uses.isEmpty(),
                "classic ships no " + LOG + " at all, so this test is asserting nothing."
                        + " If the motif really has dropped timber, delete the rule with it");

        for (String use : uses) {
            assertTrue(use.startsWith(JOISTS + " @ ") && use.endsWith(".config.block"),
                    "classic puts " + LOG + " at " + use + ", which is not a " + JOISTS
                            + " pattern's block. The shipped rule is gated on `joist`, so that log"
                            + " will never age -- either move it back, or widen the gate"
                            + " deliberately and re-read the TIMBER comment in"
                            + " classic_weathering.json first");
        }
    }

    @Test
    void classicAgesTheLogOnTheJoistGateAndTheBracketUngated() {
        JsonObject list = read("/data/dungeons2/worldgen/processor_list/classic_weathering.json");

        JsonObject log = ruleFor(list, LOG);
        assertNotNull(log, "classic no longer ages " + LOG);
        assertEquals("joist", log.get("surface").getAsString(),
                LOG + " must be gated on `joist`: it is the one rule that separates a beam, which"
                        + " may leave a gap with the shell intact, from a post, which may not");

        // The split is by ELEMENT, not by material (Mark, 2026-08-31). A corbel is a single cell and
        // reads as MISSING wherever it sits; only a run needs to know what it is holding up.
        JsonObject bracket = ruleFor(list, "dungeonblocks:spruce_corbel_block");
        assertNotNull(bracket, "classic no longer ages the spruce corbel");
        assertEquals("any", bracket.get("surface").getAsString(),
                "the bracket is deliberately ungated -- gating it would be reading the split as"
                        + " 'anything spruce', which is the obvious reading and the wrong one");
    }

    @Test
    void theMudBandUsesSpruceAsAShaftAndAgesNoLog() {
        JsonObject strata = read("/data/dungeons2/dungeons2/motif_config/classic/strata.json");
        Set<String> shafts = new LinkedHashSet<>();
        for (JsonElement band : strata.getAsJsonArray("strata_by_floor_index")) {
            JsonObject palette = band.getAsJsonObject().getAsJsonObject("palette");
            if (palette != null && palette.has("shaft")) {
                shafts.add(palette.get("shaft").getAsString());
            }
        }
        assertTrue(shafts.contains(LOG),
                "the mud band's pillar shaft is no longer " + LOG + ", which is the whole reason"
                        + " that band ages no log. If the palette changed, the decision below is"
                        + " open again rather than automatically still right");

        JsonObject mud = read("/data/dungeons2/worldgen/processor_list/classic_mud_weathering.json");
        assertNull(ruleFor(mud, LOG),
                "the mud band has a " + LOG + " rule. Its spruce is a PILLAR SHAFT and the absence"
                        + " is a decision, not an oversight (Mark, 2026-08-31): the timber is"
                        + " SHORING, and fresh wood against rotten brick reads as repair. A `joist`"
                        + " gate would make a rule mechanically safe here, but it would not make it"
                        + " the right call -- that is a separate decision, and Mark's");
    }

    // ---------- helpers ----------

    private static void assertNull(Object actual, String message) {
        assertTrue(actual == null, message);
    }

    /** The first rule in any aging processor of {@code list} keyed on {@code block}. */
    private static JsonObject ruleFor(JsonObject list, String block) {
        for (JsonElement element : list.getAsJsonArray("processors")) {
            JsonObject processor = element.getAsJsonObject();
            if (!SURFACE_AGING.equals(processor.get("processor_type").getAsString())) {
                continue;
            }
            for (JsonElement ruleElement : processor.getAsJsonArray("rules")) {
                JsonObject rule = ruleElement.getAsJsonObject();
                if (block.equals(rule.get("block").getAsString())) {
                    return rule;
                }
            }
        }
        return null;
    }

    /**
     * Every use of {@link #LOG} in {@code json}, as {@code "<pattern type> @ <dotted path>"}.
     *
     * <p>The pattern type is carried down rather than read off the path, because it is a SIBLING
     * key of the {@code config} the block sits in ({@code {"type": ..., "config": {"block": ...}}}),
     * so it never appears in the path itself. The nearest enclosing {@code type} wins, which is what
     * a nested pattern would want.</p>
     */
    private static void find(JsonElement json, String path, String enclosingType, List<String> out) {
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            String type = object.has("type") && object.get("type").isJsonPrimitive()
                    ? object.get("type").getAsString()
                    : enclosingType;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                find(entry.getValue(), path + "." + entry.getKey(), type, out);
            }
        } else if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                find(array.get(i), path + "[" + i + "]", enclosingType, out);
            }
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()
                && LOG.equals(json.getAsString())) {
            out.add(enclosingType + " @ " + path);
        }
    }

    private static JsonObject read(String resource) {
        try (InputStream in = TimberJoistAuthoringTest.class.getResourceAsStream(resource)) {
            assertTrue(in != null, "missing " + resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }
}
