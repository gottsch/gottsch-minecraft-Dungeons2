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
import mod.gottsch.forge.dungeons2.core.block.entity.ChestMarkerBlockEntity;
import mod.gottsch.forge.dungeons2.core.block.entity.SpawnerMarkerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An {@code end_rooms} template must be tier-NEUTRAL: it says which marker is the boss's, never
 * what the boss's reward is.
 *
 * <h2>What went wrong, and why it needs a test rather than a convention</h2>
 * <p>{@code small_boss_1} shipped with {@code lootTable: dungeons2:chests/classic_boss_small} on its
 * chest marker and {@code mobSetName: dungeons2:small_dungeon_boss} on its boss spawner. That welded
 * the reward to the geometry, so the one authored boss room paid out small loot in every size of
 * dungeon &mdash; including the LARGE one it was reported from.</p>
 *
 * <p>Convention alone will not hold this, because of <em>how</em> these files are authored: a
 * template is edited in a dev world and re-saved through a structure block, and the marker's own
 * block entity writes back whatever fields it is holding. Type a table id into the marker while
 * looking at something else, save, and the tier is welded back on &mdash; in a binary file, in a
 * field that looks entirely reasonable, with nothing in game wrong until two dungeons' boss chests
 * are compared side by side.</p>
 *
 * <p>The datapack half of the same feature is {@code BossTierWiringTest}.</p>
 *
 * @author Mark Gottschling on Sep 4, 2026
 */
class BossRoomAuthoringTest {

    private static final String END_ROOMS = "/data/dungeons2/structures/end_rooms";
    private static final String END_ROOM_POOLS =
            "/data/dungeons2/worldgen/template_pool/end_rooms";
    private static final String CHEST_MARKER = "dungeons2:chest_marker";
    private static final String SPAWNER_MARKER = "dungeons2:spawner_marker";
    private static final String PROCESSOR_LISTS = "/data/dungeons2/worldgen/processor_list";
    private static final String CLASSIC_BASE =
            "/data/dungeons2/dungeons2/motif_config/classic/base.json";

    /** The keys a tier processor list hands a boss room its reward and its mobs through. */
    private static final Set<String> TIER_VALUE_KEYS =
            Set.of("boss_loot_table", "boss_mob_set", "escort_mob_set", "ranged_escort_mob_set");

    /**
     * No boss marker may also name its own table or mob set.
     *
     * <p>Both processors resolve the boss rung FIRST, so a marker doing both would not misbehave
     * today &mdash; the pool would win and the authored id would sit there inert. That is precisely
     * why it is worth failing on: an inert field that looks authoritative is how the next person
     * concludes the tier is set on the template, and re-welds it somewhere the pool does not
     * override.</p>
     */
    @Test
    void aBossMarkerNamesNoRewardOfItsOwn() {
        List<String> offenders = new ArrayList<>();
        for (Path template : endRooms()) {
            for (CompoundTag marker : markers(template)) {
                if (!marker.getBoolean("boss")) {
                    continue;
                }
                String id = marker.getString("id");
                if (CHEST_MARKER.equals(id) && marker.contains(ChestMarkerBlockEntity.LOOT_TABLE)) {
                    offenders.add(template.getFileName() + ": boss chest also names lootTable "
                            + marker.getString(ChestMarkerBlockEntity.LOOT_TABLE));
                }
                if (SPAWNER_MARKER.equals(id) && marker.contains(SpawnerMarkerBlockEntity.MOB_SET_NAME)) {
                    offenders.add(template.getFileName() + ": boss spawner also names mobSetName "
                            + marker.getString(SpawnerMarkerBlockEntity.MOB_SET_NAME));
                }
            }
        }
        if (!offenders.isEmpty()) {
            fail("a boss marker declares a ROLE and takes its value from the tier's processor list."
                    + " Naming a value too is inert at best and re-welds the tier to the template at"
                    + " worst -- see this class:\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * Every end room has exactly one boss chest and at least one boss spawner.
     *
     * <p>Two bosses is a design error, not a datapack one, so the count is asserted rather than
     * merely allowed. Zero is worse and quieter: with no {@code boss: true} anywhere, the tier's
     * {@code boss_loot_table} fires on nothing, the chest falls through to the pool's ordinary
     * weighted default, and the boss room pays out like an ordinary room &mdash; the reported bug
     * again, arriving through the fix for it.</p>
     *
     * <p>Scoped to templates an {@code end_rooms} POOL actually names, unlike the two sweeps either
     * side of it. A template on disk that no pool draws cannot misbehave in game, and
     * {@code boss_1.nbt} is exactly that: a 19x19 shell with no markers in it at all, authored
     * ahead of the large tier that will one day want it. Requiring markers of a template nobody
     * places would make this test a blocker on unfinished authoring, which is the same call
     * {@code ShippedTemplateBlocksTest} makes about bedrock.</p>
     */
    @Test
    void everyEndRoomHasOneBossChestAndABossSpawner() {
        List<String> wrong = new ArrayList<>();
        for (Path template : pooledEndRooms()) {
            int chests = 0;
            int spawners = 0;
            for (CompoundTag marker : markers(template)) {
                if (!marker.getBoolean("boss")) {
                    continue;
                }
                if (CHEST_MARKER.equals(marker.getString("id"))) {
                    chests++;
                } else if (SPAWNER_MARKER.equals(marker.getString("id"))) {
                    spawners++;
                }
            }
            if (chests != 1) {
                wrong.add(template.getFileName() + " has " + chests + " boss chests, expected 1");
            }
            if (spawners < 1) {
                wrong.add(template.getFileName() + " has no boss spawner");
            }
        }
        if (!wrong.isEmpty()) {
            fail("end room(s) misdeclare their boss markers:\n  " + String.join("\n  ", wrong));
        }
    }

    /**
     * An ORDINARY spawner in a boss room must still name its own set.
     *
     * <p>The tier lists give {@code dungeons2:spawner} a non-boss {@code mob_set} default, so an
     * unnamed marker is survivable rather than fatal &mdash; but it silently becomes whatever that
     * default happens to be, which is not what an author who wrote four distinct sets into a room
     * meant. Cheap to state here, and it is the assertion that makes "absence means the pool's
     * ordinary default" a checked rule rather than an accident.</p>
     */
    @Test
    void everyNonBossSpawnerNamesItsSet() {
        List<String> unnamed = new ArrayList<>();
        for (Path template : endRooms()) {
            for (CompoundTag marker : markers(template)) {
                if (!SPAWNER_MARKER.equals(marker.getString("id")) || marker.getBoolean("boss")) {
                    continue;
                }
                boolean role = marker.getBoolean(SpawnerMarkerBlockEntity.ESCORT)
                        || marker.getBoolean(SpawnerMarkerBlockEntity.RANGED_ESCORT);
                if (!role && !marker.contains(SpawnerMarkerBlockEntity.MOB_SET_NAME)) {
                    unnamed.add(template.getFileName() + " at " + marker);
                }
            }
        }
        if (!unnamed.isEmpty()) {
            fail("spawner marker(s) in an end room declare no role and name no set, so they take"
                    + " the list's fallback set rather than anything authored:\n  "
                    + String.join("\n  ", unnamed));
        }
    }

    /**
     * A marker declares AT MOST one role.
     *
     * <p>{@code resolveMobSet} checks boss, then ranged escort, then escort, so a marker carrying
     * two would still resolve to something -- silently, and to whichever the code happens to test
     * first. Pinning it here means the precedence order in Java is a tie-breaker nobody's data
     * relies on, which is what lets that order be changed later without changing a dungeon.</p>
     */
    @Test
    void noMarkerDeclaresTwoRoles() {
        List<String> both = new ArrayList<>();
        for (Path template : endRooms()) {
            for (CompoundTag marker : markers(template)) {
                int roles = 0;
                for (String flag : List.of("boss", SpawnerMarkerBlockEntity.ESCORT,
                        SpawnerMarkerBlockEntity.RANGED_ESCORT)) {
                    if (marker.getBoolean(flag)) {
                        roles++;
                    }
                }
                if (roles > 1) {
                    both.add(template.getFileName() + " at " + marker);
                }
            }
        }
        if (!both.isEmpty()) {
            fail("marker(s) declare more than one role:\n  " + String.join("\n  ", both));
        }
    }

    /** The sweep passes vacuously if it is reading nothing. */
    @Test
    void theSweepFindsTheEndRooms() {
        assertFalse(endRooms().isEmpty(), "no end room templates found at " + END_ROOMS);
        assertEquals(1, endRooms().stream()
                        .filter(p -> p.getFileName().toString().equals("small_boss_1.nbt")).count(),
                "small_boss_1.nbt is the template the tier split was built against");
        assertFalse(pooledEndRooms().isEmpty(),
                "no end room template is named by any pool, so the marker checks pass vacuously");
    }

    // ---------- reading ----------

    /** Every block-entity tag in the template that is one of the two marker kinds. */
    private static List<CompoundTag> markers(Path template) {
        List<CompoundTag> found = new ArrayList<>();
        for (Tag blockTag : read(template).getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) blockTag;
            if (!block.contains("nbt")) {
                continue;
            }
            CompoundTag data = block.getCompound("nbt");
            String id = data.getString("id");
            if (CHEST_MARKER.equals(id) || SPAWNER_MARKER.equals(id)) {
                found.add(data);
            }
        }
        return found;
    }

    /**
     * End room templates that an {@code end_rooms} pool names, read out of the pool JSON.
     *
     * <p>Lenient parsing, like every other reader in the suite: the shipped pools carry
     * {@code //} comments.</p>
     */
    private static List<Path> pooledEndRooms() {
        Set<String> located = new LinkedHashSet<>();
        for (Path pool : poolFiles()) {
            try (java.io.Reader reader = Files.newBufferedReader(pool, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement entry : root.getAsJsonArray("elements")) {
                    JsonObject element = entry.getAsJsonObject().getAsJsonObject("element");
                    if (element.has("location")) {
                        String id = element.get("location").getAsString();
                        located.add(id.substring(id.lastIndexOf('/') + 1) + ".nbt");
                    }
                }
            } catch (IOException unreadable) {
                throw new UncheckedIOException("could not read " + pool, unreadable);
            }
        }
        assertFalse(located.isEmpty(), "no end_rooms pool names a template, so this sweep reads"
                + " nothing -- see PoolWiringTest, which owns the pools themselves");
        return endRooms().stream()
                .filter(path -> located.contains(path.getFileName().toString()))
                .toList();
    }

    private static List<Path> poolFiles() {
        URL url = BossRoomAuthoringTest.class.getResource(END_ROOM_POOLS);
        if (url == null) {
            return fail("no end_rooms pools at " + END_ROOM_POOLS);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + END_ROOM_POOLS + ": " + unreadable);
        }
    }

    private static CompoundTag read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    /**
     * <strong>No end-room marker may name a value a TIER would have supplied</strong> &mdash;
     * whether or not it declares a role.
     *
     * <h2>The gap this closes</h2>
     * <p>{@link #aBossMarkerNamesNoRewardOfItsOwn} above only inspects markers that ALREADY declare
     * {@code boss}, so a marker carrying a value and <em>no role at all</em> is invisible to it.
     * {@link #everyEndRoomHasOneBossChestAndABossSpawner} then counts zero boss markers and says
     * so &mdash; truthfully, and without mentioning the chest marker sitting right there in the
     * file. A marker that names a value and declares nothing falls between the two.</p>
     *
     * <p>That is not hypothetical. On 2026-09-14 both {@code medium_boss_1.nbt} and
     * {@code large_boss_1.nbt} were authored exactly that way: {@code mobSetName:
     * dungeons2:medium_dungeon_boss} on the boss spawner, {@code lootTable:
     * dungeons2:chests/classic_boss_medium} on the chest, and not one {@code boss} byte between
     * them. Both had just been re-saved, so the first suspicion was that the edit never reached
     * {@code src} &mdash; the failure said nothing to rule that out.</p>
     *
     * <h2>What counts as a tier's value</h2>
     * <p>Read from the tier processor lists themselves rather than pattern-matched on the id: the
     * offending set is exactly "a value the pool would have supplied anyway", which is the whole
     * argument, and a list that gains a fourth tier is covered without anyone editing this.</p>
     *
     * <p><strong>Minus whatever the ordinary route also draws.</strong> The small tier's escort
     * sets are {@code classic_undead} and {@code classic_ranged}, which the motif's ordinary
     * {@code mob_sets} bands name too &mdash; so a spawner naming one of those is an ordinary
     * spawner making an ordinary choice, not a tier weld, and flagging it would be a false
     * positive that teaches people to work around this test. What is left after the subtraction is
     * the genuinely tier-scoped ids, which nothing but a tier has any business naming.</p>
     */
    @Test
    void noEndRoomMarkerNamesATierScopedValue() {
        Set<String> tierScoped = tierSuppliedValues();
        tierScoped.removeAll(ordinaryMobSets());
        assertFalse(tierScoped.isEmpty(), "every value the boss tiers supply is also drawn by the"
                + " ordinary route, so this sweep can no longer catch anything -- the subtraction"
                + " has outlived the wiring it was written against");

        List<String> offenders = new ArrayList<>();
        for (Path template : endRooms()) {
            for (CompoundTag marker : markers(template)) {
                String id = marker.getString("id");
                String named = null;
                String field = null;
                if (CHEST_MARKER.equals(id) && marker.contains(ChestMarkerBlockEntity.LOOT_TABLE)) {
                    named = marker.getString(ChestMarkerBlockEntity.LOOT_TABLE);
                    field = "lootTable";
                } else if (SPAWNER_MARKER.equals(id)
                        && marker.contains(SpawnerMarkerBlockEntity.MOB_SET_NAME)) {
                    named = marker.getString(SpawnerMarkerBlockEntity.MOB_SET_NAME);
                    field = "mobSetName";
                }
                if (named != null && tierScoped.contains(named)) {
                    offenders.add(template.getFileName() + ": " + field + " = " + named);
                }
            }
        }
        if (!offenders.isEmpty()) {
            fail("an end-room marker NAMES a value that a boss tier's processor list already"
                    + " supplies. Delete the field and declare the ROLE instead -- boss: 1b,"
                    + " escort: 1b or ranged_escort: 1b on a spawner, boss: 1b (with treasure: 1b)"
                    + " on the chest. See small_boss_1.nbt, which is authored correctly:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    /** Every boss/escort set and boss table the tier processor lists hand out. */
    private static Set<String> tierSuppliedValues() {
        Set<String> values = new LinkedHashSet<>();
        for (Path list : tierListFiles()) {
            collectStrings(json(list), TIER_VALUE_KEYS, values);
        }
        assertFalse(values.isEmpty(), "no tier processor list supplies a boss mob set or table,"
                + " so this sweep reads nothing -- see BossTierWiringTest, which owns the lists");
        return values;
    }

    /** Every mob set the motif's ordinary depth bands can draw. */
    private static Set<String> ordinaryMobSets() {
        Set<String> sets = new LinkedHashSet<>();
        collectStrings(jsonResource(CLASSIC_BASE), Set.of("mob_set"), sets);
        return sets;
    }

    /** Every string value under any of {@code keys}, anywhere in the tree. */
    private static void collectStrings(JsonElement element, Set<String> keys, Set<String> into) {
        if (element.isJsonObject()) {
            for (String key : element.getAsJsonObject().keySet()) {
                JsonElement child = element.getAsJsonObject().get(key);
                if (keys.contains(key) && child.isJsonPrimitive()) {
                    into.add(child.getAsString());
                } else {
                    collectStrings(child, keys, into);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectStrings(child, keys, into);
            }
        }
    }

    private static List<Path> tierListFiles() {
        URL url = BossRoomAuthoringTest.class.getResource(PROCESSOR_LISTS);
        if (url == null) {
            return fail("no processor lists at " + PROCESSOR_LISTS);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("classic_boss_"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + PROCESSOR_LISTS + ": " + unreadable);
        }
    }

    private static JsonElement json(Path file) {
        try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static JsonElement jsonResource(String resource) {
        try (InputStream in = BossRoomAuthoringTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                return fail("missing datapack resource on classpath: " + resource);
            }
            return JsonParser.parseReader(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + resource, unreadable);
        }
    }

    private static List<Path> endRooms() {
        URL url = BossRoomAuthoringTest.class.getResource(END_ROOMS);
        if (url == null) {
            return fail("no end room content at " + END_ROOMS);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + END_ROOMS + ": " + unreadable);
        }
    }
}
