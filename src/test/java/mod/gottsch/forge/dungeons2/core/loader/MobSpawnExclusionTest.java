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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mini-bosses are registered and never spawned &mdash; asserted, because an absence cannot be
 * read as a decision.
 *
 * <h2>Why this is a test and not a comment</h2>
 * <p>Mark, 2026-08-31: <em>"none of the small nor big bosses are in either spawners"</em>. The
 * Skeleton Champion, the Wight and the Bodak exist as entities, have spawn eggs, render and can be
 * summoned; what they must not do is turn up in a room the player walks into, because where they
 * belong is a placement decision that has not been designed yet.</p>
 *
 * <p>Left to the JSON alone, that decision is invisible. A mob set missing the Wight and a mob set
 * that <em>forgot</em> the Wight are the same file. And there are two independent lists to keep it
 * out of &mdash; the mob sets a spawner draws from, and the structure's ambient
 * {@code spawn_overrides} &mdash; so the likely failure is not "someone adds it deliberately" but
 * "someone adds the whole roster to one list and misses that three of them were held back".</p>
 *
 * <p>The list itself lives in {@code DungeonsEntities.MINI_BOSSES} rather than here, so the
 * exclusion is stated once, next to the registrations it qualifies.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
class MobSpawnExclusionTest {

    /**
     * Kept in step with {@code DungeonsEntities.MINI_BOSSES} by
     * {@link #theMiniBossListMatchesTheOneTheModDeclares} rather than by being the same object:
     * loading the registration class headlessly means constructing its {@code DeferredRegister}
     * entries, which is exactly the kind of thing that works until it does not.
     */
    private static final Set<String> MINI_BOSSES =
            Set.of("skeleton_champion", "wight", "bodak");

    private static final String MOB_SETS = "/data/dungeons2/mob_sets";
    private static final String STRUCTURE = "/data/dungeons2/worldgen/structure/dungeon.json";
    private static final String ENTITIES_SOURCE =
            "src/main/java/mod/gottsch/forge/dungeons2/core/entity/DungeonsEntities.java";

    /** No mob set may draw one. This is the spawner route. */
    @Test
    void noMobSetContainsAMiniBoss() {
        List<String> found = new ArrayList<>();
        for (Path file : mobSetFiles()) {
            JsonObject set = read(file).getAsJsonObject();
            for (JsonElement wrapped : set.getAsJsonArray("mobs")) {
                String id = wrapped.getAsJsonObject().get("id").getAsString();
                if (isMiniBoss(id)) {
                    found.add(file.getFileName() + " -> " + id);
                }
            }
        }
        assertTrue(found.isEmpty(), "a mob set draws a mini-boss, which must appear in neither"
                + " spawner route until where it belongs has been designed: " + found);
    }

    /** Nor may the structure's ambient population. This is the natural-spawn route. */
    @Test
    void noSpawnOverrideContainsAMiniBoss() {
        JsonObject overrides = readResource(STRUCTURE).getAsJsonObject()
                .getAsJsonObject("spawn_overrides");
        List<String> found = new ArrayList<>();
        for (String category : overrides.keySet()) {
            for (JsonElement wrapped : overrides.getAsJsonObject(category).getAsJsonArray("spawns")) {
                String id = wrapped.getAsJsonObject().get("type").getAsString();
                if (isMiniBoss(id)) {
                    found.add(category + " -> " + id);
                }
            }
        }
        assertTrue(found.isEmpty(),
                "the structure spawns a mini-boss ambiently, which must not happen: " + found);
    }

    /**
     * This test's list and the mod's own are the same list.
     *
     * <p>Without this the exclusion could be silently narrowed from the other end: someone adds a
     * fourth mini-boss to {@code MINI_BOSSES}, drops it into a mob set, and every assertion above
     * still passes because this file never heard of it.</p>
     */
    @Test
    void theMiniBossListMatchesTheOneTheModDeclares() {
        String source = sourceOf(ENTITIES_SOURCE);
        int start = source.indexOf("MINI_BOSSES =");
        assertTrue(start > 0, "DungeonsEntities no longer declares MINI_BOSSES -- if the mini-boss"
                + " exclusion was lifted deliberately, this test should go with it");
        String declaration = source.substring(start, source.indexOf(';', start));

        for (String id : MINI_BOSSES) {
            assertTrue(declaration.contains(id.toUpperCase()),
                    "this test excludes '" + id + "' and DungeonsEntities.MINI_BOSSES does not");
        }
        long declared = declaration.chars().filter(c -> c == ',').count() + 1;
        assertTrue(declared == MINI_BOSSES.size(), "DungeonsEntities.MINI_BOSSES names " + declared
                + " mobs and this test knows about " + MINI_BOSSES.size()
                + ". Add the new one here too, or the lists have diverged: " + declaration);
    }

    /**
     * Everything that is <em>not</em> a mini-boss should be reachable, or the roster is decorative.
     *
     * <p>The other half of the exclusion, and the one that catches the opposite mistake: twenty-odd
     * mobs registered, rendered, given spawn eggs, and named by nothing. Asserted loosely &mdash;
     * every non-mini-boss must appear in at least one of the two routes &mdash; because which route
     * a given mob belongs in is an authoring decision, and being in only one of them is legitimate.
     */
    @Test
    void everyOtherRosterMobIsReachableBySomeRoute() {
        List<String> named = new ArrayList<>();
        for (Path file : mobSetFiles()) {
            for (JsonElement wrapped : read(file).getAsJsonObject().getAsJsonArray("mobs")) {
                named.add(wrapped.getAsJsonObject().get("id").getAsString());
            }
        }
        JsonObject overrides = readResource(STRUCTURE).getAsJsonObject()
                .getAsJsonObject("spawn_overrides");
        for (String category : overrides.keySet()) {
            for (JsonElement wrapped : overrides.getAsJsonObject(category).getAsJsonArray("spawns")) {
                named.add(wrapped.getAsJsonObject().get("type").getAsString());
            }
        }

        JsonObject lang = readResource("/assets/dungeons2/lang/en_us.json").getAsJsonObject();
        List<String> unreachable = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (!key.startsWith("entity.dungeons2.")) {
                continue;
            }
            String id = key.substring("entity.dungeons2.".length());
            if (MINI_BOSSES.contains(id) || EXEMPT.contains(id)) {
                continue;
            }
            if (!named.contains("dungeons2:" + id)) {
                unreachable.add(id);
            }
        }
        assertTrue(unreachable.isEmpty(), unreachable.size() + " mob(s) are registered and named by"
                + " no mob set and no spawn override, so nothing in a generated dungeon can ever"
                + " produce one: " + unreachable);
    }

    /**
     * Entities that legitimately appear in neither list.
     *
     * <p>The fungi are placed as <em>growth</em> by the weathering pass rather than spawned, and the
     * five projectiles are thrown by mobs. Neither is a mob a spawner could draw.</p>
     */
    private static final Set<String> EXEMPT = Set.of(
            "shrieker", "violet_fungus",
            "bone_shard", "bloater_arm", "rock", "spike_growth_spell", "withering_gaze_spell");

    // -------- helpers --------

    private static boolean isMiniBoss(String id) {
        return id.startsWith("dungeons2:")
                && MINI_BOSSES.contains(id.substring("dungeons2:".length()));
    }

    private static List<Path> mobSetFiles() {
        try {
            Path dir = Paths.get(MobSpawnExclusionTest.class.getResource(MOB_SETS).toURI());
            try (Stream<Path> files = Files.walk(dir)) {
                return files.filter(f -> f.toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::toString)).toList();
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not list " + MOB_SETS, e);
        }
    }

    private static JsonElement read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + file, e);
        }
    }

    private static JsonElement readResource(String resource) {
        try (InputStream in = MobSpawnExclusionTest.class.getResourceAsStream(resource)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + resource, e);
        }
    }

    /**
     * Reads a main-source file as TEXT.
     *
     * <p>Deliberately not by loading the class: {@code DungeonsEntities} is a {@code DeferredRegister}
     * holder, and touching it headlessly initialises every {@code RegistryObject} in it. Reading the
     * declaration is enough for what this asserts and cannot drag the Forge registry machinery into
     * a unit test.</p>
     */
    private static String sourceOf(String relativePath) {
        try {
            return Files.readString(Paths.get(relativePath), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + relativePath
                    + " -- this test runs from the project root", e);
        }
    }
}
