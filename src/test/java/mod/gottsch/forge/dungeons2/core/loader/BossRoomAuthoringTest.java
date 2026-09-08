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
