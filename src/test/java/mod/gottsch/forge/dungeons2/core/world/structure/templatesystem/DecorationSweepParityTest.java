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
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final String DIR = "/data/dungeons2/worldgen/processor_list";
    private static final String DECORATION_TYPE = "dungeons2:decoration";
    private static final String SWEEP_TYPE = "dungeons2:decoration_sweep";

    /**
     * Every shipped list. A new motif or stratum file that forgets the sweep entirely is caught by
     * {@link #everyShippedListPairsTheTwo}, not by silence &mdash; provided it is named here, which
     * {@link #everyShippedListIsChecked} is what enforces.
     */
    private static final String[] SHIPPED = {
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_mud_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_entrance_weathering.json",
            // The boss room's list (2026-09-03). It carries NO dungeons2:decoration and therefore
            // no sweep either -- the sweep exists only to repair what decoration placed. Named
            // here anyway: the parity check has to see a list with neither and agree that neither
            // is correct, which is a different assertion from never looking at the file.
            "/data/dungeons2/worldgen/processor_list/classic_boss_weathering.json",
    };

    /**
     * The guard on the guard, added 2026-08-30 with #61. A processor list on disk that
     * {@link #SHIPPED} does not name is a list nothing here checks, and the failure it hides is
     * silent in game &mdash; which is the whole premise of this class.
     *
     * <p>It is not hypothetical: {@code classic_entrance_weathering.json} shipped and was not
     * checked here for four days, for no reason other than that the array was written before the
     * file existed. Exactly the shape of #61 itself, one directory over.</p>
     */
    @Test
    void everyShippedListIsChecked() {
        Set<String> onDisk = listDirectory();
        Set<String> named = new LinkedHashSet<>(List.of(SHIPPED));

        Set<String> unchecked = new LinkedHashSet<>(onDisk);
        unchecked.removeAll(named);
        assertTrue(unchecked.isEmpty(),
                "processor list(s) " + unchecked + " ship but are not in SHIPPED, so nothing checks"
                        + " that their sweep matches their decoration pass. Add them -- to the"
                        + " @ValueSource lists too.");

        Set<String> gone = new LinkedHashSet<>(named);
        gone.removeAll(onDisk);
        assertTrue(gone.isEmpty(), "SHIPPED names " + gone + ", which no longer exist");
    }

    /**
     * JUnit cannot parameterize from a field, so the three {@code @ValueSource} lists restate
     * {@link #SHIPPED} by hand. This is what stops one of them being updated and the others not
     * &mdash; a drift that costs no failure and quietly halves the coverage.
     */
    @Test
    void everyParameterizedListMatchesShipped() {
        Set<String> expected = new LinkedHashSet<>(List.of(SHIPPED));
        for (Method method : DecorationSweepParityTest.class.getDeclaredMethods()) {
            ValueSource source = method.getAnnotation(ValueSource.class);
            if (source == null) {
                continue;
            }
            assertEquals(expected, new LinkedHashSet<>(List.of(source.strings())),
                    method.getName() + "'s @ValueSource has drifted from SHIPPED");
        }
    }

    /**
     * The two are paired: both, or neither.
     *
     * <p>Was "both, always", until the boss room's list shipped with neither (2026-09-03) and
     * failed here. Neither is a legitimate state and a meaningful one &mdash; that list exists
     * precisely to have no decoration, since a cobweb entangles the boss it is supposed to
     * threaten the player with. The sweep alone would be a pass over every cell to repair nothing.
     *
     * <p>What must never happen is <strong>one without the other</strong>, in either direction,
     * which is what this now says. Decorating without sweeping strands growth on any shared wall an
     * authored piece re-skins; sweeping without decorating is dead weight that reads as coverage.</p>
     */
    @Test
    void everyShippedListPairsTheTwo() {
        for (String resource : SHIPPED) {
            boolean decorates = entry(resource, DECORATION_TYPE) != null;
            boolean sweeps = entry(resource, SWEEP_TYPE) != null;
            assertEquals(decorates, sweeps, resource + (decorates
                    ? " decorates but never sweeps -- growth there will strand itself on any shared"
                            + " wall an authored piece re-skins"
                    : " sweeps but never decorates -- the sweep exists only to repair what the"
                            + " decoration pass placed, so it has nothing to do"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_mud_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_entrance_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_boss_weathering.json",
    })
    void theSweepNamesWhatTheDecorationPassPlaces(String resource) {
        JsonObject decoration = entry(resource, DECORATION_TYPE);
        JsonObject sweep = entry(resource, SWEEP_TYPE);
        if (decoration == null && sweep == null) {
            // A list with neither, which everyShippedListPairsTheTwo has already accepted as a
            // pairing. Named in @ValueSource rather than left out so that everyParameterized
            // ListMatchesShipped keeps agreeing with SHIPPED -- the drift that check exists to
            // catch is exactly "one of these lists was updated and the others were not".
            return;
        }

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
            "/data/dungeons2/worldgen/processor_list/classic_entrance_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_boss_weathering.json",
    })
    void theTwoBlockMatchesAreCopiedVerbatim(String resource) {
        JsonObject decoration = entry(resource, DECORATION_TYPE);
        JsonObject sweep = entry(resource, SWEEP_TYPE);
        if (decoration == null && sweep == null) {
            return;
        }

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
            "/data/dungeons2/worldgen/processor_list/classic_entrance_weathering.json",
            "/data/dungeons2/worldgen/processor_list/classic_boss_weathering.json",
    })
    void theSweepClaimsNoEntities(String resource) {
        // floor_growth's palette holds two `entity` entries -- GMM's Shrieker and Violet Fungus,
        // grown as if they were plants (#54). They are MOBS: the sweep works on block states and
        // can neither see nor remove one, so naming them here would be a promise it cannot keep.
        JsonObject sweep = entry(resource, SWEEP_TYPE);
        if (sweep == null) {
            // No sweep because no decoration -- see everyShippedListPairsTheTwo.
            return;
        }
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
    /** The processor_list directory as absolute resource paths, matching {@link #SHIPPED}. */
    private static Set<String> listDirectory() {
        try {
            URL url = DecorationSweepParityTest.class.getResource(DIR);
            assertNotNull(url, "Missing shipped directory " + DIR);
            try (Stream<Path> files = Files.list(Paths.get(url.toURI()))) {
                return files.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".json"))
                        .map(n -> DIR + "/" + n)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        } catch (Exception e) {
            throw new AssertionError("Could not list " + DIR, e);
        }
    }

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
