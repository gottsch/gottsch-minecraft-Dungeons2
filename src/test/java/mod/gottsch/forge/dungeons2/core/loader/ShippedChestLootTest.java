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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sweeps the shipped chest loot tables.
 *
 * <p>Loot tables are the one kind of content here whose faults are invisible <em>in play</em> rather
 * than at load: a chest that pays the wrong tier still opens, still has items in it, and looks
 * exactly like one that is working. Nothing below is a schema check &mdash; vanilla already rejects
 * a malformed table at datapack load. These are the rules that live in prose today and would
 * otherwise be enforced by nobody.
 *
 * @author Mark Gottschling on Sep 3, 2026
 */
class ShippedChestLootTest {

    private static final String CHESTS = "/data/dungeons2/loot_tables/chests";
    private static final String BOSS_PREFIX = "classic_boss_";

    /**
     * The rule from {@code mining_config/default.json}: the Mining Chest must not out-do the boss
     * chest, and <em>"the strongest guard is that it is a different KIND of reward -- raw material,
     * no enchanted book, no diamond gear, no golden apple -- so classic_hoard wins on kind before
     * any number is compared."</em>
     *
     * <p>That guard only holds while the boss tables actually pay a kind the Mining Chest cannot.
     * Quantity is the weaker half and is deliberately not asserted: the Mining Chest scales with
     * excavated volume without bound, so a large enough dungeon will always out-<em>count</em> a
     * boss chest. The answer to that is the kind gap, not a bigger number.</p>
     */
    @Test
    void everyBossTablePaysAKindTheMiningChestCannot() {
        List<String> flat = new ArrayList<>();
        for (Path file : bossTables()) {
            String json = read(file);
            boolean trophy = json.contains("minecraft:enchant_randomly")
                    || json.contains("minecraft:enchant_with_levels")
                    || json.contains("minecraft:enchanted_golden_apple")
                    || json.contains("minecraft:netherite_upgrade_smithing_template");
            if (!trophy) {
                flat.add(file.getFileName().toString());
            }
        }
        assertTrue(flat.isEmpty(), "a boss loot table pays only raw material, which is exactly what"
                + " the Mining Chest pays -- the kind gap that stops the Mining Chest out-doing the"
                + " boss chest is gone: " + flat);
    }

    /**
     * A boss table must guarantee its trophy, not merely make one possible.
     *
     * <p>A boss chest is opened once per dungeon, at the end of the only fight the dungeon builds
     * toward. A merely weighted chance of gear would mean runs where the boss pays a stack of lapis,
     * which reads as the feature being broken rather than as an unlucky roll &mdash; so the first
     * pool rolls a fixed number of times and every entry in it is a trophy.</p>
     */
    @Test
    void everyBossTableGuaranteesItsFirstPool() {
        List<String> chancy = new ArrayList<>();
        for (Path file : bossTables()) {
            JsonElement rolls = JsonParser.parseString(read(file)).getAsJsonObject()
                    .getAsJsonArray("pools").get(0).getAsJsonObject().get("rolls");
            // A number is a fixed count; an object is a min/max range, which could roll low.
            if (!rolls.isJsonPrimitive() || rolls.getAsInt() < 1) {
                chancy.add(file.getFileName() + " (rolls " + rolls + ")");
            }
        }
        assertTrue(chancy.isEmpty(), "a boss table's trophy pool is not guaranteed: " + chancy);
    }

    /**
     * The three sizes must stay strictly ordered by guaranteed gear.
     *
     * <p>The first version of these tables was tiered on quantity, which made the small one barely
     * better than {@code classic_hoard} &mdash; a table any deep room can already roll, and which
     * guarantees nothing at all. The fix was structural: small guarantees two pieces of gear, medium
     * three, large four. Asserted because "the boss chest feels weak" is the kind of regression that
     * is only ever noticed by playing, and only after someone has played a lot.</p>
     */
    @Test
    void theSizesAreStrictlyOrderedByGuaranteedGear() {
        int small = guaranteedTrophies("classic_boss_small");
        int medium = guaranteedTrophies("classic_boss_medium");
        int large = guaranteedTrophies("classic_boss_large");
        assertTrue(small >= 2, "the small boss chest guarantees " + small + " pieces of gear;"
                + " classic_hoard already carries diamond gear at weight 4, so fewer than two"
                + " guaranteed makes a boss chest a lucky ordinary chest");
        assertTrue(medium > small && large > medium,
                "boss chest tiers must strictly increase in GUARANTEED gear, not merely in stack"
                        + " sizes -- small=" + small + " medium=" + medium + " large=" + large);
    }

    /** The rolls of the pool whose entries are all enchanted, i.e. the trophy pool. */
    private static int guaranteedTrophies(String table) {
        for (Path file : bossTables()) {
            if (!file.getFileName().toString().equals(table + ".json")) {
                continue;
            }
            JsonObject json = JsonParser.parseString(read(file)).getAsJsonObject();
            for (JsonElement pool : json.getAsJsonArray("pools")) {
                JsonObject p = pool.getAsJsonObject();
                JsonElement rolls = p.get("rolls");
                if (!rolls.isJsonPrimitive()) {
                    continue;
                }
                boolean allEnchanted = true;
                for (JsonElement entry : p.getAsJsonArray("entries")) {
                    if (!entry.toString().contains("minecraft:enchant_with_levels")) {
                        allEnchanted = false;
                        break;
                    }
                }
                if (allEnchanted) {
                    return rolls.getAsInt();
                }
            }
            return 0;
        }
        throw new IllegalStateException("no such boss table: " + table);
    }

    /**
     * {@code random_sequence} must name the table's own id.
     *
     * <p>A copy-paste hazard whose symptom is confusion rather than failure: two tables sharing a
     * sequence draw from one stream, so the second chest opened in a world is correlated with the
     * first. Everything still works, the loot is just quietly less random than it looks, and no
     * amount of playing tells you so.</p>
     */
    @Test
    void everyRandomSequenceMatchesItsOwnPath() {
        List<String> wrong = new ArrayList<>();
        for (Path file : chestTables()) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonElement seq = JsonParser.parseString(read(file)).getAsJsonObject()
                    .get("random_sequence");
            String expected = "dungeons2:chests/" + name;
            if (seq == null || !expected.equals(seq.getAsString())) {
                wrong.add(name + " -> " + seq);
            }
        }
        assertTrue(wrong.isEmpty(), "a chest table's random_sequence does not name itself, so it"
                + " shares a draw stream with whatever it was copied from: " + wrong);
    }

    /** Every entry must be a well-formed item reference; a typo is a datapack load failure in game. */
    @Test
    void everyEntryNamesAnItem() {
        Set<String> allowed = Set.of("minecraft:item", "minecraft:loot_table", "minecraft:empty",
                "minecraft:tag", "minecraft:alternatives", "minecraft:group", "minecraft:sequence");
        List<String> odd = new ArrayList<>();
        for (Path file : chestTables()) {
            JsonObject table = JsonParser.parseString(read(file)).getAsJsonObject();
            for (JsonElement pool : table.getAsJsonArray("pools")) {
                JsonArray entries = pool.getAsJsonObject().getAsJsonArray("entries");
                for (JsonElement entry : entries) {
                    JsonObject e = entry.getAsJsonObject();
                    String type = e.get("type").getAsString();
                    if (!allowed.contains(type)) {
                        odd.add(file.getFileName() + ": unknown entry type " + type);
                    } else if ("minecraft:item".equals(type)
                            && (!e.has("name") || !e.get("name").getAsString().contains(":"))) {
                        odd.add(file.getFileName() + ": item entry with no namespaced name");
                    }
                }
            }
        }
        assertTrue(odd.isEmpty(), "a chest loot entry is malformed: " + odd);
    }

    /** The directory must not quietly empty out and silence everything above. */
    @Test
    void theTablesExist() {
        assertFalse(chestTables().isEmpty(), "no chest loot tables at all");
        assertFalse(bossTables().isEmpty(), "no boss loot tables at all; if the boss chest was"
                + " dropped deliberately, the two tests keyed on them should go too");
    }

    private static List<Path> bossTables() {
        return chestTables().stream()
                .filter(f -> f.getFileName().toString().startsWith(BOSS_PREFIX)).toList();
    }

    private static List<Path> chestTables() {
        try {
            Path dir = Paths.get(ShippedChestLootTest.class.getResource(CHESTS).toURI());
            try (Stream<Path> files = Files.walk(dir)) {
                return files.filter(f -> f.toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::toString)).toList();
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not list " + CHESTS, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + file, e);
        }
    }
}
