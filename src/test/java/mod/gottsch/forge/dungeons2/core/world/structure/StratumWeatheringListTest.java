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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigHelper;
import mod.gottsch.forge.dungeons2.diagnostic.FakeWorldGenLevel;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #45 step 4 &mdash; <strong>per-stratum weathering</strong>: the depth band a piece is on
 * selects the processor list it weathers from.
 *
 * <p>Two tiers: {@code dungeons2:<motif>_<stratum>_weathering}, then
 * {@code dungeons2:<motif>_weathering}. The band's {@code name} is the selector &mdash; the same one
 * step 3 uses for room pools, deliberately, so a band carries one identifier rather than two.</p>
 *
 * <h2>Why this is worth a class of its own</h2>
 * <p>Every failure mode here is <strong>silent in game</strong>. A stratum whose list is misnamed
 * falls back to the motif's and simply looks un-aged; a stratum whose list omits the #10 spawner
 * processor leaves authored markers standing as markers; and a stratum list reaching for a processor
 * that decides across the whole block list would age each chunk of a piece differently. None of
 * those log anything.</p>
 *
 * <p>{@link WeatheringProcessorListTest} guards {@code classic_weathering.json} in depth. This class
 * guards what must hold of <strong>every</strong> shipped weathering list, swept from disk, so a
 * stratum list added later is covered the day it ships rather than the day someone remembers.</p>
 *
 * @author Mark Gottschling on Aug 24, 2026
 */
class StratumWeatheringListTest {

    private static final String PROCESSOR_ROOT = "/data/dungeons2/worldgen/processor_list";
    private static final String MOTIF = "classic";

    /** The one stratum shipped today, from {@code motif_config/classic/strata.json}. */
    private static final String STRATUM = "mud";

    private static final String AGING_TYPE = "dungeons2:aging";
    private static final String DECORATION_TYPE = "dungeons2:decoration";
    private static final String SPAWNER_TYPE = "dungeons2:spawner";

    /** See {@code WeatheringProcessorListTest#onlyChunkSafeProcessorsAreUsed} for the reasoning. */
    private static final Set<String> CHUNK_SAFE =
            Set.of("minecraft:rule", AGING_TYPE, DECORATION_TYPE, SPAWNER_TYPE);

    private static final double EPSILON = 1.0e-6;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------------------------------------------------------------------------------------
    // Tier resolution
    // ---------------------------------------------------------------------------------------

    @Test
    void theStratumListWinsOverTheMotifs() {
        FakeWorldGenLevel level = FakeWorldGenLevel.create();

        StructureProcessorList motifList =
                PieceProcessors.weatheringList(level.level(), MOTIF, Optional.empty()).orElseThrow();
        StructureProcessorList stratumList =
                PieceProcessors.weatheringList(level.level(), MOTIF, Optional.of(STRATUM)).orElseThrow();

        assertNotSame(motifList, stratumList,
                "classic_" + STRATUM + "_weathering.json must resolve ahead of classic_weathering.json;"
                        + " getting the motif's back means the stratum file is misnamed, and the only"
                        + " symptom in game is a depth that never ages");
    }

    @Test
    void aStratumWithNoListOfItsOwnFallsBackToTheMotifs() {
        // The ORDINARY case, and deliberately silent: a band usually only repaints, and #45 step 3
        // made the same call for the room-pool tier. A warning here would fire for every band that
        // is doing nothing wrong.
        FakeWorldGenLevel level = FakeWorldGenLevel.create();

        StructureProcessorList motifList =
                PieceProcessors.weatheringList(level.level(), MOTIF, Optional.empty()).orElseThrow();

        assertSame(motifList,
                PieceProcessors.weatheringList(level.level(), MOTIF, Optional.of("no_such_stratum"))
                        .orElseThrow(),
                "an unmatched stratum must degrade to the motif's list, not to nothing");
        assertSame(motifList,
                PieceProcessors.weatheringList(level.level(), MOTIF, Optional.of("  ")).orElseThrow(),
                "a blank name is not a tier");
    }

    @Test
    void thereIsNoCrossMotifStratumTier() {
        // #45 step 3's decision, applied to the same shape: stratum names are per-motif, so a
        // classic_<stratum> fallback would hand another motif someone else's idea of a depth.
        FakeWorldGenLevel level = FakeWorldGenLevel.create();

        assertTrue(PieceProcessors.weatheringList(level.level(), "no_such_motif", Optional.of(STRATUM))
                        .isEmpty(),
                "a motif with no list of its own must not borrow classic's by way of the stratum tier");
    }

    @Test
    void theShippedBandNamesTheStratumTheListIsFiledUnder() {
        // The two halves of the contract live in different files and neither mentions the other:
        // strata.json says "mud", the filename says classic_mud_weathering. This is the only place
        // they are compared, so a rename of either alone is otherwise invisible.
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        MotifConfig motif = MotifConfigHelper.get(level.level().registryAccess(), MOTIF);

        assertEquals(Optional.of(STRATUM), motif.stratumNameFor(0),
                "classic's floor-0 band should be named '" + STRATUM + "' -- that name is what"
                        + " selects classic_" + STRATUM + "_weathering.json");
        assertNotNull(readList(MOTIF + "_" + STRATUM + "_weathering"),
                "the band names a stratum with no weathering file");

        // And the floors below it inherit the motif's, because band 1 declares no name.
        assertEquals(Optional.empty(), motif.stratumNameFor(1),
                "band 1 restores the motif as authored, weathering included");
    }

    // ---------------------------------------------------------------------------------------
    // Properties every shipped weathering list must have
    // ---------------------------------------------------------------------------------------

    @Test
    void everyShippedWeatheringListIsChunkSafeAndOrdered() {
        // Swept rather than named, so a stratum list added later is covered without anyone
        // remembering to add it here. TestRegistries already proves they all DECODE -- it builds
        // the registry these tests read from -- so this asserts the two things decoding cannot:
        // that no processor decides across the whole block list, and that aging precedes
        // decoration so growth sees the dirt aging made.
        List<String> files = weatheringFiles();
        assertTrue(files.size() >= 2,
                "expected at least the motif list and the mud stratum's, found " + files);

        for (String file : files) {
            List<String> types = processorTypes(readList(file));
            for (String type : types) {
                assertTrue(CHUNK_SAFE.contains(type),
                        file + " uses " + type + ", which is not vetted as chunk-safe for a"
                                + " procedural piece -- see PieceProcessors");
            }
            if (types.contains(DECORATION_TYPE)) {
                assertTrue(types.contains(AGING_TYPE)
                                && types.indexOf(AGING_TYPE) < types.indexOf(DECORATION_TYPE),
                        file + ": dungeons2:aging must be authored before dungeons2:decoration or"
                                + " growth decides from the un-aged piece. Got " + types);
            }
        }
    }

    @Test
    void everyShippedWeatheringListCarriesTheSpawnerProcessor() {
        // The trap this closes is specific to step 4. A stratum's list REPLACES the motif's -- it
        // has to, because a pool element names exactly one processor_list -- so the #10 spawner
        // marker is not inherited. A stratum file that forgot it would leave authored
        // dungeons2:spawner_marker blocks on that depth as inert markers, with nothing logged.
        for (String file : weatheringFiles()) {
            assertTrue(processorTypes(readList(file)).contains(SPAWNER_TYPE),
                    file + " has no " + SPAWNER_TYPE + " processor. A stratum list replaces the"
                            + " motif's rather than extending it, so it must restate the processors"
                            + " that are not weathering at all -- see the file's header");
        }
    }

    @Test
    void theCopiedProcessorsStillMatchClassicVerbatim() {
        // The cost of replacement-not-delta: decoration and the spawner marker are duplicated into
        // every stratum file. Nothing but this test stops the copy drifting from its source, and a
        // drifted copy reads as "this depth's growth is tuned differently" rather than as a bug.
        //
        // If a stratum ever WANTS its own decoration -- different growth for a mud depth is a
        // perfectly good idea -- exempt that file here rather than deleting the test, so the
        // remaining copies stay honest.
        JsonObject classic = readList(MOTIF + "_weathering");
        for (String file : weatheringFiles()) {
            if (file.equals(MOTIF + "_weathering")) {
                continue;
            }
            JsonObject stratum = readList(file);
            for (String type : List.of(DECORATION_TYPE, SPAWNER_TYPE)) {
                assertEquals(processorOfType(classic, type), processorOfType(stratum, type),
                        file + "'s " + type + " has drifted from classic_weathering.json's."
                                + " It is a verbatim copy on purpose; re-copy it, or give this"
                                + " stratum its own on purpose and exempt it here");
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // The mud chain itself
    // ---------------------------------------------------------------------------------------

    @Test
    void mudBricksDecayThroughPackedMudAndDirtToAir() {
        // The one decay family the mud stratum ships, at the rates a player actually SEES: a stage
        // only rolls if the one before it hit, and a block only survives as the deepest stage
        // reached. Same convention as AgingChainRatesTest -- authored 0.3/0.3/0.15 becomes
        // 21% / 7.65% / 1.35%.
        Map<String, Double> rates = survivingRates(readList(MOTIF + "_" + STRATUM + "_weathering"),
                "minecraft:mud_bricks");

        assertEquals(0.21, rates.getOrDefault("minecraft:packed_mud", 0.0), EPSILON);
        assertEquals(0.0765, rates.getOrDefault("minecraft:dirt", 0.0), EPSILON);
        assertEquals(0.0135, rates.getOrDefault("minecraft:air", 0.0), EPSILON,
                "the air terminus is priced, not incidental -- read the block comment above the"
                        + " chain before changing it. classic refuses to reach air at all because"
                        + " wall, floor and ceiling share one block (#15), and this band shares"
                        + " its own the same way");
    }

    @Test
    void theAgingCapCanReachTheEndOfTheChain() {
        // "agings" caps how many stages of a chain may apply. A cap below the chain's length
        // truncates it silently -- the air stage would simply never appear, and the file would
        // still look exactly right.
        JsonObject aging = processorOfType(readList(MOTIF + "_" + STRATUM + "_weathering"), AGING_TYPE);
        assertNotNull(aging, "the mud list has no aging processor");

        int longest = 0;
        for (var rule : aging.getAsJsonArray("rules")) {
            longest = Math.max(longest, rule.getAsJsonObject().getAsJsonArray("output_blocks").size());
        }
        assertTrue(aging.get("agings").getAsInt() >= longest,
                "agings=" + aging.get("agings").getAsInt() + " truncates a " + longest
                        + "-stage chain; the last stages would never fire and nothing would say so");
    }

    // ---------------------------------------------------------------------------------------

    /**
     * What fraction of {@code source} blocks end up as each output &mdash; absolute, and net of the
     * next stage having fired instead.
     *
     * <p>Two levels of conditioning, both of them the file's own convention. Down a chain, stage
     * <em>i+1</em> only rolls if stage <em>i</em> hit; across chains for one source, a later chain
     * is only reached when every earlier one missed.</p>
     */
    private static Map<String, Double> survivingRates(JsonObject list, String source) {
        Map<String, Double> rates = new LinkedHashMap<>();
        double reachesThisChain = 1.0;

        for (var element : list.getAsJsonArray("processors")) {
            JsonObject processor = element.getAsJsonObject();
            if (!AGING_TYPE.equals(processor.get("processor_type").getAsString())) {
                continue;
            }
            for (var ruleElement : processor.getAsJsonArray("rules")) {
                JsonObject rule = ruleElement.getAsJsonObject();
                if (!source.equals(rule.get("block").getAsString())) {
                    continue;
                }
                JsonArray stages = rule.getAsJsonArray("output_blocks");

                // Absolute chance of REACHING each stage.
                List<Double> reached = new ArrayList<>(stages.size());
                double running = reachesThisChain;
                for (var stage : stages) {
                    running *= stage.getAsJsonObject().get("probability").getAsDouble();
                    reached.add(running);
                }
                for (int i = 0; i < stages.size(); i++) {
                    double surviving = reached.get(i) - (i + 1 < stages.size() ? reached.get(i + 1) : 0.0);
                    rates.merge(stages.get(i).getAsJsonObject().get("block").getAsString(),
                            surviving, Double::sum);
                }
                // A later chain is only tried when this one's FIRST stage missed.
                reachesThisChain -= reached.get(0);
            }
        }
        return rates;
    }

    private static List<String> processorTypes(JsonObject list) {
        List<String> types = new ArrayList<>();
        list.getAsJsonArray("processors")
                .forEach(e -> types.add(e.getAsJsonObject().get("processor_type").getAsString()));
        return types;
    }

    private static JsonObject processorOfType(JsonObject list, String type) {
        for (var element : list.getAsJsonArray("processors")) {
            JsonObject processor = element.getAsJsonObject();
            if (type.equals(processor.get("processor_type").getAsString())) {
                return processor;
            }
        }
        return null;
    }

    /** Every shipped {@code *_weathering.json} under the processor_list root, by bare id. */
    private static List<String> weatheringFiles() {
        try {
            var url = StratumWeatheringListTest.class.getResource(PROCESSOR_ROOT);
            assertNotNull(url, "Missing " + PROCESSOR_ROOT);
            try (Stream<Path> paths = Files.list(Paths.get(url.toURI()))) {
                return paths.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith("_weathering.json"))
                        .map(name -> name.substring(0, name.length() - ".json".length()))
                        .sorted()
                        .toList();
            }
        } catch (Exception e) {
            throw new AssertionError("Could not list " + PROCESSOR_ROOT, e);
        }
    }

    private static JsonObject readList(String id) {
        String resource = PROCESSOR_ROOT + "/" + id + ".json";
        try (InputStream in = StratumWeatheringListTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            // JsonParser is lenient, which is what lets these files carry the // comments they do.
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }
}
