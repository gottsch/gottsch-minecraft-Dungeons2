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
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The shipped advancements hang together: every title and description has a name, every parent
 * ships, every trigger is vanilla's or one D2 registers, and every structure or biome a location
 * criterion names ships. A broken one fails to load as a datapack error, or worse, never fires.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class ShippedAdvancementsTest {

    private static final String ROOT = "/data/dungeons2/advancements";
    private static final String LANG = "/assets/dungeons2/lang/en_us.json";
    /**
     * The non-vanilla criteria: D2's own (DungeonsTriggers), and Gottsch Books' book_read, which the
     * lore books' advancements use (#118).
     */
    private static final Set<String> D2_TRIGGERS = Set.of("dungeons2:reached_floor", "gottschbooks:book_read");

    @Test
    void everyAdvancementHangsTogether() {
        JsonObject lang = read(path(LANG)).getAsJsonObject();
        List<Path> files = files();
        assertTrue(files.size() >= 10, "expected the shipped advancements, found " + files.size());
        List<String> bad = new ArrayList<>();
        for (Path file : files) {
            String name = path(ROOT).relativize(file).toString().replace('\\', '/').replace(".json", "");
            JsonObject json = read(file).getAsJsonObject();
            if (json.has("display")) {
                for (String part : List.of("title", "description")) {
                    String key = json.getAsJsonObject("display").getAsJsonObject(part).get("translate").getAsString();
                    if (!lang.has(key)) {
                        bad.add(name + ": no lang for " + key);
                    }
                }
            }
            if (json.has("parent")) {
                String parent = json.get("parent").getAsString();
                if (parent.startsWith("dungeons2:") && !Files.exists(
                        path(ROOT).resolve(parent.substring("dungeons2:".length()) + ".json"))) {
                    bad.add(name + ": parent " + parent + " does not ship");
                }
            }
            for (var criterion : json.getAsJsonObject("criteria").entrySet()) {
                JsonObject c = criterion.getValue().getAsJsonObject();
                String trigger = c.get("trigger").getAsString();
                if (!trigger.startsWith("minecraft:") && !D2_TRIGGERS.contains(trigger)) {
                    bad.add(name + ": unknown trigger " + trigger);
                }
                JsonObject location = location(c);
                if (location != null) {
                    // Vanilla's structure test reads chunk references, written only within 8 chunks
                    // of the start; a dungeon sprawls further, so it never fired in game. "In a
                    // dungeon" is dungeons2:reached_floor (min_floor 0), read from the floor index.
                    if (location.has("structure")
                            && location.get("structure").getAsString().startsWith("dungeons2:")) {
                        bad.add(name + ": a D2 structure location never fires; use dungeons2:reached_floor");
                    }
                    check(location, "structure", "/data/dungeons2/worldgen/structure/", name, bad);
                    check(location, "biome", "/data/dungeons2/worldgen/biome/", name, bad);
                }
            }
        }
        if (!bad.isEmpty()) {
            fail(String.join("\n  ", bad));
        }
    }

    private static JsonObject location(JsonObject criterion) {
        if (!criterion.has("conditions")) {
            return null;
        }
        JsonObject conditions = criterion.getAsJsonObject("conditions");
        if (!conditions.has("player") || !conditions.get("player").isJsonObject()) {
            return null;
        }
        JsonObject player = conditions.getAsJsonObject("player");
        return player.has("location") ? player.getAsJsonObject("location") : null;
    }

    private static void check(JsonObject location, String field, String dir, String name, List<String> bad) {
        if (!location.has(field)) {
            return;
        }
        String id = location.get(field).getAsString();
        if (id.startsWith("dungeons2:")
                && ShippedAdvancementsTest.class.getResource(dir + id.substring("dungeons2:".length()) + ".json") == null) {
            bad.add(name + ": " + field + " " + id + " does not ship");
        }
    }

    private static Path path(String resource) {
        URL url = ShippedAdvancementsTest.class.getResource(resource);
        if (url == null) {
            return fail("nothing at " + resource);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException bad) {
            return fail(bad.toString());
        }
    }

    private static List<Path> files() {
        try (Stream<Path> paths = Files.walk(path(ROOT))) {
            return paths.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static JsonElement read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
