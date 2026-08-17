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
package mod.gottsch.forge.dungeons2.core.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every id in a motif's {@code templateLimits} must name a template some pool actually references.
 *
 * <h2>Why this cannot be a load error</h2>
 * <p>Template pools are datapack content resolved through a registry at a different point in the
 * load than the motif config, so at decode time "no pool references this" and "the pools have not
 * been read yet" are indistinguishable &mdash; the same split {@code ShippedMobSetsTest} documents
 * for mob sets and {@code SpawnerMarkerProcessor} for its own set.</p>
 *
 * <p>Which leaves the typo completely silent: a limit keyed on
 * {@code dungeons2:rooms/classic/11x11/mighty_hal} caps nothing, logs nothing, and looks exactly
 * like a limit that is simply never reached. The room it was meant to constrain goes on appearing
 * four times a dungeon. This turns that into a build failure.</p>
 *
 * <p>Nothing ships a limit today, so this currently passes over an empty set &mdash; deliberately
 * not asserted as non-vacuous, because "this pack caps no template" is the correct state until a
 * room exists that needs capping. The check goes live the moment one is added.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
class ShippedTemplateLimitsTest {

    private static final String MOTIF_CONFIGS = "/data/dungeons2/dungeons2/motif_config";
    private static final String TEMPLATE_POOLS = "/data/dungeons2/worldgen/template_pool";

    @Test
    void everyLimitedTemplateIsReferencedByAPool() {
        Set<String> pooled = pooledTemplateIds();
        List<String> dangling = new ArrayList<>();

        for (Path file : jsonFilesUnder(MOTIF_CONFIGS)) {
            JsonObject fragment = parse(file).getAsJsonObject();
            if (!fragment.has("templateLimits")) {
                continue;
            }
            for (Map.Entry<String, JsonElement> limit
                    : fragment.getAsJsonObject("templateLimits").entrySet()) {
                if (!pooled.contains(limit.getKey())) {
                    dangling.add(file.getParent().getFileName() + "/" + file.getFileName()
                            + " -> " + limit.getKey());
                }
            }
        }

        if (!dangling.isEmpty()) {
            org.junit.jupiter.api.Assertions.fail(dangling.size() + " templateLimits entry/entries"
                    + " name a template no pool references, so they cap nothing and say nothing:\n  "
                    + String.join("\n  ", dangling)
                    + "\nPooled templates:\n  " + String.join("\n  ", pooled));
        }
    }

    /** Every {@code location} any shipped template pool names, in any category. */
    private static Set<String> pooledTemplateIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Path file : jsonFilesUnder(TEMPLATE_POOLS)) {
            JsonObject pool = parse(file).getAsJsonObject();
            if (!pool.has("elements")) {
                continue;
            }
            for (JsonElement wrapped : pool.getAsJsonArray("elements")) {
                JsonObject element = wrapped.getAsJsonObject().getAsJsonObject("element");
                if (element != null && element.has("location")) {
                    ids.add(element.get("location").getAsString());
                }
            }
        }
        return ids;
    }

    /** The sweep passes vacuously if the pools are not being read; that much IS worth asserting. */
    @Test
    void theSweepFindsTheShippedPools() {
        org.junit.jupiter.api.Assertions.assertFalse(pooledTemplateIds().isEmpty(),
                "no template pool locations were found under " + TEMPLATE_POOLS
                        + ", so a dangling limit could never be detected");
    }

    // ---------- reading ----------

    private static JsonElement parse(Path file) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static List<Path> jsonFilesUnder(String resourceDir) {
        URL url = ShippedTemplateLimitsTest.class.getResource(resourceDir);
        if (url == null) {
            return org.junit.jupiter.api.Assertions.fail("no shipped content at " + resourceDir);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return org.junit.jupiter.api.Assertions.fail("could not walk " + resourceDir + ": " + unreadable);
        }
    }
}
