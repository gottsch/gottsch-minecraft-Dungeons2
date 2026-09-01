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
import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
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
    private static final String MOTIF_CONFIGS = "/data/dungeons2/dungeons2/motif_config";

    /**
     * Dungeons2's own entity ids. Not resolvable through the registry headlessly (Forge does not
     * run), so they are listed; {@code StructureSpawnOverridesTest} already pins the same pair from
     * the spawn-overrides side, and a third place naming them would be one too many.
     */
    /**
     * This mod's own entity ids, read from the LANG FILE rather than listed here.
     *
     * <p>{@code BuiltInRegistries} carries no modded entity headlessly, so a {@code dungeons2:} id
     * has to be checked against something else. A hand-maintained literal was fine at two mobs and
     * is a liability at twenty-five: every mob added would have to be remembered here, and
     * forgetting one fails the build with "this entity does not exist" pointing at a mob that does.
     * The lang file is the same indirect proof {@code StructureSpawnOverridesTest} uses, and it is
     * strictly better than a literal &mdash; a mob with no display name is a real defect, so the
     * check keeps its teeth rather than merely getting out of the way.</p>
     */
    private static boolean isOwnEntity(String id) {
        return id.startsWith("dungeons2:")
                && lang().has("entity.dungeons2." + id.substring("dungeons2:".length()));
    }

    private static JsonObject lang() {
        if (LANG_CACHE == null) {
            try (java.io.InputStream in = ShippedMobSetsTest.class.getResourceAsStream(LANG)) {
                LANG_CACHE = com.google.gson.JsonParser.parseReader(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalStateException("could not read " + LANG, e);
            }
        }
        return LANG_CACHE;
    }

    private static final String LANG = "/assets/dungeons2/lang/en_us.json";
    private static JsonObject LANG_CACHE;

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
                if (EMPTY.equals(id) || isOwnEntity(id)) {
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

    /**
     * The same sweep from the other two sources of spawner content: a motif's
     * {@code mob_sets_by_floor_index} depth table, and any scheme's {@code spawners} slot that names
     * its own sets instead of deferring to the table.
     *
     * <p>Worth its own check rather than folding into the processor one, because the failure is
     * worse here. A processor's dangling set breaks the handful of authored templates carrying the
     * marker; a table's breaks <em>every procedural room on the floors that band covers</em>. Both
     * are invisible in game &mdash; a spawner drawing from a set that does not exist is an
     * invisible block that ticks and does nothing.</p>
     */
    @Test
    void everyMobSetASchemeOrDepthBandReferencesShips() {
        Set<String> shipped = new LinkedHashSet<>();
        for (Path file : jsonFilesUnder(MOB_SETS)) {
            shipped.add(parse(file).getAsJsonObject().get("id").getAsString());
        }

        List<String> dangling = new ArrayList<>();
        int sources = 0;
        for (Path file : jsonFilesUnder(MOTIF_CONFIGS)) {
            JsonObject fragment = parse(file).getAsJsonObject();
            String where = file.getFileName().toString();

            if (fragment.has("mob_sets_by_floor_index")) {
                for (JsonElement wrapped : fragment.getAsJsonArray("mob_sets_by_floor_index")) {
                    JsonObject band = wrapped.getAsJsonObject();
                    sources++;
                    checkSets(band, shipped, where + " / floor "
                            + band.get("min_floor_index").getAsString() + " band", dangling);
                }
            }
            if (!fragment.has("schemes")) {
                continue;
            }
            for (JsonElement wrapped : fragment.getAsJsonArray("schemes")) {
                JsonObject scheme = wrapped.getAsJsonObject();
                if (!scheme.has("spawners")) {
                    continue;
                }
                JsonObject spawners = scheme.getAsJsonObject("spawners");
                // A slot with no mobSets defers to the depth table above -- nothing to check, and
                // that is the common case by design, not an omission.
                if (spawners.has("mob_sets")) {
                    sources++;
                    checkSets(spawners, shipped,
                            where + " / " + scheme.get("name").getAsString(), dangling);
                }
            }
        }
        if (!dangling.isEmpty()) {
            org.junit.jupiter.api.Assertions.fail(dangling.size() + " spawner source(s) reference a"
                    + " mob set that does not ship. Every procedural room they fire in would get an"
                    + " invisible block that spawns nothing:\n  "
                    + String.join("\n  ", dangling) + "\nShipped: " + shipped);
        }
        org.junit.jupiter.api.Assertions.assertTrue(sources > 0,
                "no shipped motif declares a depth band or a scheme-level mob_sets list, so this"
                        + " check passed vacuously -- either procedural spawners were removed or a"
                        + " key was renamed");
    }

    /**
     * A shipped depth curve must not get <em>easier</em> as it descends.
     *
     * <p>Bands may set their own {@code min_mobs}/{@code max_mobs}; a band that omits them falls back
     * to {@link SpawnerConfig#DEFAULT_MIN_MOBS}/{@link SpawnerConfig#DEFAULT_MAX_MOBS}, so the
     * comparison has to be made on the <em>resolved</em> numbers rather than the declared ones. That
     * is the whole trap here: declaring counts on the deep band alone reads as an escalation, but
     * declaring them on the shallow band alone silently makes the depths tamer, and the JSON looks
     * equally deliberate either way.</p>
     *
     * <p>Not a codec rule. A pack is entitled to author a curve that eases off &mdash; a mod whose
     * deep floors are meant to be sparse and tense is a legitimate thing to build. This pins what
     * <strong>this</strong> mod ships, which is a curve that escalates.</p>
     */
    @Test
    void theShippedDepthCurveNeverGetsEasierAsItDescends() {
        List<String> regressions = new ArrayList<>();
        int declaredCounts = 0;

        for (Path file : jsonFilesUnder(MOTIF_CONFIGS)) {
            JsonObject fragment = parse(file).getAsJsonObject();
            if (!fragment.has("mob_sets_by_floor_index")) {
                continue;
            }
            String where = file.getFileName().toString();

            List<JsonObject> bands = new ArrayList<>();
            for (JsonElement wrapped : fragment.getAsJsonArray("mob_sets_by_floor_index")) {
                bands.add(wrapped.getAsJsonObject());
            }
            // File order is not authored order -- MobSetBand.forFloor reads the table by
            // minFloorIndex, so a curve has to be judged in that order too.
            bands.sort(java.util.Comparator.comparingInt(
                    band -> band.has("min_floor_index") ? band.get("min_floor_index").getAsInt() : 0));

            int previousMin = Integer.MIN_VALUE;
            int previousMax = Integer.MIN_VALUE;
            String previousWhere = null;
            for (JsonObject band : bands) {
                int start = band.has("min_floor_index") ? band.get("min_floor_index").getAsInt() : 0;
                if (band.has("min_mobs") || band.has("max_mobs")) {
                    declaredCounts++;
                }
                int min = band.has("min_mobs")
                        ? band.get("min_mobs").getAsInt() : SpawnerConfig.DEFAULT_MIN_MOBS;
                int max = band.has("max_mobs")
                        ? band.get("max_mobs").getAsInt() : SpawnerConfig.DEFAULT_MAX_MOBS;
                String here = where + " / floor " + start + " band (" + min + ".." + max + ")";

                if (previousWhere != null && (min < previousMin || max < previousMax)) {
                    regressions.add(here + " releases fewer mobs than the shallower "
                            + previousWhere);
                }
                previousMin = min;
                previousMax = max;
                previousWhere = here;
            }
        }

        org.junit.jupiter.api.Assertions.assertTrue(regressions.isEmpty(),
                "a shipped depth band gets EASIER as it descends, which is the opposite of what the"
                        + " table is for. Remember an omitted count resolves to the default ("
                        + SpawnerConfig.DEFAULT_MIN_MOBS + ".." + SpawnerConfig.DEFAULT_MAX_MOBS
                        + "), so a shallow band declaring counts can cause this without the deep"
                        + " band changing at all:\n  " + String.join("\n  ", regressions));

        org.junit.jupiter.api.Assertions.assertTrue(declaredCounts > 0,
                "no shipped band declares min_mobs/max_mobs, so this check passed vacuously -- either"
                        + " the per-band counts were removed or the keys were renamed");
    }

    /**
     * Every shipped scheme that places spawners must be answerable by its motif's depth table.
     *
     * <p>The two halves of the override are easy to get half-done: strip {@code mob_sets} off a
     * scheme to let it inherit, and forget to add the table. Nothing complains &mdash;
     * {@code SpawnerConfig} resolves to an empty list and the room places no spawners at all, which
     * looks exactly like a scheme that never had the slot.</p>
     */
    @Test
    void everySchemeThatDefersHasATableToDeferTo() {
        List<String> orphans = new ArrayList<>();
        for (Path file : jsonFilesUnder(MOTIF_CONFIGS)) {
            JsonObject fragment = parse(file).getAsJsonObject();
            if (!fragment.has("schemes")) {
                continue;
            }
            // Same folder = same motif; a fragment may hold the schemes while a sibling holds the
            // table, which is the whole point of fragments, so the table is looked for across the
            // motif's directory rather than in this file alone.
            boolean tableInMotif = false;
            for (Path sibling : jsonFilesUnder(MOTIF_CONFIGS)) {
                if (sibling.getParent().equals(file.getParent())
                        && parse(sibling).getAsJsonObject().has("mob_sets_by_floor_index")) {
                    tableInMotif = true;
                    break;
                }
            }
            for (JsonElement wrapped : fragment.getAsJsonArray("schemes")) {
                JsonObject scheme = wrapped.getAsJsonObject();
                if (!scheme.has("spawners")) {
                    continue;
                }
                if (!scheme.getAsJsonObject("spawners").has("mob_sets") && !tableInMotif) {
                    orphans.add(file.getParent().getFileName() + " / "
                            + scheme.get("name").getAsString());
                }
            }
        }
        if (!orphans.isEmpty()) {
            org.junit.jupiter.api.Assertions.fail(orphans.size() + " scheme(s) declare spawners with"
                    + " no mob_sets and belong to a motif with no mob_sets_by_floor_index table, so they"
                    + " resolve to nothing and place no spawners:\n  "
                    + String.join("\n  ", orphans));
        }
    }

    private static void checkSets(JsonObject holder, Set<String> shipped, String where,
                                  List<String> dangling) {
        for (JsonElement entry : holder.getAsJsonArray("mob_sets")) {
            String referenced = entry.getAsJsonObject().get("mob_set").getAsString();
            if (!shipped.contains(referenced)) {
                dangling.add(where + " -> " + referenced);
            }
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
