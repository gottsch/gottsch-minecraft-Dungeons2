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
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sweeps what the shipped tombs (#104) hold. Every one of these faults is silent in game: a typo'd
 * table spills nothing, a typo'd guardian logs one WARN from DungeonBlocks and raises nothing, and
 * either tomb then looks exactly like one whose roll came up empty &mdash; which it is meant to do a
 * third of the time. The only place they can be told apart is here.
 *
 * <p>Tomb content lives in three places, and all three are swept: the motif's
 * {@code tomb_contents_by_floor_index}, a scheme's {@code tombs} slot (motif or band), and the
 * {@code dungeons2:sarcophagus} processor entries.</p>
 *
 * @author Mark Gottschling on Sep 26, 2026
 */
class ShippedTombsTest {

    private static final String MOTIF_CONFIGS = "/data/dungeons2/dungeons2/motif_config";
    private static final String PROCESSOR_LISTS = "/data/dungeons2/worldgen/processor_list";
    private static final String LANG = "/assets/dungeons2/lang/en_us.json";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** One place tomb content is authored, and where it was found. */
    private record Holder(String where, JsonObject contents) {}

    @Test
    void everyTombTableShipsAndIsAChestTable() {
        List<String> broken = new ArrayList<>();
        for (Holder holder : holders()) {
            if (!holder.contents().has("loot_tables")) {
                continue;
            }
            for (JsonElement entry : holder.contents().getAsJsonArray("loot_tables")) {
                String id = entry.getAsJsonObject().get("loot_table").getAsString();
                String problem = tableProblem(id);
                if (problem != null) {
                    broken.add(holder.where() + " -> " + id + ": " + problem);
                }
            }
        }
        if (!broken.isEmpty()) {
            fail("a tomb names a table it cannot spill -- the roll comes up 'loot' and nothing"
                    + " falls out:\n  " + String.join("\n  ", broken));
        }
    }

    /**
     * Every guardian is a real mob. {@code dungeons2:} ids are checked against the lang file, the
     * way {@code ShippedMobSetsTest} does it: the registry holds vanilla only headlessly. That none
     * is a mini-boss is {@code MobSpawnExclusionTest}'s, beside the other lists it guards.
     */
    @Test
    void everyGuardianIsARealMob() {
        List<String> broken = new ArrayList<>();
        JsonObject lang = parse(resource(LANG)).getAsJsonObject();
        for (String[] guardian : guardians()) {
            String id = guardian[1];
            boolean exists = id.startsWith("dungeons2:")
                    ? lang.has("entity.dungeons2." + id.substring("dungeons2:".length()))
                    : BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation(id));
            if (!exists) {
                broken.add(guardian[0] + " -> " + id);
            }
        }
        if (!broken.isEmpty()) {
            fail("tomb guardians that do not exist -- DungeonBlocks logs one WARN and raises"
                    + " nothing:\n  " + String.join("\n  ", broken));
        }
    }

    /** Every guardian any shipped tomb can raise, as {where, entity id}. */
    static List<String[]> guardians() {
        List<String[]> out = new ArrayList<>();
        for (Holder holder : holders()) {
            if (holder.contents().has("guardians")) {
                for (JsonElement entry : holder.contents().getAsJsonArray("guardians")) {
                    out.add(new String[] {holder.where(),
                            entry.getAsJsonObject().get("entity").getAsString()});
                }
            }
        }
        return out;
    }

    /**
     * A {@code tombs} slot that names no contents of its own defers to the motif's band. With no
     * band either, the slot resolves to nothing and places no tomb at all -- the feature shipped and
     * absent, the #61 shape.
     */
    @Test
    void everySlotThatDefersHasABandToDeferTo() {
        for (Path file : jsonFilesUnder(MOTIF_CONFIGS)) {
            Path motif = file.getParent();
            boolean motifHasBand = motifDeclaresTombBands(motif);
            for (Holder slot : slots(file)) {
                boolean namesContent = slot.contents().has("loot_tables")
                        || slot.contents().has("guardians");
                assertTrue(namesContent || motifHasBand, slot.where() + " names no loot_tables or"
                        + " guardians and its motif declares no tomb_contents_by_floor_index, so it"
                        + " can never place a tomb");
            }
        }
    }

    /** The authored route: one entry per shipped list, each building a real sarcophagus. */
    @Test
    void everyListsEntryBuildsASarcophagus() {
        int entries = 0;
        for (Path file : jsonFilesUnder(PROCESSOR_LISTS)) {
            for (JsonElement processor : parse(file).getAsJsonObject().getAsJsonArray("processors")) {
                JsonObject entry = processor.getAsJsonObject();
                if (!"dungeons2:sarcophagus".equals(entry.get("processor_type").getAsString())) {
                    continue;
                }
                entries++;
                String block = entry.has("sarcophagus_block")
                        ? entry.get("sarcophagus_block").getAsString()
                        : "dungeonblocks:stone_sarcophagus";
                // A DungeonBlocks coffin extends its sarcophagus: same two parts, block entity, seal.
                assertTrue(block.endsWith("_sarcophagus") || block.endsWith("_coffin"),
                        file.getFileName() + " builds " + block
                        + ", which is not a two-block tomb");
                assertTrue(entry.has("loot_tables") || entry.has("guardians"),
                        file.getFileName() + ": an entry naming nothing leaves every marker that"
                                + " names nothing standing");
            }
        }
        assertEquals(jsonFilesUnder(PROCESSOR_LISTS).size(), entries,
                "one dungeons2:sarcophagus entry per shipped list");
    }

    /** The checks above pass vacuously if the sweep reads nothing. */
    @Test
    void theSweepFindsTheShippedContent() {
        List<Holder> holders = holders();
        assertTrue(holders.stream().anyMatch(h -> h.where().contains("band at floor 0")),
                "no tomb band was found");
        assertTrue(holders.stream().anyMatch(h -> h.where().contains("scheme 'crypt'")),
                "the crypt's tombs slot was not found");
        assertFalse(holders.stream().noneMatch(h -> h.where().contains("processor")),
                "no processor entry was found");
    }

    // ---------- where tomb content is authored ----------

    private static List<Holder> holders() {
        List<Holder> holders = new ArrayList<>();
        for (Path file : jsonFilesUnder(MOTIF_CONFIGS)) {
            JsonObject root = parse(file).getAsJsonObject();
            if (root.has("tomb_contents_by_floor_index")) {
                for (JsonElement band : root.getAsJsonArray("tomb_contents_by_floor_index")) {
                    JsonObject object = band.getAsJsonObject();
                    holders.add(new Holder(file.getFileName() + ": band at floor "
                            + (object.has("min_floor_index") ? object.get("min_floor_index").getAsInt() : 0),
                            object));
                }
            }
            holders.addAll(slots(file));
        }
        for (Path file : jsonFilesUnder(PROCESSOR_LISTS)) {
            for (JsonElement processor : parse(file).getAsJsonObject().getAsJsonArray("processors")) {
                JsonObject entry = processor.getAsJsonObject();
                if ("dungeons2:sarcophagus".equals(entry.get("processor_type").getAsString())) {
                    holders.add(new Holder(file.getFileName() + ": processor", entry));
                }
            }
        }
        return holders;
    }

    /** Every {@code tombs} slot in a motif file, the motif's schemes and every band's. */
    private static List<Holder> slots(Path file) {
        List<Holder> slots = new ArrayList<>();
        JsonObject root = parse(file).getAsJsonObject();
        collectSlots(root, file.getFileName().toString(), slots);
        if (root.has("strata_by_floor_index")) {
            for (JsonElement band : root.getAsJsonArray("strata_by_floor_index")) {
                collectSlots(band.getAsJsonObject(), file.getFileName() + " stratum", slots);
            }
        }
        return slots;
    }

    private static void collectSlots(JsonObject holder, String where, List<Holder> out) {
        if (!holder.has("schemes")) {
            return;
        }
        for (JsonElement wrapped : holder.getAsJsonArray("schemes")) {
            JsonObject scheme = wrapped.getAsJsonObject();
            if (!scheme.has("tombs")) {
                continue;
            }
            JsonElement slot = scheme.get("tombs");
            String name = where + ": scheme '" + scheme.get("name").getAsString() + "'";
            if (slot.isJsonObject()) {
                out.add(new Holder(name, slot.getAsJsonObject()));
            } else {
                for (JsonElement option : slot.getAsJsonArray()) {
                    if (!option.getAsJsonObject().has("none")) {
                        out.add(new Holder(name, option.getAsJsonObject()));
                    }
                }
            }
        }
    }

    private static boolean motifDeclaresTombBands(Path motifDir) {
        try (Stream<Path> files = Files.list(motifDir)) {
            return files.filter(p -> p.toString().endsWith(".json"))
                    .anyMatch(p -> parse(p).getAsJsonObject().has("tomb_contents_by_floor_index"));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** Null when {@code id} names a shipped chest-type table; otherwise what is wrong with it. */
    private static String tableProblem(String id) {
        ResourceLocation location = new ResourceLocation(id);
        URL url = ShippedTombsTest.class.getResource("/data/" + location.getNamespace()
                + "/loot_tables/" + location.getPath() + ".json");
        if (url == null) {
            return location.getNamespace().equals("dungeons2") ? "no such table ships" : null;
        }
        try {
            String type = parse(Paths.get(url.toURI())).getAsJsonObject().get("type").getAsString();
            // DungeonBlocks rolls it with the CHEST parameter set; an entity table would fail.
            return "minecraft:chest".equals(type) ? null : "type is " + type + ", not minecraft:chest";
        } catch (URISyntaxException bad) {
            return bad.toString();
        }
    }

    // ---------- reading ----------

    private static Path resource(String name) {
        URL url = ShippedTombsTest.class.getResource(name);
        if (url == null) {
            return fail("no shipped resource at " + name);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException bad) {
            return fail(bad.toString());
        }
    }

    private static JsonElement parse(Path file) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            // Lenient, as everywhere in the suite: the shipped files carry // comments.
            return JsonParser.parseReader(reader);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static List<Path> jsonFilesUnder(String resourceDir) {
        try (Stream<Path> paths = Files.walk(resource(resourceDir))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            return fail("could not walk " + resourceDir + ": " + unreadable);
        }
    }
}
