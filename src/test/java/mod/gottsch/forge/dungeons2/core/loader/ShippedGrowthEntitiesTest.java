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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every mob a shipped growth palette names actually exists, and is rarer than the plants it grows
 * among.
 *
 * <h2>Why this needs its own sweep</h2>
 * <p>A growth palette entry may name an <em>entity</em> rather than a block
 * ({@code { "entity": "dungeons2:shrieker", "weight": 1 }}), which is how the Shrieker and Violet
 * Fungus are grown on weathered dirt. That id is resolved at <em>generation</em> time, not at load:
 * {@code DecorationProcessor} warns and spawns nothing if it does not resolve. So a typo is not a
 * load error, it is a dungeon quietly missing its fungi &mdash; and
 * {@code ShippedBlockIdsTest} cannot help, because it classifies {@code entity} as a
 * <em>non</em>-block key and skips it.</p>
 *
 * <h2>What "exists" means without a running game</h2>
 * <p>The entity registry is unreachable here (Forge's {@code DeferredRegister} never fires in a
 * plain JUnit run), so this checks the same way {@code ShippedBlockIdsTest} checks modded blocks:
 * against this mod's own shipped files. A {@code dungeons2:} mob has a lang key
 * {@code entity.dungeons2.<name>}, and that key is only there if someone registered and named the
 * mob. It is indirect, and it catches the thing that actually goes wrong &mdash; a misspelling in
 * one of the two files.</p>
 */
class ShippedGrowthEntitiesTest {

    private static final String PROCESSOR_LISTS = "/data/dungeons2/worldgen/processor_list";
    private static final String LANG = "/assets/dungeons2/lang/en_us.json";

    /** {@code { "entity": "<id>", "weight": <n> }} in any order. */
    private static final Pattern ENTITY_ENTRY = Pattern.compile(
            "\\{[^{}]*\"entity\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"[^{}]*\\}");
    private static final Pattern WEIGHT = Pattern.compile("\"weight\"\\s*:\\s*(\\d+)");
    private static final Pattern BLOCK_ENTRY = Pattern.compile(
            "\\{[^{}]*\"block\"\\s*:\\s*\"[a-z0-9_.-]+:[a-z0-9_/.-]+\"[^{}]*\\}");

    @Test
    void everyGrownEntityIsAMobThisModShips() throws IOException {
        String lang = read(LANG);
        Set<String> grown = grownEntities();
        assertFalse(grown.isEmpty(),
                "no shipped processor_list grows an entity any more -- if the fungi were removed on"
                        + " purpose, delete this test; otherwise the palette lost them");

        for (String id : grown) {
            assertTrue(id.startsWith("dungeons2:"),
                    "growth names '" + id + "', which belongs to another mod. That is allowed by the"
                            + " codec but this mod cannot guarantee it is installed, so it would"
                            + " silently grow nothing for most players");
            String key = "\"entity.dungeons2." + id.substring("dungeons2:".length()) + "\"";
            assertTrue(lang.contains(key),
                    "growth names the mob '" + id + "' but there is no lang key " + key + ", so it"
                            + " is not a mob this mod registers -- almost certainly a typo, and one"
                            + " that fails at GENERATION time with a warning nobody reads");
        }
    }

    /**
     * The fungi are monsters, so they have to be rarer than the ground cover they grow among.
     *
     * <p>Pins the intent, not the number: weights are free to move as long as an entity entry stays
     * strictly rarer than every block entry beside it. Unweighted they were 2 of 8 &mdash; a quarter
     * of everything that grows, which a later re-authoring of the palette could quietly restore.</p>
     */
    @Test
    void aGrownMobIsRarerThanEveryPlantBesideIt() throws IOException {
        int checked = 0;
        for (Path file : processorLists()) {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            for (String palette : palettes(json)) {
                Map<String, Integer> entities = weightsOf(ENTITY_ENTRY, palette);
                if (entities.isEmpty()) {
                    continue;
                }
                Map<String, Integer> blocks = weightsOf(BLOCK_ENTRY, palette);
                if (blocks.isEmpty()) {
                    continue;   // a palette of nothing but mobs has nothing to be rarer than
                }
                int heaviestMob = entities.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                int lightestPlant = blocks.values().stream().mapToInt(Integer::intValue).min().orElse(0);
                assertTrue(heaviestMob < lightestPlant,
                        file.getFileName() + ": a mob is weighted " + heaviestMob + " against a"
                                + " plant at " + lightestPlant + ", so the dungeon grows monsters as"
                                + " often as ground cover");
                checked++;
            }
        }
        assertTrue(checked > 0, "no palette mixing mobs and plants was found, so this asserted nothing");
    }

    /** Every {@code "entity"} id named anywhere in the shipped processor lists. */
    private static Set<String> grownEntities() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        for (Path file : processorLists()) {
            Matcher matcher = ENTITY_ENTRY.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
        }
        return found;
    }

    /** The text of each {@code "blocks": [ ... ]} array in one file. */
    private static java.util.List<String> palettes(String json) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Matcher matcher = Pattern.compile("\"blocks\"\\s*:\\s*\\[").matcher(json);
        while (matcher.find()) {
            int end = json.indexOf(']', matcher.end());
            if (end > 0) {
                out.add(json.substring(matcher.end(), end));
            }
        }
        return out;
    }

    /** Entry text &rarr; its weight, for entries matching {@code shape}. Bare ids are weight 1. */
    private static Map<String, Integer> weightsOf(Pattern shape, String palette) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        Matcher matcher = shape.matcher(palette);
        while (matcher.find()) {
            Matcher weight = WEIGHT.matcher(matcher.group());
            weights.put(matcher.group(), weight.find() ? Integer.parseInt(weight.group(1)) : 1);
        }
        // Bare ids elsewhere in the palette are blocks at weight 1.
        //
        // Scanned over the palette with every OBJECT entry removed first, which is not fussiness:
        // an object's own "block": "minecraft:brown_mushroom" is a quoted id followed by a comma
        // and matches the bare-id shape exactly, so scanning the raw text records every weighted
        // entry a second time at weight 1 -- and the minimum plant weight then reads as 1 no matter
        // what the file says. That silently made this test's comparison meaningless.
        if (shape == BLOCK_ENTRY) {
            String withoutObjects = palette.replaceAll("\\{[^{}]*\\}", "");
            Matcher bare = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"").matcher(withoutObjects);
            while (bare.find()) {
                weights.putIfAbsent(bare.group(), 1);
            }
        }
        return weights;
    }

    private static java.util.List<Path> processorLists() throws IOException {
        try {
            var url = ShippedGrowthEntitiesTest.class.getResource(PROCESSOR_LISTS);
            assertNotNull(url, "missing " + PROCESSOR_LISTS);
            try (Stream<Path> files = Files.walk(Paths.get(url.toURI()))) {
                return files.filter(f -> f.toString().endsWith(".json")).toList();
            }
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = ShippedGrowthEntitiesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
