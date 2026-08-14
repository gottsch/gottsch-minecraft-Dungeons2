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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The dungeon's {@code spawn_overrides} (backlog #42) actually name things that exist.
 *
 * <h2>Why this needs its own test</h2>
 * <p>{@link ShippedBlockIdsTest} classifies {@code "type"} as a non-block key, so its sweep walks
 * straight past a spawn entry's entity id. And the failure mode is the familiar silent one: an
 * unknown id does not stop the structure loading, it just means nothing ever spawns there &mdash;
 * indistinguishable from "the spawn weights are too low" or "I did not look long enough". Exactly
 * #13's problem in a different registry.</p>
 *
 * <h2>How a modded id is checked without a running game</h2>
 * <p>A bare {@code Bootstrap} has no Forge registries, so {@code dungeons2:rat} is not in
 * {@code BuiltInRegistries.ENTITY_TYPE} here and asking would fail everything. Vanilla ids are
 * checked against the registry; ours are checked against the <strong>lang file</strong>, which is
 * where a registered entity has to have a display name anyway. Indirect, and the same shape as
 * {@code ShippedBlockIdsTest} using blockstate files for {@code dungeonblocks:} ids.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
class StructureSpawnOverridesTest {

    private static final String STRUCTURE = "/data/dungeons2/worldgen/structure/dungeon.json";
    private static final String LANG = "/assets/dungeons2/lang/en_us.json";

    /** {@code StructureSpawnOverride.BoundingBoxType} -- the only two values that decode. */
    private static final Set<String> BOUNDING_BOX_TYPES = Set.of("piece", "full");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private record Spawn(String category, String type, int weight, int minCount, int maxCount) {
    }

    @Test
    void everySpawnNamesAnEntityThatExists() {
        List<String> bad = new ArrayList<>();
        for (Spawn spawn : spawns()) {
            ResourceLocation id = ResourceLocation.tryParse(spawn.type());
            if (id == null) {
                bad.add(spawn.type() + "  (not a valid resource location)");
            } else if (!exists(id)) {
                bad.add(spawn.type() + "  (no such entity -- nothing will ever spawn from this entry,"
                        + " and nothing will say so)");
            }
        }
        if (!bad.isEmpty()) {
            fail(bad.size() + " spawn entry(s) in dungeon.json name an entity that does not exist:\n  "
                    + String.join("\n  ", bad));
        }
    }

    @Test
    void everyCategoryAndBoundingBoxDecodes() {
        Set<String> categories = Arrays.stream(MobCategory.values())
                .map(MobCategory::getName).collect(Collectors.toSet());
        JsonObject overrides = overrides();
        for (String category : overrides.keySet()) {
            assertTrue(categories.contains(category),
                    "'" + category + "' is not a MobCategory -- the structure will fail to decode."
                            + " Valid: " + categories);
            String boundingBox = overrides.getAsJsonObject(category).get("bounding_box").getAsString();
            assertTrue(BOUNDING_BOX_TYPES.contains(boundingBox),
                    "bounding_box '" + boundingBox + "' for category '" + category
                            + "' is not one of " + BOUNDING_BOX_TYPES);
        }
    }

    @Test
    void everySpawnHasSaneCounts() {
        for (Spawn spawn : spawns()) {
            assertTrue(spawn.weight() > 0,
                    spawn.type() + " has weight " + spawn.weight() + " -- a non-positive weight is"
                            + " rejected by Weight's codec and would fail the whole structure");
            assertTrue(spawn.minCount() >= 1,
                    spawn.type() + " has minCount " + spawn.minCount() + "; must be positive");
            assertTrue(spawn.minCount() <= spawn.maxCount(),
                    spawn.type() + " has minCount " + spawn.minCount() + " > maxCount "
                            + spawn.maxCount());
        }
    }

    /**
     * The dungeon spawns its own mobs and nothing else.
     *
     * <p>Pinned deliberately, because it is the surprising half of how {@code spawn_overrides} works:
     * an override <strong>replaces</strong> the biome's list for that category inside the box rather
     * than adding to it. Listing rats under {@code monster} therefore means no zombies, skeletons or
     * creepers spawn naturally in a dungeon. That is the intent &mdash; the dungeon has its own
     * denizens &mdash; but it is a decision, not a detail, so a future edit that quietly reintroduces
     * vanilla monsters should have to change this assertion first.</p>
     */
    @Test
    void theDungeonsMonstersAreItsOwn() {
        List<Spawn> monsters = spawns().stream()
                .filter(spawn -> spawn.category().equals("monster")).toList();
        assertTrue(!monsters.isEmpty(), "expected the monster category to be overridden");
        for (Spawn spawn : monsters) {
            assertEquals("dungeons2", ResourceLocation.tryParse(spawn.type()).getNamespace(),
                    spawn.type() + " is not one of this mod's mobs. Adding a vanilla monster here is"
                            + " a design change: see this test's note.");
        }
    }

    /** Non-vacuity -- every check above passes trivially on an empty override. */
    @Test
    void theOverridesAreActuallyRead() {
        List<Spawn> spawns = spawns();
        assertTrue(spawns.size() >= 2,
                "expected at least the two rats, found " + spawns.size()
                        + " -- spawn_overrides was empty until #42");
        assertTrue(spawns.stream().anyMatch(spawn -> spawn.type().equals("dungeons2:rat")),
                "expected dungeons2:rat among the spawns");
    }

    // ---------- reading ----------

    private static boolean exists(ResourceLocation id) {
        if ("minecraft".equals(id.getNamespace())) {
            return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
        }
        if ("dungeons2".equals(id.getNamespace())) {
            return lang().has("entity.dungeons2." + id.getPath());
        }
        // Another mod's entity: nothing on this classpath can confirm it. Fail rather than pass
        // silently, so adding one is a deliberate act that comes here first.
        return false;
    }

    private static List<Spawn> spawns() {
        List<Spawn> spawns = new ArrayList<>();
        JsonObject overrides = overrides();
        for (String category : overrides.keySet()) {
            JsonArray entries = overrides.getAsJsonObject(category).getAsJsonArray("spawns");
            for (JsonElement entry : entries) {
                JsonObject spawn = entry.getAsJsonObject();
                spawns.add(new Spawn(category,
                        spawn.get("type").getAsString(),
                        spawn.get("weight").getAsInt(),
                        spawn.get("minCount").getAsInt(),
                        spawn.get("maxCount").getAsInt()));
            }
        }
        return spawns;
    }

    private static JsonObject overrides() {
        JsonObject structure = read(STRUCTURE);
        assertTrue(structure.has("spawn_overrides"), STRUCTURE + " has no spawn_overrides");
        return structure.getAsJsonObject("spawn_overrides");
    }

    private static JsonObject lang() {
        return read(LANG);
    }

    private static JsonObject read(String resource) {
        URL url = StructureSpawnOverridesTest.class.getResource(resource);
        if (url == null) {
            return fail("missing " + resource);
        }
        try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + resource, unreadable);
        }
    }
}
