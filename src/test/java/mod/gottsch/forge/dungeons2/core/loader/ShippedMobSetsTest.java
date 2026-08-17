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
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Set;
import java.util.stream.Stream;

/**
 * Backlog #10's content sweep: the shipped {@code mob_sets} name entities that exist, and every
 * {@code mob_set} a processor list references actually ships.
 *
 * <h2>Why this test rather than validation in the processor</h2>
 * <p>{@code SpawnerMarkerProcessor} deliberately does not check that its set exists.
 * {@code MobSetDataRegistry} is filled from datapacks at reload time while a processor runs during
 * worldgen, so "not loaded yet" and "does not exist" are indistinguishable there &mdash; and a
 * dungeon is not worth aborting over a spawner. That makes the typo a <em>build</em> problem, which
 * is what this is. Same split as {@code ShippedBlockIdsTest}: GottschCore warns at runtime, the
 * consuming mod fails its own build over its own content.</p>
 *
 * <p>The mob ids are checked against {@code BuiltInRegistries.ENTITY_TYPE}, which under a bare
 * {@code Bootstrap} holds vanilla only. Dungeons2's own entities ({@code dungeons2:rat},
 * {@code dungeons2:giant_rat}) are not registered without a running game, so they are verified
 * against {@code DungeonsEntities}' declared ids instead &mdash; the same "an indirect check is the
 * only one available headlessly" compromise the blockstate-file check makes for dungeonblocks.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
class ShippedMobSetsTest {

    private static final String MOB_SETS = "/data/dungeons2/mob_sets";
    private static final String PROCESSOR_LISTS = "/data/dungeons2/worldgen/processor_list";

    /**
     * Dungeons2's own entity ids. Not resolvable through the registry headlessly (Forge does not
     * run), so they are listed; {@code StructureSpawnOverridesTest} already pins the same pair from
     * the spawn-overrides side, and a third place naming them would be one too many.
     */
    private static final Set<String> OWN_ENTITIES =
            Set.of("dungeons2:rat", "dungeons2:giant_rat");

    /** {@code minecraft:empty} is a real authoring idiom: a weighted "spawn nothing" slot. */
    private static final String EMPTY = "minecraft:empty";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyShippedMobSetNamesEntitiesThatExist() {
        List<String> broken = new ArrayList<>();
        for (Path file : jsonFilesUnder(MOB_SETS)) {
            JsonObject set = parse(file).getAsJsonObject();
            for (JsonElement wrapped : set.getAsJsonArray("mobs")) {
                String id = wrapped.getAsJsonObject().get("id").getAsString();
                if (EMPTY.equals(id) || OWN_ENTITIES.contains(id)) {
                    continue;
                }
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation(id))) {
                    broken.add(file.getFileName() + " -> " + id);
                }
            }
        }
        if (!broken.isEmpty()) {
            org.junit.jupiter.api.Assertions.fail(broken.size() + " mob set entry/entries name an"
                    + " entity that does not exist. The spawner would roll it and spawn nothing,"
                    + " logging nothing:\n  " + String.join("\n  ", broken));
        }
    }

    /** A set's own {@code id} must match where it sits, or nothing can reference it by name. */
    @Test
    void everyShippedMobSetIdMatchesItsFileName() {
        for (Path file : jsonFilesUnder(MOB_SETS)) {
            String name = file.getFileName().toString().replace(".json", "");
            String declared = parse(file).getAsJsonObject().get("id").getAsString();
            org.junit.jupiter.api.Assertions.assertEquals("dungeons2:" + name, declared,
                    file.getFileName() + " declares id '" + declared + "'. MobSetDataRegistry keys"
                            + " on the declared id, not the path, so a mismatch makes the set"
                            + " unreachable by the name anyone would guess.");
        }
    }

    @Test
    void everyMobSetAProcessorListReferencesShips() {
        Set<String> shipped = new LinkedHashSet<>();
        for (Path file : jsonFilesUnder(MOB_SETS)) {
            shipped.add(parse(file).getAsJsonObject().get("id").getAsString());
        }

        List<String> dangling = new ArrayList<>();
        for (Path file : jsonFilesUnder(PROCESSOR_LISTS)) {
            for (JsonElement wrapped : parse(file).getAsJsonObject().getAsJsonArray("processors")) {
                JsonObject processor = wrapped.getAsJsonObject();
                if (!processor.has("mob_set")) {
                    continue;
                }
                String referenced = processor.get("mob_set").getAsString();
                if (!shipped.contains(referenced)) {
                    dangling.add(file.getFileName() + " -> " + referenced);
                }
            }
        }
        if (!dangling.isEmpty()) {
            org.junit.jupiter.api.Assertions.fail(dangling.size() + " processor(s) reference a mob"
                    + " set that does not ship. Every d2:spawner marker they convert would produce"
                    + " a spawner that spawns nothing:\n  " + String.join("\n  ", dangling)
                    + "\nShipped: " + shipped);
        }
    }

    /** The checks above pass vacuously if nothing is being read. */
    @Test
    void theSweepFindsTheShippedContent() {
        org.junit.jupiter.api.Assertions.assertFalse(jsonFilesUnder(MOB_SETS).isEmpty(),
                "no shipped mob_sets were found at " + MOB_SETS);
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
        URL url = ShippedMobSetsTest.class.getResource(resourceDir);
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
