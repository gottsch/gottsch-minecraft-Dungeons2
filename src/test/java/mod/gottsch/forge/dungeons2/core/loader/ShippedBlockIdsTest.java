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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The sweep backlog #13 asked for: <strong>every block id in every shipped datapack file names a
 * block that exists.</strong>
 *
 * <h2>Why a typo cannot be caught anywhere else</h2>
 * <p>{@code BuiltInRegistries.BLOCK} is a <em>defaulted</em> registry, so an unknown id does not
 * fail a codec &mdash; it resolves to {@code minecraft:air}. A misspelled decay target therefore
 * loads cleanly and produces a rule that never fires; a misspelled palette entry grows air. Nothing
 * in the game says anything. GottschCore now logs a warning at decode time
 * ({@code BlockIds}), which is the right behaviour for a shared library, but a warning in a log is
 * not a build failure &mdash; this is.</p>
 *
 * <h2>How modded ids are checked without a running game</h2>
 * <p>A bare {@code Bootstrap.bootStrap()} has no Forge registries, so <strong>every</strong>
 * {@code dungeonblocks:} id resolves to air here and a registry check would pass everything. That is
 * exactly why the offline check that existed before this test validated nothing: it was asking the
 * registry a question the registry could not answer.</p>
 *
 * <p>So modded ids are checked against a different source of truth: <strong>the blockstate files in
 * the {@code dungeonblocks} jar</strong>, which is on the test classpath as a real dependency. Every
 * registered block has one, they are named for the block, and they ship in the artifact. It is an
 * indirect check, and it is the only one available without launching Minecraft.</p>
 *
 * <h2>The part that keeps this from going stale</h2>
 * <p>The sweep classifies by <strong>key name</strong>: {@link #BLOCK_KEYS} hold block ids,
 * {@link #NON_BLOCK_KEYS} hold something else that merely looks like one (a pool, a loot table, an
 * entity, a tag). A namespaced value under a key in <em>neither</em> list <strong>fails the
 * test</strong>.</p>
 *
 * <p>That is deliberate and is the whole design. The obvious alternative &mdash; sweep the keys we
 * know about &mdash; silently stops covering the next field somebody adds, which is precisely how
 * #13 sat half-done for two weeks. Adding {@code bracket_block} to the ceiling schema must not
 * quietly fall out of the sweep; here it fails until it is classified, which takes one line and one
 * decision.</p>
 *
 * @author Mark Gottschling on Aug 11, 2026
 */
class ShippedBlockIdsTest {

    /** Keys whose string values are block ids. */
    private static final Set<String> BLOCK_KEYS = Set.of(
            // scheme element slots
            "block", "alternate_block", "corner_block", "base_block", "cap_block",
            "stair_block", "centre_block", "top_block", "bracket_block",
            // floor-pattern material slots. Long-standing keys on FloorPatternEntry, but they only
            // reached this sweep once a FloorConfig gained its own `pattern` -- until then every
            // one lived in a scheme file the sweep does not read. Real block ids, so they are
            // verified rather than exempted.
            "primary_block", "secondary_block", "edge_left_block", "edge_right_block",
            // The gradient wall pattern's lower material. Its upper one is `top_block`, already
            // listed above for the platform slot -- the two records mean the same kind of thing by
            // it. The sweep classifies by LITERAL key name, so the 2026-08-31 rename of the whole
            // config schema to snake_case had to reach this list; an unclassified key fails this
            // test by design, which is what stopped that rename passing by omission.
            "bottom_block",
            // pit shapes (#3). floorBlock paves the sunken floor, rimBlock is the ring of stairs
            // around it, spikeBlock is the hazard shaft's stalagmite. All three are real block ids
            // resolved through BlockStateCodec, so a typo becomes air (#13) unless swept here.
            "floor_block", "rim_block", "spike_block",
            // motif material sections
            "wall", "ceiling", "floor", "door", "lintel", "base", "alternate_base",
            "alternate_floor", "arch_block",
            // processor palettes, and vanilla's own block-state object
            "blocks", "Name",
            // #48: the chest processor's target block, and a chests slot variant's block. Both are
            // real block ids, so they are verified here rather than exempted.
            "chest_block",
            // #10: the spawner processor's authored marker. A real block id, so it belongs here and
            // not in the exempt list -- the sweep verifies dungeons2: ids through our own
            // blockstate files exactly as it does dungeonblocks:, which is what makes a typo in it
            // a build failure rather than a marker that silently never matches.
            "marker_block");

    /**
     * Keys whose values are namespaced ids of something that is <em>not</em> a block. Listed rather
     * than ignored-by-default so that the "unclassified fails" rule above has teeth.
     */
    private static final Set<String> NON_BLOCK_KEYS = Set.of(
            // jigsaw / structure plumbing
            "name", "location", "fallback", "processors", "element_type", "processor_type",
            "predicate_type", "type", "structure", "feature", "features", "biomes", "values",
            // The `platforms` slot's dispatch axis. It is a registered PLATFORM LAYOUT id, not a
            // block -- the one slot that spells its type key something other than `type`, because
            // it already has a `type` meaning what the platform IS. It only reached this sweep at
            // all once the layouts became registry ids; as a bare word it was invisible here.
            "layout",
            // content references
            "entity", "loot_table", "function", "condition", "random_sequence",
            // #10: a mob set id, resolved from GottschCore's MobSetDataRegistry at datapack reload
            // rather than from the block registry. ShippedMobSetsTest is what verifies it. ONE
            // entry since 2026-08-31: the marker processor and a scheme's spawners slot used to
            // spell it `mob_set` and `mobSet` respectively, and this list carried both.
            "mob_set",
            // block TAGS -- the same class of typo, but resolved from datapacks rather than the
            // block registry, so out of scope here. Worth its own sweep if one ever bites.
            "tags",
            // #48: loot table ids, resolved from the loot table registry -- the chest processor's
            // pool-level default, the scheme slot's, and the chest loot band's, all one key since
            // the 2026-08-31 rename (they were `loot_table` and `lootTable`, listed twice here).
            // A sweep of these against the shipped loot_tables folder would be the chest
            // equivalent of ShippedMobSetsTest, and does not exist yet.
            // #7: the Mining Chest's ore bands name ITEMS, not blocks -- a dungeon that ate a
            // diamond ore pays back a diamond. Resolved from the item registry, so out of scope
            // here and verified instead by MiningHaulCalibrationTest.everyOreBandNamesARealItem,
            // which is the item-registry equivalent of this sweep.
            "item");

    /** Where a modded block proves it exists, absent a running game. See the class notes. */
    private static final String BLOCKSTATE_DIR = "/assets/%s/blockstates/%s.json";

    private static final String DATA_ROOT = "/data/dungeons2";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** One namespaced string found in one place, kept with enough context to be actionable. */
    private record Found(String file, String key, String value) {
        @Override
        public String toString() {
            return file + " -> \"" + key + "\": \"" + value + "\"";
        }
    }

    // ---------- the sweep ----------

    @Test
    void everyShippedBlockIdNamesABlockThatExists() {
        List<String> bad = new ArrayList<>();
        for (Found found : sweep()) {
            if (!BLOCK_KEYS.contains(found.key())) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(found.value());
            if (id == null) {
                bad.add(found + "  (not a valid resource location)");
            } else if (!exists(id)) {
                bad.add(found + "  " + suggestion(id));
            }
        }
        if (!bad.isEmpty()) {
            fail("shipped datapack files name " + bad.size() + " block(s) that do not exist."
                    + " Each one silently becomes minecraft:air at runtime:\n  "
                    + String.join("\n  ", bad));
        }
    }

    /**
     * The guard on the guard. A namespaced value under a key in neither list means the schema grew a
     * field this sweep does not know about &mdash; which is how a sweep quietly stops sweeping.
     */
    @Test
    void everyNamespacedValueSitsUnderAClassifiedKey() {
        Set<String> unclassified = new LinkedHashSet<>();
        for (Found found : sweep()) {
            if (!BLOCK_KEYS.contains(found.key()) && !NON_BLOCK_KEYS.contains(found.key())) {
                unclassified.add(found.toString());
            }
        }
        if (!unclassified.isEmpty()) {
            fail("these keys hold namespaced ids but are in neither BLOCK_KEYS nor NON_BLOCK_KEYS,"
                    + " so the block-id sweep is not covering them. Add each to whichever list is"
                    + " right:\n  " + String.join("\n  ", unclassified));
        }
    }

    /**
     * Shipped content may only use namespaces this test can actually verify. Without it, adding a
     * dependency on another block mod would make its ids unsweepable and nothing would say so.
     */
    @Test
    void everyBlockNamespaceIsOneThisSweepCanVerify() {
        Set<String> unverifiable = new LinkedHashSet<>();
        for (Found found : sweep()) {
            if (!BLOCK_KEYS.contains(found.key())) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(found.value());
            if (id != null && !"minecraft".equals(id.getNamespace())
                    && ShippedBlockIdsTest.class.getResource(
                            String.format(BLOCKSTATE_DIR, id.getNamespace(), "")) == null
                    && !hasBlockstates(id.getNamespace())) {
                unverifiable.add(id.getNamespace());
            }
        }
        assertTrue(unverifiable.isEmpty(),
                "no blockstate assets on the test classpath for namespace(s) " + unverifiable
                        + " -- add the mod as a test dependency, or this test is not checking its ids");
    }

    /** The sweep has to actually be finding things, or all three tests above pass vacuously. */
    @Test
    void theSweepFindsTheShippedContent() {
        List<Found> all = sweep();
        assertTrue(all.size() > 200, "expected the whole datapack, found " + all.size() + " ids");
        assertTrue(all.stream().anyMatch(f -> BLOCK_KEYS.contains(f.key())
                        && f.value().startsWith("dungeonblocks:")),
                "expected at least one dungeonblocks block id to be swept");
        assertTrue(all.stream().anyMatch(f -> f.file().contains("classic_weathering")),
                "expected the weathering list to be swept -- it is the file #13 was raised about");
    }

    // ---------- resolution ----------

    private static boolean exists(ResourceLocation id) {
        if ("minecraft".equals(id.getNamespace())) {
            return BuiltInRegistries.BLOCK.containsKey(id);
        }
        return ShippedBlockIdsTest.class.getResource(
                String.format(BLOCKSTATE_DIR, id.getNamespace(), id.getPath())) != null;
    }

    private static boolean hasBlockstates(String namespace) {
        // Any one known-good id proves the assets are on the classpath; the sweep itself will have
        // reported the individual misses.
        return ShippedBlockIdsTest.class.getResource("/assets/" + namespace) != null;
    }

    /** Nothing clever: the closest few real ids by edit distance, which is enough to spot a typo. */
    private static String suggestion(ResourceLocation id) {
        List<String> candidates = "minecraft".equals(id.getNamespace())
                ? BuiltInRegistries.BLOCK.keySet().stream().map(ResourceLocation::getPath).toList()
                : blockstateNames(id.getNamespace());
        List<String> nearest = candidates.stream()
                .sorted(Comparator.comparingInt(candidate -> distance(id.getPath(), candidate)))
                .limit(3)
                .toList();
        return nearest.isEmpty() ? "(no such block)" : "(no such block; did you mean " + nearest + "?)";
    }

    private static List<String> blockstateNames(String namespace) {
        URL url = ShippedBlockIdsTest.class.getResource("/assets/" + namespace + "/blockstates");
        if (url == null) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(Paths.get(url.toURI()))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .toList();
        } catch (IOException | URISyntaxException | RuntimeException unreadable) {
            return List.of();
        }
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    // ---------- walking the shipped files ----------

    /**
     * Directories whose files hold no block ids at all and are verified by a different sweep.
     *
     * <p>A whole-directory skip rather than new {@link #NON_BLOCK_KEYS} entries, deliberately.
     * {@code mob_sets} files key their entity ids under a bare {@code "id"}, and classifying
     * {@code "id"} as never-a-block would blunt the sweep everywhere &mdash; the teeth of this test
     * are precisely that an unclassified key fails. Excluding one directory with a named reason
     * costs one line and leaves the classifier sharp. {@code ShippedMobSetsTest} covers what is in
     * here.</p>
     */
    private static final Set<String> SWEPT_ELSEWHERE = Set.of("mob_sets");

    private static List<Found> sweep() {
        List<Found> found = new ArrayList<>();
        for (Path file : jsonFilesUnder(DATA_ROOT)) {
            Path parent = file.getParent();
            if (parent != null && SWEPT_ELSEWHERE.contains(parent.getFileName().toString())) {
                continue;
            }
            collect(parse(file), null, file.getFileName().toString(), found);
        }
        return found;
    }

    private static void collect(JsonElement element, String key, String file, List<Found> out) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                collect(entry.getValue(), entry.getKey(), file, out);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                // An array's items belong to the array's own key -- "blocks": [ ... ].
                collect(item, key, file, out);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (key != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                out.add(new Found(file, key, value));
            }
        }
    }

    private static JsonElement parse(Path file) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            // Lenient, because the shipped weathering list is authored with // comments.
            return JsonParser.parseReader(reader);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static List<Path> jsonFilesUnder(String resourceDir) {
        URL url = ShippedBlockIdsTest.class.getResource(resourceDir);
        if (url == null) {
            return fail("no shipped data at " + resourceDir);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + resourceDir + ": " + unreadable);
        }
    }
}
