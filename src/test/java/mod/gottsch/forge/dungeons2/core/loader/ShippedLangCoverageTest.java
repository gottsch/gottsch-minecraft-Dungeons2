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
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every shipped item has a display name and a model.
 *
 * <p>Added with the mobs (backlog #40 / #41), which brought this mod its first items &mdash; two
 * spawn eggs &mdash; and therefore its first {@code lang} file. Both halves fail the same way: not
 * with an error, but visibly and only in game. A missing lang key shows the raw
 * {@code item.dungeons2.rat_egg} in the tooltip; a missing model shows the black-and-magenta cube.
 * Neither is caught by anything else here.</p>
 *
 * <p><strong>The entity side of this cannot be swept the same way.</strong> Entity ids live in
 * {@code DungeonsEntities}' {@code RegistryObject} fields, and loading that class initialises them
 * against Forge registries that do not exist in a bare {@code Bootstrap}. So
 * {@code entity.dungeons2.*} keys are checked for existence, not for coverage &mdash; if a third mob
 * is added and its key is forgotten, this test will not say so.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
class ShippedLangCoverageTest {

    private static final String LANG = "/assets/dungeons2/lang/en_us.json";
    private static final String ITEM_MODELS = "/assets/dungeons2/models/item";

    @Test
    void everyItemModelHasADisplayName() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Path model : itemModels()) {
            String name = model.getFileName().toString().replace(".json", "");
            String key = "item.dungeons2." + name;
            if (!lang.has(key)) {
                missing.add(key + "  (model " + model.getFileName() + " ships with no display name)");
            }
        }
        if (!missing.isEmpty()) {
            fail(missing.size() + " item(s) would show a raw translation key in game:\n  "
                    + String.join("\n  ", missing));
        }
    }

    @Test
    void everyDisplayNameIsForSomethingThatShips() {
        JsonObject lang = lang();
        List<String> orphans = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (!key.startsWith("item.dungeons2.")) {
                continue;
            }
            String name = key.substring("item.dungeons2.".length());
            if (ShippedLangCoverageTest.class.getResource(ITEM_MODELS + "/" + name + ".json") == null) {
                orphans.add(key + "  (no item model of that name)");
            }
        }
        if (!orphans.isEmpty()) {
            fail(orphans.size() + " lang key(s) name an item that does not ship -- a rename left the"
                    + " old key behind, or the model is missing and the item will render as the"
                    + " missing-texture cube:\n  " + String.join("\n  ", orphans));
        }
    }

    /** Non-vacuity, and the entity half this test cannot sweep properly. */
    @Test
    void theLangFileIsBeingRead() {
        JsonObject lang = lang();
        assertTrue(lang.keySet().stream().anyMatch(k -> k.startsWith("item.dungeons2.")),
                "expected at least one item name in " + LANG);
        assertTrue(lang.keySet().stream().anyMatch(k -> k.startsWith("entity.dungeons2.")),
                "expected at least one entity name in " + LANG + " -- see the class note on why"
                        + " entity keys are checked for existence rather than coverage");
        assertTrue(!itemModels().isEmpty(), "expected item models under " + ITEM_MODELS);
    }

    // ---------- reading ----------

    private static JsonObject lang() {
        URL url = ShippedLangCoverageTest.class.getResource(LANG);
        if (url == null) {
            return fail("no lang file at " + LANG);
        }
        try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + LANG, unreadable);
        }
    }

    private static List<Path> itemModels() {
        URL url = ShippedLangCoverageTest.class.getResource(ITEM_MODELS);
        if (url == null) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + ITEM_MODELS + ": " + unreadable);
        }
    }
}
