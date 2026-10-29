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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The Delver's Companion's own references resolve: every entry's category ships, every
 * {@code advancement:} gate ships, every {@code entry:} link names an entry (and anchor) that exists,
 * every {@code dungeons2:} item or mob it shows or lists has a model or is registered, and only the
 * {@code items:} lists answer item lookups. The rest is what
 * {@code /gottschbooks check} finds in game; this catches the D2 half without launching it.
 *
 * @author Mark Gottschling on 10/28/2026
 */
class DelversCompanionTest {

    private static final String BOOK = "/assets/dungeons2/books/delvers_companion/en_us";
    private static final String ENTITIES =
            "/mod/gottsch/forge/dungeons2/core/entity/DungeonsEntities.java";

    private static final Pattern FRONT = Pattern.compile("^(\\w+): (.+)$", Pattern.MULTILINE);
    private static final Pattern LINK = Pattern.compile("\\]\\(entry:([\\w/]+)(?:#([\\w-]+))?\\)");
    private static final Pattern ANCHOR = Pattern.compile("\\{#([\\w-]+)\\}");
    private static final Pattern BLOCK = Pattern.compile("^```(spotlight|entity) (\\S+)", Pattern.MULTILINE);

    @Test
    void everyReferenceResolves() {
        Path root = path(BOOK);
        assertTrue(Files.exists(root.resolve("_index.md")), "a manual needs _index.md");
        Map<String, String> entries = new HashMap<>();
        for (Path file : markdown(root)) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            if (!rel.startsWith("categories/") && !rel.equals("_index.md")) {
                entries.put(rel.replace(".md", ""), read(file));
            }
        }
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String name = entry.getKey();
            String text = entry.getValue();
            Map<String, String> front = front(text);
            String category = front.get("category");
            if (category == null || !Files.exists(root.resolve("categories/" + category + ".md"))) {
                bad.add(name + ": category " + category + " does not ship");
            }
            String gate = front.get("advancement");
            if (gate != null && gate.startsWith("dungeons2:") && getClass().getResource(
                    "/data/dungeons2/advancements/" + gate.substring("dungeons2:".length()) + ".json") == null) {
                bad.add(name + ": advancement " + gate + " does not ship");
            }
            checkItem(name, "icon", front.get("icon"), bad);
            if (front.containsKey("items")) {
                for (String item : items(front.get("items"))) {
                    checkItem(name, "items", item, bad);
                }
            }
            Matcher link = LINK.matcher(text);
            while (link.find()) {
                String target = entries.get(link.group(1));
                if (target == null) {
                    bad.add(name + ": links to missing entry " + link.group(1));
                } else if (link.group(2) != null && !anchors(target).contains(link.group(2))) {
                    bad.add(name + ": links to missing anchor " + link.group(1) + "#" + link.group(2));
                }
            }
            Matcher block = BLOCK.matcher(text);
            while (block.find()) {
                if (block.group(1).equals("entity")) {
                    checkEntity(name, block.group(2), bad);
                } else {
                    checkItem(name, "spotlight", block.group(2), bad);
                }
            }
        }
        if (!bad.isEmpty()) {
            fail(String.join("\n  ", bad));
        }
    }

    /**
     * Without {@code lookup_from: items} an entry's icon and spotlights answer item lookups too, so a
     * vanilla icon (the Dungeon Maps entry's filled map) would claim every stack of that item.
     */
    @Test
    void onlyTheItemsListsAnswerLookups() {
        Map<String, String> book = front(read(path(BOOK).resolve("../book.md")));
        assertTrue("items".equals(book.get("lookup_from")), "book.md must say lookup_from: items");
    }

    /** {@code items:} values, split on the commas outside an NBT {@code {...}}. */
    private static List<String> items(String value) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        StringBuilder item = new StringBuilder();
        for (char c : value.toCharArray()) {
            depth += c == '{' ? 1 : c == '}' ? -1 : 0;
            if (c == ',' && depth == 0) {
                items.add(item.toString().trim());
                item.setLength(0);
            } else {
                item.append(c);
            }
        }
        items.add(item.toString().trim());
        return items;
    }

    private void checkItem(String name, String what, String id, List<String> bad) {
        if (id != null && id.contains("{")) {
            id = id.substring(0, id.indexOf('{'));
        }
        if (id != null && id.startsWith("dungeons2:") && getClass().getResource(
                "/assets/dungeons2/models/item/" + id.substring("dungeons2:".length()) + ".json") == null) {
            bad.add(name + ": " + what + " " + id + " has no item model");
        }
    }

    private static void checkEntity(String name, String id, List<String> bad) {
        if (!id.startsWith("dungeons2:")) {
            return;
        }
        String constant = "\"" + id.substring("dungeons2:".length()) + "\"";
        Path source = Paths.get("src/main/java" + ENTITIES);
        if (!read(source).contains(constant)) {
            bad.add(name + ": entity " + id + " is not registered in DungeonsEntities");
        }
    }

    private static Map<String, String> front(String text) {
        Map<String, String> front = new HashMap<>();
        if (!text.startsWith("---")) {
            return front;
        }
        int end = text.indexOf("\n---", 3);
        Matcher line = FRONT.matcher(text.substring(3, end));
        while (line.find()) {
            front.put(line.group(1), line.group(2).trim());
        }
        return front;
    }

    private static List<String> anchors(String text) {
        List<String> anchors = new ArrayList<>();
        Matcher anchor = ANCHOR.matcher(text);
        while (anchor.find()) {
            anchors.add(anchor.group(1));
        }
        return anchors;
    }

    private static List<Path> markdown(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Path path(String resource) {
        URL url = DelversCompanionTest.class.getResource(resource);
        if (url == null) {
            return fail("nothing at " + resource);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException bad) {
            return fail(bad.toString());
        }
    }
}
