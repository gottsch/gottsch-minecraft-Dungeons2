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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps {@code dungeons2:decoration_sweep} naming the same blocks as the
 * {@code dungeons2:decoration} entry it inverts, in every shipped processor list.
 *
 * <h2>Why this cannot be a code-side invariant</h2>
 * <p>A {@link net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor}
 * cannot see its siblings in the list it was decoded into &mdash; each is built from its own JSON
 * object and handed to vanilla independently. So the pairing between a decoration behaviour and the
 * sweep's counterpart is a convention held by the datapack, and the only place it can be enforced
 * is here, over the shipped files.
 *
 * <p>Drift is silent both ways, which is what makes it worth a build failure. Add a growth block to
 * {@code wall_growth} and forget the sweep, and that species alone keeps stranding itself on
 * facades at shared walls &mdash; the exact 2026-08-26 bug, back for one block. Remove one from the
 * decoration side only, and the sweep keeps deleting a block nothing places any more, which is
 * worse: it would start eating whatever a template author put there by hand.
 *
 * @author Mark Gottschling on Aug 26, 2026
 */
class DecorationSweepParityTest {

    private static final String DECORATION_TYPE = "dungeons2:decoration";
    private static final String SWEEP_TYPE = "dungeons2:decoration_sweep";

    /**
     * Every shipped list. A new motif or stratum file that forgets the sweep entirely is caught by
     * {@link #everyShippedListPairsTheTwo}, not by silence.
     */
    private static final String[] SHIPPED = {
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_mud_weathering.json",
    };

    @Test
    void everyShippedListPairsTheTwo() {
        for (String resource : SHIPPED) {
            assertNotNull(entry(resource, DECORATION_TYPE), resource + " has no decoration entry");
            assertNotNull(entry(resource, SWEEP_TYPE),
                    resource + " decorates but never sweeps -- growth there will strand itself"
                            + " on any shared wall an authored piece re-skins");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_mud_weathering.json",
    })
    void theSweepNamesWhatTheDecorationPassPlaces(String resource) {
        JsonObject decoration = entry(resource, DECORATION_TYPE);
        JsonObject sweep = entry(resource, SWEEP_TYPE);

        assertPalette(resource, decoration, "wall_growth", sweep, "growth");
        assertPalette(resource, decoration, "cobwebs", sweep, "webs");
        assertPalette(resource, decoration, "corner_cobwebs", sweep, "corner_webs");
        assertPalette(resource, decoration, "floor_growth", sweep, "floor_growth");
        assertPalette(resource, decoration, "hanging_growth", sweep, "hanging_growth");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_mud_weathering.json",
    })
    void theTwoBlockMatchesAreCopiedVerbatim(String resource) {
        JsonObject decoration = entry(resource, DECORATION_TYPE);
        JsonObject sweep = entry(resource, SWEEP_TYPE);

        // `dirt` is not a palette but the predicate floor_growth and hanging_growth are tested
        // against, and `unsupported` is a tag set on both sides. Both are BlockMatch objects, so
        // they compare whole rather than element-wise.
        assertEquals(decoration.get("dirt"), sweep.get("dirt"),
                resource + ": the sweep's `dirt` must be the decoration pass's `dirt`,"
                        + " or floor/hanging growth is re-tested against a different rule");
        assertEquals(decoration.get("unsupported"), sweep.get("unsupported"),
                resource + ": the sweep's `unsupported` must be the decoration pass's");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_mud_weathering.json",
    })
    void theSweepClaimsNoEntities(String resource) {
        // floor_growth's palette holds two `entity` entries -- GMM's Shrieker and Violet Fungus,
        // grown as if they were plants (#54). They are MOBS: the sweep works on block states and
        // can neither see nor remove one, so naming them here would be a promise it cannot keep.
        JsonObject sweep = entry(resource, SWEEP_TYPE);
        for (String field : new String[] {"growth", "webs", "corner_webs", "floor_growth",
                "hanging_growth"}) {
            JsonElement palette = sweep.get(field);
            if (palette == null) {
                continue;
            }
            for (JsonElement element : palette.getAsJsonObject().getAsJsonArray("blocks")) {
                assertFalse(element.isJsonObject() && element.getAsJsonObject().has("entity"),
                        resource + ": " + field + " names an entity; the sweep only handles blocks");
            }
        }
    }

    /**
     * Compares one decoration palette against its sweep counterpart, as sets of block ids.
     *
     * <p>Three shape differences are normalised away rather than asserted on, because none of them
     * changes which blocks are named: the decoration side may weight an entry
     * ({@code {block, weight}} vs a bare id), it may carry {@code entity} entries the sweep cannot
     * have, and it carries a {@code probability} the sweep has no use for.
     */
    private static void assertPalette(String resource, JsonObject decoration, String decorationField,
                                      JsonObject sweep, String sweepField) {
        JsonElement rule = decoration.get(decorationField);
        JsonElement counterpart = sweep.get(sweepField);
        if (rule == null) {
            assertTrue(counterpart == null, resource + ": sweep declares " + sweepField
                    + " but the decoration pass has no " + decorationField + " to invert");
            return;
        }
        assertNotNull(counterpart, resource + ": decoration declares " + decorationField
                + " but the sweep has no " + sweepField + " to clean up after it");

        assertEquals(blockIds(rule), blockIds(counterpart),
                resource + ": " + decorationField + " and " + sweepField + " name different blocks");
    }

    /** The block ids in a palette, whatever shape its entries take; entity entries are dropped. */
    private static Set<String> blockIds(JsonElement palette) {
        Set<String> ids = new LinkedHashSet<>();
        JsonArray blocks = palette.getAsJsonObject().getAsJsonArray("blocks");
        assertNotNull(blocks, "palette has no `blocks` array: " + palette);
        for (JsonElement element : blocks) {
            if (element.isJsonPrimitive()) {
                ids.add(element.getAsString());
            } else if (element.getAsJsonObject().has("block")) {
                ids.add(element.getAsJsonObject().get("block").getAsString());
            }
            // `entity` entries are mobs, not blocks -- see theSweepClaimsNoEntities.
        }
        return ids;
    }

    /** The first processor of the given type in a shipped list, or null. */
    private static JsonObject entry(String resource, String type) {
        try (InputStream in = DecorationSweepParityTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing shipped resource " + resource);
            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("processors")) {
                JsonObject processor = element.getAsJsonObject();
                if (type.equals(processor.get("processor_type").getAsString())) {
                    return processor;
                }
            }
            return null;
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }
}
