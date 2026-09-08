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
package mod.gottsch.forge.dungeons2.core.world.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The boss room's reward follows the DUNGEON's size tier, not the room that was drawn.
 *
 * <h2>The bug this is the guard for</h2>
 * <p>Reported from a live world 2026-09-04: {@code small_boss_1}, the only end room authored, was
 * drawn for a LARGE dungeon and paid out {@code classic_boss_small} &mdash; because the template's
 * own chest marker named that table. The reward followed the ROOM when it has to follow the DUNGEON.
 * A small boss room at the bottom of a five-floor descent is still the end of a five-floor
 * descent.</p>
 *
 * <h2>The three things that together fix it, one test each</h2>
 * <ol>
 *   <li>every size tier ships a pool, and each names <em>its own</em> processor list &mdash; the
 *       pool is the only place the dungeon's size is knowable by the time the room is built;</li>
 *   <li>the tier lists are identical apart from the two {@code boss_} fields, so a weathering change
 *       cannot land on one tier and miss the other two;</li>
 *   <li>the templates are tier-NEUTRAL: a boss marker says {@code boss: true} and nothing about
 *       which table or mob set, which is what lets one geometry serve all three sizes.</li>
 * </ol>
 *
 * <p>The third is the one that regresses most easily, and silently: re-saving a boss template from
 * a dev world after typing a table id into the marker would weld the tier back on, and nothing in
 * game would look wrong until someone compared two dungeons' chests. {@code BossRoomAuthoringTest}
 * owns that half, reading the {@code .nbt}; this class owns the datapack half.
 *
 * @author Mark Gottschling on Sep 4, 2026
 */
class BossTierWiringTest {

    private static final String POOL_DIR = "/data/dungeons2/worldgen/template_pool/end_rooms/classic";
    private static final String LIST_DIR = "/data/dungeons2/worldgen/processor_list";

    /** The loot table and mob set each tier must name. The whole point of the split. */
    private static final List<String[]> EXPECTED = List.of(
            new String[] {"small",  "dungeons2:chests/classic_boss_small",  "dungeons2:small_dungeon_boss"},
            new String[] {"medium", "dungeons2:chests/classic_boss_medium", "dungeons2:medium_dungeon_boss"},
            new String[] {"large",  "dungeons2:chests/classic_boss_large",  "dungeons2:large_dungeon_boss"});

    @Test
    void everySizeTierShipsItsOwnPoolNamingItsOwnProcessorList() {
        for (DungeonSize size : DungeonSize.values()) {
            String tier = size.name().toLowerCase(Locale.ROOT);
            JsonObject pool = json(POOL_DIR + "/" + tier + "/normal.json");
            JsonArray elements = pool.getAsJsonArray("elements");
            assertTrue(elements.size() > 0,
                    "the " + tier + " boss pool is empty, which switches the feature off for that"
                            + " size -- see DungeonStructure#hasAnyBossPool");
            for (JsonElement entry : elements) {
                JsonObject element = entry.getAsJsonObject().getAsJsonObject("element");
                assertEquals("dungeons2:classic_boss_weathering_" + tier,
                        element.get("processors").getAsString(),
                        "the " + tier + " pool must name the " + tier + " processor list -- the list"
                                + " is where the tier's reward lives, so a pool pointing at another"
                                + " tier's list hands out that tier's loot");
            }
        }
    }

    /**
     * The tier lists differ ONLY in {@code boss_loot_table} and {@code boss_mob_set}.
     *
     * <p>Three near-identical 340-line files is the cost of vanilla's one-processor-list-per-element
     * rule, and the risk that buys is drift: an aging rule tuned on the small list and not the other
     * two would make the same room weather differently by dungeon size, for no authored reason and
     * with nothing to say so. Comparing the parsed JSON with the two tier fields blanked is the
     * cheapest thing that catches it.</p>
     */
    @Test
    void theTierListsDifferOnlyInTheirTierFields() {
        String reference = null;
        String referenceTier = null;
        for (String[] tier : EXPECTED) {
            JsonObject list = json(LIST_DIR + "/classic_boss_weathering_" + tier[0] + ".json");
            blankBossFields(list);
            String normalised = list.toString();
            if (reference == null) {
                reference = normalised;
                referenceTier = tier[0];
                continue;
            }
            assertEquals(reference, normalised,
                    "classic_boss_weathering_" + tier[0] + " has drifted from _" + referenceTier
                            + " somewhere other than its boss_ fields. The tiers must weather"
                            + " identically -- only the reward and the fight change with size.");
        }
    }

    @Test
    void eachTierNamesItsOwnLootTableAndMobSet() {
        List<String> wrong = new ArrayList<>();
        for (String[] tier : EXPECTED) {
            JsonObject list = json(LIST_DIR + "/classic_boss_weathering_" + tier[0] + ".json");
            String loot = bossField(list, "dungeons2:chest", "boss_loot_table");
            String mobs = bossField(list, "dungeons2:spawner", "boss_mob_set");
            if (!tier[1].equals(loot)) {
                wrong.add(tier[0] + " boss_loot_table is " + loot + ", expected " + tier[1]);
            }
            if (!tier[2].equals(mobs)) {
                wrong.add(tier[0] + " boss_mob_set is " + mobs + ", expected " + tier[2]);
            }
        }
        if (!wrong.isEmpty()) {
            fail("a tier naming another tier's reward is the reported bug with extra steps:\n  "
                    + String.join("\n  ", wrong));
        }
    }

    /**
     * A tier's ordinary {@code mob_set} default must NOT be a boss set.
     *
     * <p>The alternative design considered for this feature was "an unnamed spawner marker inherits
     * the pool's set, and the pool's set is the boss's" &mdash; which needs no flag and is one line
     * shorter. It was rejected because a boss room holds several ordinary spawners and exactly one
     * boss, so a marker that merely <em>forgot</em> to name a set would become a second boss,
     * silently. This asserts the rejection stuck.</p>
     */
    @Test
    void theFallbackMobSetIsNotABossSet() {
        Set<String> bossSets = new LinkedHashSet<>();
        for (String[] tier : EXPECTED) {
            bossSets.add(tier[2]);
        }
        for (String[] tier : EXPECTED) {
            JsonObject list = json(LIST_DIR + "/classic_boss_weathering_" + tier[0] + ".json");
            String fallback = bossField(list, "dungeons2:spawner", "mob_set");
            assertTrue(fallback != null && !bossSets.contains(fallback),
                    "the " + tier[0] + " list's ordinary mob_set is " + fallback + ", a boss set."
                            + " Any spawner marker in the room that names no set of its own would"
                            + " become a second boss -- see this test.");
        }
    }

    /**
     * The tier pool id, which is the whole coupling between the planner's rolled size and the
     * reward. Pinned because it is a string built in one place and authored as a folder name in
     * another, and a mismatch degrades SILENTLY to the procedural terminal room.
     */
    @Test
    void theTierPoolIdIsTheFolderTheDataUses() {
        assertEquals("dungeons2:end_rooms/classic/large/normal",
                DungeonStructure.bossRoomStartPool("classic", DungeonSize.LARGE).toString());
        assertEquals("dungeons2:end_rooms/desert/small/normal",
                DungeonStructure.bossRoomStartPool("desert", DungeonSize.SMALL).toString());
    }

    /**
     * A tier borrows DOWNWARD, never up, then falls back to the untiered id.
     *
     * <p>Direction is the load-bearing half. Downward always fits -- a floor that holds a large
     * boss room holds a small one -- whereas handing a SMALL dungeon's bottom floor a LARGE
     * template burns all four of {@code placeBossRoom}'s attempts discovering it does not fit, and
     * the dungeon ends in a procedural room instead. The borrow keeps THIS tier's processor list
     * either way, which is what stops the borrow reintroducing the reward bug one level up.</p>
     */
    @Test
    void aTierBorrowsDownwardThenFallsBackToTheUntieredPool() {
        assertEquals(List.of("dungeons2:end_rooms/classic/medium/normal",
                        "dungeons2:end_rooms/classic/small/normal",
                        "dungeons2:end_rooms/classic/normal"),
                DungeonStructure.bossBorrowOrder("classic", DungeonSize.LARGE).stream()
                        .map(Object::toString).toList());

        assertEquals(List.of("dungeons2:end_rooms/classic/normal"),
                DungeonStructure.bossBorrowOrder("classic", DungeonSize.SMALL).stream()
                        .map(Object::toString).toList(),
                "SMALL is the bottom tier: it has nothing below it to borrow from, and must NOT"
                        + " reach up to MEDIUM or LARGE for a template its floor may not fit");
    }

    // ---------- reading ----------

    /** Blanks the tier fields wherever they appear, so the rest can be compared verbatim. */
    private static void blankBossFields(JsonObject list) {
        for (JsonElement entry : list.getAsJsonArray("processors")) {
            JsonObject processor = entry.getAsJsonObject();
            for (String field : List.of("boss_loot_table", "boss_mob_set",
                    "escort_mob_set", "ranged_escort_mob_set")) {
                if (processor.has(field)) {
                    processor.addProperty(field, "<tier>");
                }
            }
        }
    }

    /** One field off the processor entry of the given type, or null. */
    private static String bossField(JsonObject list, String processorType, String field) {
        for (JsonElement entry : list.getAsJsonArray("processors")) {
            JsonObject processor = entry.getAsJsonObject();
            if (processor.has("processor_type")
                    && processorType.equals(processor.get("processor_type").getAsString())
                    && processor.has(field)) {
                return processor.get(field).getAsString();
            }
        }
        return null;
    }

    /** Lenient, like every other reader in the suite: the shipped files carry // comments. */
    private static JsonObject json(String resource) {
        try (InputStream in = BossTierWiringTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing shipped resource " + resource);
            return JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + resource, e);
        }
    }
}
