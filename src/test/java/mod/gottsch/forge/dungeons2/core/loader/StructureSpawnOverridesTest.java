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
                    spawn.type() + " has min_count " + spawn.minCount() + "; must be positive");
            assertTrue(spawn.minCount() <= spawn.maxCount(),
                    spawn.type() + " has min_count " + spawn.minCount() + " > max_count "
                            + spawn.maxCount());
        }
    }

    /**
     * The vanilla monsters allowed alongside this mod's own.
     *
     * <p>An <strong>allowlist</strong> rather than "anything from {@code minecraft:}", because the
     * override replaces the biome's list wholesale: whatever is named here is the complete vanilla
     * bestiary of a dungeon, so the interesting failure is not a typo but a plausible-looking
     * addition nobody decided on. A creeper in a corridor is a different game.</p>
     */
    private static final Set<String> ALLOWED_VANILLA = Set.of(
            "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
            "minecraft:cave_spider");

    /**
     * The dungeon's monsters are its own <em>plus a named handful of vanilla ones</em>.
     *
     * <p>Pinned because of the surprising half of how {@code spawn_overrides} works: an override
     * <strong>replaces</strong> the biome's list for that category inside the box rather than adding
     * to it. So this file is not "extra mobs" &mdash; it is the entire natural population of a
     * dungeon, and anything absent from it does not spawn there at all.</p>
     *
     * <h2>This assertion was inverted on 2026-08-31, deliberately</h2>
     * <p>It used to require that <em>every</em> spawn be a {@code dungeons2:} mob, and said in as
     * many words that reintroducing vanilla monsters was a design change which should have to edit
     * this test first. Mark made that change, having noticed the dungeon had no vanilla mobs in it
     * at all. The old test did its job &mdash; the decision could not be made by accident &mdash; so
     * it is rewritten to guard the new shape rather than deleted.</p>
     *
     * <p>What it guards now is the balance the old rule protected absolutely: vanilla monsters are
     * present but must not take the dungeon over, and the ones present must be on
     * {@link #ALLOWED_VANILLA}.</p>
     */
    @Test
    void theDungeonsMonstersAreMostlyItsOwn() {
        List<Spawn> monsters = spawns().stream()
                .filter(spawn -> spawn.category().equals("monster")).toList();
        assertTrue(!monsters.isEmpty(), "expected the monster category to be overridden");

        int own = 0;
        int vanilla = 0;
        for (Spawn spawn : monsters) {
            String namespace = ResourceLocation.tryParse(spawn.type()).getNamespace();
            if ("dungeons2".equals(namespace)) {
                own += spawn.weight();
                continue;
            }
            assertTrue(ALLOWED_VANILLA.contains(spawn.type()), spawn.type()
                    + " is not one of this mod's mobs and is not on the vanilla allowlist. The"
                    + " override REPLACES the biome's list, so adding a monster here is a design"
                    + " change: see this test's note.");
            vanilla += spawn.weight();
        }

        assertTrue(own > 0, "no dungeons2 monster is left in the override");
        assertTrue(vanilla > 0, "the vanilla allowlist exists but nothing uses it -- either the"
                + " vanilla monsters were removed again, in which case delete the allowlist, or"
                + " this test has stopped reading the file");
        assertTrue(own > vanilla, "vanilla monsters hold " + vanilla + " of " + (own + vanilla)
                + " weight against this mod's " + own + ". The dungeon should read as having its"
                + " own denizens; raise the dungeons2 weights or lower the vanilla ones");
    }

    /**
     * <strong>The slime is deliberately NOT here</strong>, and the reason is a real difference
     * between the two ways a mob reaches a dungeon.
     *
     * <p>It was added to this file on 2026-08-31 and taken out again the same day, because a natural
     * spawn cannot escape the entity's own {@code SpawnPlacements} predicate: vanilla's slime rule
     * wants a slime chunk below Y 40, so no weight in this file could have made slimes appear
     * reliably. Mark moved it to the mob sets instead.</p>
     *
     * <p><strong>That route genuinely does escape it</strong>, which is what makes the move work
     * rather than merely relocate the problem. GottschCore's {@code SpawnUtil.spawnMob} checks
     * {@code NaturalSpawner.isSpawnPositionOk} &mdash; is this block a legal place to stand for the
     * entity's placement TYPE &mdash; and never calls {@code SpawnPlacements.checkSpawnRules}, which
     * is where the slime-chunk test lives. A spawner-placed slime therefore appears wherever the
     * spawner is, exactly as a vanilla mob spawner ignores the rules its mob would face in the
     * wild.</p>
     *
     * <p>Left as a note rather than an assertion: "this id is absent" is what the allowlist above
     * already enforces, and a test asserting the absence of one specific mob would fail the day
     * somebody legitimately reconsiders.</p>
     */

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
                        spawn.get("min_count").getAsInt(),
                        spawn.get("max_count").getAsInt()));
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
