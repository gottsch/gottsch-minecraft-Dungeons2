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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Backlog #45 step 4: <strong>a stratum's weathering list has to cover the blocks its band
 * places.</strong>
 *
 * <h2>The failure this exists to catch, which shipped and stood for two weeks</h2>
 * <p>{@code classic/strata.json} put floor 0 in mud brick, and {@code classic_mud_weathering.json}
 * aged the mud. What neither noticed was that floor 0's <em>arches</em> are still stone brick &mdash;
 * {@code archBlock} on a band is inert for classic, because the arch lives inside the {@code styles}
 * entry the planner rolled &mdash; and no rule in the mud list mentioned {@code stone_brick_stairs}.
 * So the one stratum that was supposed to look the most weathered rendered its arches
 * <strong>pristine</strong>, on an otherwise fully aged floor. The backlog entry called that
 * "backwards from the intent"; it was found by hand on 2026-09-06, and by then the mechanism had
 * been correct and the authoring wrong since 2026-08-23.</p>
 *
 * <p>{@code StratumWeatheringListTest} could not have caught it. That one asserts <em>resolution</em>
 * &mdash; which list a floor gets, that it is chunk-safe, that its processors are ordered. It never
 * looks at what is <em>in</em> a list. Nothing did, until this.</p>
 *
 * <h2>Why the motif's own list is the reference, and not a hand-written list of blocks</h2>
 * <p>The obvious test &mdash; "every block a band places must have an aging rule" &mdash; is wrong
 * and would fail immediately on seventeen blocks that are all correctly unaged: pots and barrels and
 * chests (containers, and pots are entities besides), braziers and glowstone (aging a light source
 * removes the light), doors (#14 kept lichen off them deliberately), dripstone and rubble (already
 * the <em>output</em> of weathering, so a rule would be a second route on one block). Curating that
 * by hand would mean a list that has to be maintained forever and that silently rots.</p>
 *
 * <p>So the reference is the <strong>motif's own weathering list</strong>. If
 * {@code classic_weathering.json} thinks a block is architecture worth aging, then a stratum of
 * classic that also places that block should age it too. The motif list already excludes every pot,
 * light and container, because the same judgement was applied when it was authored &mdash; so the
 * exclusions come for free and stay correct as the motif grows. What is left over is exactly the
 * arches.</p>
 *
 * <p>This also makes the block-id extraction below safe to keep loose. A stray namespaced string
 * that is not really a placed block can only produce a finding if the motif list <em>also</em> ages
 * it, which would make it architecture after all.</p>
 *
 * @author Mark Gottschling on Sep 6, 2026
 */
class StratumWeatheringCoverageTest {

    private static final String MOTIF_ROOT = "/data/dungeons2/dungeons2/motif_config";
    private static final String PROCESSOR_LISTS = "/data/dungeons2/worldgen/processor_list";

    /** The key a motif's strata fragment declares its depth bands under. */
    private static final String BANDS = "strata_by_floor_index";

    /** A band opts a depth into its own pools and its own weathering list under this name. */
    private static final String NAME = "name";

    private static final String SURFACE_AGING = "dungeons2:surface_aging";

    /** Only these namespaces name blocks this pack places. {@code dungeons2:} ids are mob sets. */
    private static final Pattern BLOCK_ID =
            Pattern.compile("^(?:minecraft|dungeonblocks):[a-z0-9_]+$");

    /**
     * Blocks a stratum is allowed to leave unaged even though its motif ages them, each with the
     * decision that put it here. <strong>An entry is a decision, not a suppression</strong> &mdash;
     * if a block is here because nobody got to it yet, it belongs in the failure list instead.
     */
    private static final Map<String, String> DELIBERATELY_UNAGED = Map.of(
            // #45 step 4: in the mud band the timber is SHORING, not decoration -- it is what holds
            // a wet, shallow cutting open, so it does not rot on the same curve the masonry does.
            // The motif list ages it only on the `joist` surface, which is a different element
            // entirely. Deleting this entry would ask for a rule that was refused on purpose.
            "minecraft:spruce_log", "mud band timber is shoring, not decoration (#45 step 4)");

    // ---------- the sweep ----------

    /**
     * The test. For every band that names a stratum, whatever that floor places and the motif ages,
     * the stratum ages too.
     */
    @Test
    void everyStratumAgesTheBlocksItsBandPlacesAndItsMotifAges() {
        List<String> gaps = new ArrayList<>();
        int strataChecked = 0;

        for (Path motifDir : motifDirectories()) {
            String motif = motifDir.getFileName().toString();
            Set<String> motifAged = agedBlocks(motif + "_weathering");
            if (motifAged.isEmpty()) {
                continue;   // A motif with no weathering of its own has no reference to compare to.
            }
            Set<String> motifPlaces = blocksPlacedByFragments(motifDir);

            for (JsonObject band : namedBands(motifDir)) {
                String stratum = band.get(NAME).getAsString();
                strataChecked++;

                // The floor's palette is the motif's own fragments PLUS this band's overlay -- a
                // band states only what it changes, so the base fragments are still in force.
                Set<String> places = new TreeSet<>(motifPlaces);
                places.addAll(blockIdsIn(band));

                Set<String> stratumAges = agedBlocks(motif + "_" + stratum + "_weathering");
                for (String block : places) {
                    if (motifAged.contains(block)
                            && !stratumAges.contains(block)
                            && !DELIBERATELY_UNAGED.containsKey(block)) {
                        gaps.add(motif + "/" + stratum + " places " + block
                                + ", and " + motif + "_weathering ages it, but "
                                + motif + "_" + stratum + "_weathering does not");
                    }
                }
            }
        }

        assertTrue(strataChecked > 0,
                "no named strata found under " + MOTIF_ROOT + " -- this test swept nothing, which"
                        + " reports green while checking nothing at all");

        if (!gaps.isEmpty()) {
            fail(gaps.size() + " block(s) render PRISTINE on a stratum floor while the rest of that"
                    + " floor weathers. Either author the rule in the stratum's list, or add the"
                    + " block to DELIBERATELY_UNAGED with the reason:\n  "
                    + String.join("\n  ", gaps));
        }
    }

    /**
     * The guard on the guard. An exclusion whose block is aged after all, or which nothing places any
     * more, is a decision that has quietly stopped being about anything &mdash; and it would go on
     * hiding a real gap if the block came back.
     */
    @Test
    void everyDeliberateExclusionIsStillAboutSomething() {
        Set<String> live = new LinkedHashSet<>();
        for (Path motifDir : motifDirectories()) {
            live.addAll(blocksPlacedByFragments(motifDir));
        }
        List<String> stale = new ArrayList<>();
        for (String block : DELIBERATELY_UNAGED.keySet()) {
            if (!live.contains(block)) {
                stale.add(block + " -- nothing places it any more");
            }
        }
        assertTrue(stale.isEmpty(),
                "DELIBERATELY_UNAGED has entries that no longer describe anything shipped. Remove"
                        + " them, or the next real gap on that block passes unnoticed:\n  "
                        + String.join("\n  ", stale));
    }

    /**
     * The sweep only means something if the shipped lists actually differ. If a stratum's list were
     * a copy of its motif's, every assertion above would pass while the stratum weathered as stone.
     */
    @Test
    void aStratumListIsNotJustACopyOfItsMotifs() {
        boolean compared = false;
        for (Path motifDir : motifDirectories()) {
            String motif = motifDir.getFileName().toString();
            Set<String> motifAged = agedBlocks(motif + "_weathering");
            for (JsonObject band : namedBands(motifDir)) {
                Set<String> stratumAged = agedBlocks(motif + "_" + band.get(NAME).getAsString()
                        + "_weathering");
                if (motifAged.isEmpty() || stratumAged.isEmpty()) {
                    continue;
                }
                compared = true;
                assertFalse(motifAged.equals(stratumAged),
                        motif + "/" + band.get(NAME).getAsString() + " ages exactly the blocks its"
                                + " motif does -- the stratum is not weathering as its own material");
            }
        }
        assertTrue(compared, "no stratum list to compare against its motif's");
    }

    // ---------- reading the shipped files ----------

    /** Every block a motif's non-strata fragments name. A motif is a FOLDER of merged fragments. */
    private static Set<String> blocksPlacedByFragments(Path motifDir) {
        Set<String> out = new TreeSet<>();
        for (Path file : jsonFilesUnder(motifDir)) {
            JsonElement root = parse(file);
            // The strata fragment's bands are per-depth overlays, not the base palette -- folding
            // them in here would make a block placed only on floor 3 look placed on floor 0, and
            // report a gap in a list that was right.
            if (root.isJsonObject() && root.getAsJsonObject().has(BANDS)) {
                continue;
            }
            out.addAll(blockIdsIn(root));
        }
        return out;
    }

    /** The bands that name a stratum. A band without a name takes its motif's list, so it is not one. */
    private static List<JsonObject> namedBands(Path motifDir) {
        List<JsonObject> out = new ArrayList<>();
        for (Path file : jsonFilesUnder(motifDir)) {
            JsonElement root = parse(file);
            if (!root.isJsonObject() || !root.getAsJsonObject().has(BANDS)) {
                continue;
            }
            for (JsonElement band : root.getAsJsonObject().getAsJsonArray(BANDS)) {
                if (band.isJsonObject() && band.getAsJsonObject().has(NAME)) {
                    out.add(band.getAsJsonObject());
                }
            }
        }
        return out;
    }

    /** The blocks a processor list's {@code surface_aging} rules take as INPUT, or empty if absent. */
    private static Set<String> agedBlocks(String listName) {
        URL url = StratumWeatheringCoverageTest.class
                .getResource(PROCESSOR_LISTS + "/" + listName + ".json");
        if (url == null) {
            return Set.of();
        }
        Set<String> out = new TreeSet<>();
        JsonElement root;
        try {
            root = parse(Paths.get(url.toURI()));
        } catch (URISyntaxException bad) {
            return fail("could not read " + listName + ": " + bad);
        }
        for (JsonElement processor : root.getAsJsonObject().getAsJsonArray("processors")) {
            JsonObject p = processor.getAsJsonObject();
            if (!p.has("processor_type")
                    || !SURFACE_AGING.equals(p.get("processor_type").getAsString())
                    || !p.has("rules")) {
                continue;
            }
            for (JsonElement rule : p.getAsJsonArray("rules")) {
                JsonObject r = rule.getAsJsonObject();
                if (r.has("block")) {
                    out.add(r.get("block").getAsString());
                }
            }
        }
        return out;
    }

    /** Every namespaced block id anywhere in a tree, by shape rather than by key. */
    private static Set<String> blockIdsIn(JsonElement element) {
        Set<String> out = new TreeSet<>();
        collect(element, out);
        return out;
    }

    private static void collect(JsonElement element, Set<String> out) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(e -> collect(e.getValue(), out));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> collect(item, out));
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (BLOCK_ID.matcher(value).matches()) {
                out.add(value);
            }
        }
    }

    private static List<Path> motifDirectories() {
        URL url = StratumWeatheringCoverageTest.class.getResource(MOTIF_ROOT);
        if (url == null) {
            return fail("no motif config at " + MOTIF_ROOT);
        }
        try (Stream<Path> paths = Files.list(Paths.get(url.toURI()))) {
            return paths.filter(Files::isDirectory).sorted().toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not list " + MOTIF_ROOT + ": " + unreadable);
        }
    }

    private static List<Path> jsonFilesUnder(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            return fail("could not walk " + dir + ": " + unreadable);
        }
    }

    private static JsonElement parse(Path file) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file),
                StandardCharsets.UTF_8)) {
            // Lenient, because the shipped weathering lists are authored with // comments.
            return JsonParser.parseReader(reader);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }
}
