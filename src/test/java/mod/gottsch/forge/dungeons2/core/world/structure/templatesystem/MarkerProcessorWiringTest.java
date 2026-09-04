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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every marker processor must be reachable from every shipped weathering list. Backlog #61.
 *
 * <h2>The failure this exists to make impossible</h2>
 * <p>A marker processor converts an authored block into the thing it stands for &mdash; a chest, a
 * pot, a spawner. If no processor list a pool names carries it, <strong>the marker block is left
 * standing in the finished dungeon</strong>: bright, obviously wrong, and only discoverable by
 * walking to it.</p>
 *
 * <p>That is exactly what happened to {@code dungeons2:chest}. It shipped in a processor list of its
 * own, {@code dungeons2:classic_chests}, built for one throwaway test template during #48's in-game
 * trip ({@code 0bc6a45}). The template was removed again ({@code 93baad8}) and the list stayed,
 * referenced by nothing, for eleven days &mdash; a whole feature reachable from no pool in the mod.
 * Nothing failed, because nothing was asking this question.</p>
 *
 * <h2>Why "every list" and not "some list"</h2>
 * <p>A pool names exactly one weathering list, so a marker in a list the pool did not name is a
 * marker left standing. The lists are per motif and per stratum ({@code classic_mud_weathering}) and
 * per category ({@code classic_entrance_weathering}), and an author placing a chest in a mud-band
 * room has no reason to know which file governs it. The invariant is therefore total: all markers,
 * all lists.</p>
 *
 * <p>{@link #everyShippedListIsChecked} is what keeps that true as files are added &mdash; a new
 * motif's list appearing on disk fails here until it is named below, rather than quietly being the
 * one list nobody checked.</p>
 *
 * @author Mark Gottschling on Aug 30, 2026
 */
class MarkerProcessorWiringTest {

    private static final String DIR = "/data/dungeons2/worldgen/processor_list";

    /**
     * The marker processors, by dispatch key. Each turns an authored block into something else, and
     * each leaves that block standing when it does not run.
     */
    private static final List<String> MARKER_TYPES = List.of(
            "dungeons2:spawner",
            "dungeons2:pot",
            "dungeons2:chest");

    /** Every shipped list, checked against the directory by {@link #everyShippedListIsChecked}. */
    private static final Set<String> SHIPPED = Set.of(
            "classic_weathering.json",
            "classic_mud_weathering.json",
            "classic_entrance_weathering.json",
            // The boss room's list (2026-09-03) is the smallest one that ships -- no decoration,
            // no sweep, four aging rules. It is NOT exempt here and must not become so: #61 is
            // about a marker processor missing from a list a pool names, and a deliberately
            // minimal list is exactly where that omission is easiest to make and hardest to see.
            "classic_boss_weathering.json");

    @Test
    void everyShippedListCarriesEveryMarkerProcessor() {
        List<String> missing = new ArrayList<>();
        for (String file : SHIPPED) {
            Set<String> types = processorTypes(file);
            for (String marker : MARKER_TYPES) {
                if (!types.contains(marker)) {
                    missing.add(file + " is missing " + marker);
                }
            }
        }
        if (!missing.isEmpty()) {
            fail("a marker processor absent from a list a pool names leaves its marker BLOCK"
                    + " standing in the finished dungeon -- see this class:\n  "
                    + String.join("\n  ", missing));
        }
    }

    /**
     * The guard on the guard. A new motif or stratum list on disk that {@link #SHIPPED} does not
     * name is a list this test is not checking, which is how a sweep quietly stops sweeping.
     */
    @Test
    void everyShippedListIsChecked() {
        Set<String> onDisk = listDirectory();
        Set<String> unchecked = new LinkedHashSet<>(onDisk);
        unchecked.removeAll(SHIPPED);
        assertTrue(unchecked.isEmpty(),
                "processor list(s) " + unchecked + " ship but are not in SHIPPED, so nothing checks"
                        + " that their markers are wired. Add them.");

        Set<String> gone = new LinkedHashSet<>(SHIPPED);
        gone.removeAll(onDisk);
        assertTrue(gone.isEmpty(), "SHIPPED names " + gone + ", which no longer exist");
    }

    /**
     * A marker processor is only useful if it runs against something. This does not assert that a
     * template carries one &mdash; none does yet, and that is legitimate &mdash; only that the list
     * of markers above has not silently emptied out.
     */
    @Test
    void thereAreMarkerProcessorsToCheck() {
        assertTrue(MARKER_TYPES.size() >= 3, "expected the spawner, pot and chest markers");
    }

    // ---------- reading ----------

    private static Set<String> processorTypes(String file) {
        String resource = DIR + "/" + file;
        try (InputStream in = MarkerProcessorWiringTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing shipped resource " + resource);
            // JsonParser in lenient mode is what reads these files everywhere else in the suite:
            // the shipped lists carry // comments, which strict JSON does not allow and which are
            // load-bearing documentation.
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray processors = root.getAsJsonArray("processors");
            Set<String> types = new LinkedHashSet<>();
            processors.forEach(e -> types.add(
                    e.getAsJsonObject().get("processor_type").getAsString()));
            return types;
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }

    private static Set<String> listDirectory() {
        try {
            URL url = MarkerProcessorWiringTest.class.getResource(DIR);
            assertNotNull(url, "Missing shipped directory " + DIR);
            Path dir = Paths.get(url.toURI());
            try (Stream<Path> files = Files.list(dir)) {
                return files.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".json"))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        } catch (Exception e) {
            throw new AssertionError("Could not list " + DIR, e);
        }
    }
}
