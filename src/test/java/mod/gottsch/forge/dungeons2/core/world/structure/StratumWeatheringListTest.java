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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final String ROOM_POOL_ROOT = "/data/dungeons2/worldgen/template_pool/rooms";
    private static final String WEATHERING = "_weathering";
    private static final String MOTIF = "classic";

    /** The one stratum shipped today, from {@code motif_config/classic/strata.json}. */
    private static final String STRATUM = "mud";

    private static final String AGING_TYPE = "dungeons2:aging";

    /**
     * The mud stratum's own, surface-scoped aging. It is a strict superset of {@link #AGING_TYPE}
     * -- same chains, plus a `surface` on each rule -- so everything asserted about ordering,
     * chunk-safety and chain rates applies to either, and the helpers below accept both.
     */
    private static final String SURFACE_AGING_TYPE = "dungeons2:surface_aging";

    /** Either aging processor: a list carries one or the other, never both. See {@link #aListNeverMixesGatedAndUngatedAging}. */
    private static final Set<String> AGING_TYPES = Set.of(AGING_TYPE, SURFACE_AGING_TYPE);
    private static final String DECORATION_TYPE = "dungeons2:decoration";
    private static final String SPAWNER_TYPE = "dungeons2:spawner";
    private static final String SWEEP_TYPE = "dungeons2:decoration_sweep";
    private static final String POT_TYPE = "dungeons2:pot";

    /** See {@code WeatheringProcessorListTest#onlyChunkSafeProcessorsAreUsed} for the reasoning. */
    private static final Set<String> CHUNK_SAFE =
            Set.of("minecraft:rule", AGING_TYPE, SURFACE_AGING_TYPE, DECORATION_TYPE, SPAWNER_TYPE,
                    SWEEP_TYPE, POT_TYPE);

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
                int aging = types.stream().filter(AGING_TYPES::contains).findFirst()
                        .map(types::indexOf).orElse(-1);
                assertTrue(aging >= 0 && aging < types.indexOf(DECORATION_TYPE),
                        file + ": an aging processor (" + AGING_TYPES + ") must be authored before"
                                + " dungeons2:decoration or growth decides from the un-aged piece."
                                + " Got " + types);

                // And the sweep after it, since it inspects what decoration decided. Every list
                // that decorates must have one, or growth on that depth keeps stranding itself on
                // whatever an authored piece re-skins a shared wall with.
                assertTrue(types.contains(SWEEP_TYPE)
                                && types.indexOf(DECORATION_TYPE) < types.indexOf(SWEEP_TYPE),
                        file + ": dungeons2:decoration_sweep must be authored after"
                                + " dungeons2:decoration. Got " + types);
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
    void everyStratumPoolElementNamesThatStratumsWeatheringList() {
        // The prefab half of step 4, and the one thing about it that is silent. A pool element
        // names exactly ONE processor_list; a mud room that named dungeons2:classic_weathering
        // would load, place, and sit factory-fresh in an aging dungeon, because classic's 110
        // rules are keyed on stone and this template is mud. Nothing logs that.
        //
        // Swept by PATH rather than named, so the next stratum pool is covered on the day it is
        // authored: any pool under template_pool/rooms/<motif>/<stratum>/ must name
        // dungeons2:<motif>_<stratum>_weathering on every element.
        List<Path> pools = stratumPools();
        assertFalse(pools.isEmpty(), "expected at least rooms/classic/mud/normal.json");

        for (Path pool : pools) {
            // .../rooms/<motif>/<stratum>/<pool>.json
            String stratum = pool.getParent().getFileName().toString();
            String motif = pool.getParent().getParent().getFileName().toString();
            String expected = "dungeons2:" + motif + "_" + stratum + WEATHERING;

            JsonObject json = parse(pool);
            for (var entry : json.getAsJsonArray("elements")) {
                JsonObject element = entry.getAsJsonObject().getAsJsonObject("element");
                assertEquals(expected, element.get("processors").getAsString(),
                        pool.getFileName() + ": element " + element.get("location").getAsString()
                                + " names the wrong processor list. A stratum pool must name its"
                                + " own stratum's, or its rooms never weather and nothing says so");

                // And the template it points at must live under the same stratum folder, or the
                // pool is quietly serving another depth's rooms.
                assertTrue(element.get("location").getAsString()
                                .startsWith("dungeons2:rooms/" + motif + "/" + stratum + "/"),
                        pool.getFileName() + ": element " + element.get("location").getAsString()
                                + " is not a " + stratum + " template");
            }
        }
    }

    @Test
    void noShippedWeatheringListPlacesAFallingBlock() {
        // The general form of AgingChainRatesTest#deepDecayNoLongerProducesAFallingBlock, which is
        // scoped to classic's deep-decay chains. This sweeps EVERY weathering list and every output
        // in it -- aging stages and minecraft:rule output_states alike -- so a stratum list cannot
        // reintroduce the hazard the rubble swap removed.
        //
        // Why it is a hazard at all: gravel and sand fall when the block below them turns to air.
        // These lists are applied to the ceiling as well as the wall and the floor, because a motif
        // uses one block for all three (#15) and a processor only ever sees the block, never the
        // surface. A falling block overhead rains debris onto the player, and the ONLY control was
        // the rate. dungeonblocks:rubble reads like gravel and does not fall, which is why it is
        // the terminus everywhere as of 2026-08-25.
        Set<String> falling = Set.of("minecraft:gravel", "minecraft:sand", "minecraft:red_sand",
                "minecraft:suspicious_gravel", "minecraft:suspicious_sand");

        for (String file : weatheringFiles()) {
            for (String output : outputBlocks(readList(file))) {
                assertFalse(falling.contains(output),
                        file + " places " + output + ", which falls when the block below it is air."
                                + " Use dungeonblocks:rubble -- it reads the same and does not fall,"
                                + " which is the whole reason it exists");
            }
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
    void mudBricksDecayThroughPackedMudAndDirtToAir_aboveTheFloor() {
        // The wall/ceiling chain, at the rates a player actually SEES: a stage only rolls if the
        // one before it hit, and a block only survives as the deepest stage reached. Same
        // convention as AgingChainRatesTest -- authored 0.3/0.3/0.15 becomes 21% / 7.65% / 1.35%.
        //
        // SCOPED TO `above_floor` SINCE 2026-08-26. It used to apply everywhere, which is what made
        // the air terminus awkward: the same rule that puts a welcome hole in a wall put one in the
        // floor. The floor has its own, shallower chains now (see below), so this one can be about
        // walls alone.
        Map<String, Double> rates = survivingRates(readList(MOTIF + "_" + STRATUM + "_weathering"),
                "minecraft:mud_bricks", "above_floor");

        assertEquals(0.21, rates.getOrDefault("minecraft:packed_mud", 0.0), EPSILON);
        assertEquals(0.0765, rates.getOrDefault("minecraft:dirt", 0.0), EPSILON);
        assertEquals(0.0135, rates.getOrDefault("minecraft:air", 0.0), EPSILON,
                "the air terminus is deliberate. A hole in an OUTER wall still lets the terrain"
                        + " behind it in, water included, and Mark accepted that on 2026-08-25:"
                        + " if it leaves a hole, it leaves a hole. This is a taste number, so"
                        + " move it freely; it is pinned only so the move is deliberate");
    }

    @Test
    void theFloorWearsOnItsOwnSchedule() {
        JsonObject list = readList(MOTIF + "_" + STRATUM + "_weathering");

        // Cobble paving frets rather than dissolves: mossy first, then breaks up. Authored
        // 0.3/0.25/0.25 -> 22.5% / 5.6% / 1.9%.
        Map<String, Double> cobble = survivingRates(list, "minecraft:cobblestone", "floor");
        assertEquals(0.225, cobble.getOrDefault("minecraft:mossy_cobblestone", 0.0), EPSILON);
        assertEquals(0.05625, cobble.getOrDefault("dungeonblocks:rubble", 0.0), EPSILON);
        assertEquals(0.01875, cobble.getOrDefault("minecraft:dirt", 0.0), EPSILON);

        // NO AIR ANYWHERE ON THE FLOOR, and that is the point of the whole surface gate. On a wall
        // a hole is the look; underfoot it opens onto whatever terrain the dungeon was carved from.
        for (String source : new String[] {"minecraft:cobblestone", "minecraft:packed_mud",
                "minecraft:mud_bricks"}) {
            assertEquals(0.0, survivingRates(list, source, "floor")
                            .getOrDefault("minecraft:air", 0.0), EPSILON,
                    source + " decays to air on the FLOOR. That is a hole into raw terrain, which"
                            + " is a different and much less interesting thing than the wall holes"
                            + " -- keep the floor chains stopping at dirt");
        }
    }

    /**
     * <strong>The exclusivity the surface gate depends on.</strong> A processor list is chained, so
     * a surface-gated processor sitting beside an ungated one is additive, not exclusive: the
     * ungated rules would still run over the floor and a cell could decay twice on two schedules.
     * The gates only partition the piece if every rule in a file carries one.
     *
     * <p>Invisible if it regresses -- a doubly-aged floor looks like a floor with a slightly wrong
     * rate -- which is exactly why it is pinned rather than left to the file's own comment.</p>
     */
    @Test
    void aListNeverMixesGatedAndUngatedAging() {
        for (String file : weatheringFiles()) {
            List<String> types = processorTypes(readList(file));
            assertFalse(types.contains(AGING_TYPE) && types.contains(SURFACE_AGING_TYPE),
                    file + " carries BOTH dungeons2:aging and dungeons2:surface_aging. The"
                            + " ungated rules would still run over the surfaces the gated ones"
                            + " claim, so a cell can decay twice. Move every rule into"
                            + " surface_aging and give each one a surface.");
        }
    }

    /** Every rule in a surface_aging processor must state its surface, or the partition has a hole. */
    @Test
    void everySurfaceAgingRuleStatesItsSurface() {
        for (String file : weatheringFiles()) {
            JsonObject processor = processorOfType(readList(file), SURFACE_AGING_TYPE);
            if (processor == null) {
                continue;
            }
            for (var ruleElement : processor.getAsJsonArray("rules")) {
                JsonObject rule = ruleElement.getAsJsonObject();
                assertTrue(rule.has("surface"),
                        file + ": rule for " + rule.get("block").getAsString() + " states no"
                                + " surface, so it defaults to `any` and decays every surface --"
                                + " which silently un-partitions the ones that DO state one");
            }
        }
    }

    @Test
    void theAgingCapCanReachTheEndOfTheChain() {
        // "agings" caps how many stages of a chain may apply. A cap below the chain's length
        // truncates it silently -- the air stage would simply never appear, and the file would
        // still look exactly right.
        JsonObject list = readList(MOTIF + "_" + STRATUM + "_weathering");
        JsonObject aging = processorOfType(list, SURFACE_AGING_TYPE);
        if (aging == null) {
            aging = processorOfType(list, AGING_TYPE);
        }
        assertNotNull(aging, "the mud list has no aging processor of either type");

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
        return survivingRates(list, source, null);
    }

    /**
     * As above, restricted to the rules whose {@code surface} is {@code surface}. Pass {@code null}
     * to take every rule for {@code source} regardless -- which is what a {@code dungeons2:aging}
     * list wants, since none of its rules carry one.
     */
    private static Map<String, Double> survivingRates(JsonObject list, String source, String surface) {
        Map<String, Double> rates = new LinkedHashMap<>();
        double reachesThisChain = 1.0;

        for (var element : list.getAsJsonArray("processors")) {
            JsonObject processor = element.getAsJsonObject();
            if (!AGING_TYPES.contains(processor.get("processor_type").getAsString())) {
                continue;
            }
            for (var ruleElement : processor.getAsJsonArray("rules")) {
                JsonObject rule = ruleElement.getAsJsonObject();
                if (!source.equals(rule.get("block").getAsString())) {
                    continue;
                }
                // A rule with no `surface` is `any`, which every surface matches.
                if (surface != null && rule.has("surface")
                        && !surface.equals(rule.get("surface").getAsString())) {
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

    /**
     * Every block id a list can WRITE: an aging chain's stages and a {@code minecraft:rule}'s
     * {@code output_state}. Deliberately not the inputs &mdash; a rule keyed on gravel would be
     * harmless, it is placing one that is not.
     */
    private static Set<String> outputBlocks(JsonObject list) {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (var element : list.getAsJsonArray("processors")) {
            JsonObject processor = element.getAsJsonObject();
            String type = processor.get("processor_type").getAsString();
            if (AGING_TYPE.equals(type)) {
                for (var ruleElement : processor.getAsJsonArray("rules")) {
                    for (var stage : ruleElement.getAsJsonObject().getAsJsonArray("output_blocks")) {
                        out.add(stage.getAsJsonObject().get("block").getAsString());
                    }
                }
            } else if ("minecraft:rule".equals(type)) {
                for (var ruleElement : processor.getAsJsonArray("rules")) {
                    JsonObject state = ruleElement.getAsJsonObject().getAsJsonObject("output_state");
                    if (state != null && state.has("Name")) {
                        out.add(state.get("Name").getAsString());
                    }
                }
            }
        }
        return out;
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

    /**
     * Every room pool that sits one level deeper than {@code rooms/<motif>/}, i.e. a per-stratum
     * pool. {@code rooms/classic/normal.json} is the motif's own and is deliberately excluded.
     */
    private static List<Path> stratumPools() {
        try {
            var url = StratumWeatheringListTest.class.getResource(ROOM_POOL_ROOT);
            assertNotNull(url, "Missing " + ROOM_POOL_ROOT);
            Path root = Paths.get(url.toURI());
            try (Stream<Path> paths = Files.walk(root)) {
                return paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        // root/<motif>/<stratum>/<pool>.json -> 3 names below the root
                        .filter(path -> root.relativize(path).getNameCount() == 3)
                        .sorted()
                        .toList();
            }
        } catch (Exception e) {
            throw new AssertionError("Could not walk " + ROOM_POOL_ROOT, e);
        }
    }

    private static JsonObject parse(Path file) {
        try (var in = Files.newInputStream(file)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("Could not read " + file, e);
        }
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
