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
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigFragment;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The mood biomes (#129) as shipped: each decodes as a vanilla biome, spawns nothing, has a name,
 * and every biome a motif names is one that ships.
 *
 * <p>A biome that fails to decode is a datapack load error that only shows in game, and a motif
 * naming a missing biome paints nothing and warns once in the log &mdash; neither reaches the build
 * without this.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class ShippedMoodBiomesTest {

    private static final String BIOME_ROOT = "/data/dungeons2/worldgen/biome";
    private static final String CONFIG_ROOT = "/data/dungeons2/dungeons2/motif_config";
    private static final String LANG = "/assets/dungeons2/lang/en_us.json";
    private static final Pattern BIOME_FIELD = Pattern.compile("\"biome\"\\s*:\\s*\"([^\"]+)\"");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyShippedBiomeDecodesAndSpawnsNothing() {
        // Sounds are registry references, so the ops need the sound registry to resolve them; a
        // misspelled sound id fails here. Features and carvers are empty and need no lookup.
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                HolderLookup.Provider.create(Stream.of(BuiltInRegistries.SOUND_EVENT.asLookup())));
        List<Path> biomes = files(BIOME_ROOT);
        assertTrue(biomes.size() >= 3, "expected the three depth-band biomes, found " + biomes.size());
        List<String> failures = new ArrayList<>();
        for (Path file : biomes) {
            DataResult<Biome> result = Biome.DIRECT_CODEC.parse(ops, read(file));
            if (result.error().isPresent()) {
                failures.add(file.getFileName() + ": " + result.error().orElseThrow().message());
                continue;
            }
            MobSpawnSettings spawns = result.result().orElseThrow().getMobSettings();
            for (MobCategory category : MobCategory.values()) {
                if (!spawns.getMobs(category).isEmpty()) {
                    failures.add(file.getFileName() + ": spawns " + category.getName()
                            + " -- spawning belongs to the mob sets and spawn_overrides, not the mood");
                }
            }
        }
        if (!failures.isEmpty()) {
            fail(String.join("\n  ", failures));
        }
    }

    @Test
    void everyBiomeAMotifNamesShips() {
        Set<String> shipped = shippedBiomeIds();
        List<String> missing = new ArrayList<>();
        int named = 0;
        for (Path file : files(CONFIG_ROOT)) {
            Matcher m = BIOME_FIELD.matcher(text(file));
            while (m.find()) {
                named++;
                if (!shipped.contains(m.group(1))) {
                    missing.add(file.getFileName() + " names " + m.group(1));
                }
            }
        }
        assertTrue(named >= 3, "expected motif configs to name biomes, found " + named);
        if (!missing.isEmpty()) {
            fail("motif biome(s) that do not ship -- the pieces would keep the ground's biome:\n  "
                    + String.join("\n  ", missing));
        }
    }

    /**
     * Every shipped mood biome sits in exactly one dark-fog tier (#129 step 3), and the tiers name
     * nothing that does not ship. A biome in none would keep vanilla's fog distance in the dungeon.
     */
    @Test
    void everyShippedBiomeHasOneDarkFogTier() {
        List<String> tiered = new ArrayList<>();
        for (String tier : List.of("shallow", "middle", "deep", "haze")) {
            Path file = Paths.get(resource("/data/dungeons2/tags/worldgen/biome/dark_fog/" + tier + ".json"));
            read(file).getAsJsonObject().getAsJsonArray("values").forEach(v -> tiered.add(v.getAsString()));
        }
        assertEquals(shippedBiomeIds(), new TreeSet<>(tiered), "the dark_fog tiers vs the shipped biomes");
        assertEquals(tiered.size(), new TreeSet<>(tiered).size(), "a biome in two tiers: " + tiered);
    }

    /** Every prefab a motif maps to a biome ships as a template, and names a biome that ships. */
    @Test
    void everyTemplateBiomeNamesAShippedTemplateAndBiome() {
        Set<String> shipped = shippedBiomeIds();
        List<String> bad = new ArrayList<>();
        int mapped = 0;
        for (Path file : files(CONFIG_ROOT)) {
            JsonElement json = read(file);
            if (!json.isJsonObject() || !json.getAsJsonObject().has("template_biomes")) {
                continue;
            }
            for (var entry : json.getAsJsonObject().getAsJsonObject("template_biomes").entrySet()) {
                mapped++;
                ResourceLocation template = new ResourceLocation(entry.getKey());
                String nbt = "/data/" + template.getNamespace() + "/structures/" + template.getPath() + ".nbt";
                if (ShippedMoodBiomesTest.class.getResource(nbt) == null) {
                    bad.add(file.getFileName() + ": no template " + entry.getKey());
                }
                if (!shipped.contains(entry.getValue().getAsString())) {
                    bad.add(file.getFileName() + ": " + entry.getKey() + " names unshipped biome "
                            + entry.getValue().getAsString());
                }
            }
        }
        assertTrue(mapped >= 1, "expected classic to map its sewer prefabs");
        if (!bad.isEmpty()) {
            fail("a mapped prefab would keep its floor's biome:\n  " + String.join("\n  ", bad));
        }
    }

    /** Classic's bands as authored: mud on top, stone in the middle, deepslate at the bottom. */
    @Test
    void classicPaintsEachBandItsOwnBiome() {
        List<MotifConfigFragment> fragments = new ArrayList<>();
        for (Path file : files(CONFIG_ROOT + "/classic")) {
            fragments.add(MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, read(file))
                    .getOrThrow(false, message -> fail(file.getFileName() + ": " + message)));
        }
        MotifConfig classic = MotifConfigFragment.resolve(fragments);
        assertEquals(Optional.of(new ResourceLocation("dungeons2:mud_crypts")), classic.biomeFor(0));
        for (int floor = 1; floor <= 3; floor++) {
            assertEquals(Optional.of(new ResourceLocation("dungeons2:stone_halls")), classic.biomeFor(floor),
                    "floor " + floor);
        }
        assertEquals(Optional.of(new ResourceLocation("dungeons2:deep_vaults")), classic.biomeFor(4));
    }

    @Test
    void everyShippedBiomeHasADisplayNameAndNoOrphans() {
        JsonObject lang = read(Paths.get(resource(LANG))).getAsJsonObject();
        Set<String> expected = shippedBiomeIds().stream()
                .map(id -> "biome." + id.replace(':', '.'))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actual = lang.keySet().stream()
                .filter(key -> key.startsWith("biome.dungeons2."))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(expected, actual, "biome lang keys must match the shipped biome files one for one");
    }

    // ---------- reading ----------

    private static Set<String> shippedBiomeIds() {
        return files(BIOME_ROOT).stream()
                .map(path -> "dungeons2:" + path.getFileName().toString().replace(".json", ""))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static java.net.URI resource(String root) {
        URL url = ShippedMoodBiomesTest.class.getResource(root);
        if (url == null) {
            return fail("nothing shipped at " + root);
        }
        try {
            return url.toURI();
        } catch (URISyntaxException bad) {
            return fail(bad.toString());
        }
    }

    private static List<Path> files(String root) {
        try (Stream<Path> paths = Files.walk(Paths.get(resource(root)))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            return fail("could not walk " + root + ": " + unreadable);
        }
    }

    private static String text(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static JsonElement read(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            // Lenient: the motif configs carry `//` notes.
            JsonReader json = new JsonReader(reader);
            json.setLenient(true);
            return JsonParser.parseReader(json);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }
}
