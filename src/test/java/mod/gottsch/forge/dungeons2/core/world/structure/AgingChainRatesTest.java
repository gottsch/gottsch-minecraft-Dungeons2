/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the <strong>deep decay</strong> chains in {@code classic_weathering.json} &mdash; the
 * ones that take a plain wall block all the way down to {@code gravel -> dirt -> air}.
 *
 * <p>Worth its own test class for two reasons.</p>
 *
 * <p><strong>The numbers are conditional and have been got wrong before.</strong> A chain's
 * later stages only roll if the earlier ones hit, and a whole chain only rolls if the earlier
 * chains for the same source block missed. Every block here already carries two chains at 0.3
 * and 0.43, so a third one is reached just 0.7 &times; 0.57 = 39.9% of the time and its
 * authored numbers mean nothing until composed. Nothing about a mis-tuned file fails at load
 * or looks obviously wrong in game.</p>
 *
 * <p><strong>Three decoration behaviours depend on these chains existing at all.</strong>
 * Classic dungeons author no dirt and no holes, so the aging processor is the only source of
 * either. Without a dirt output, {@code floor_growth} and {@code hanging_growth} are dead
 * code; without an air output, {@code unsupported} can never fire. That linkage is invisible
 * &mdash; both sides look perfectly healthy on their own &mdash; so it is asserted here.</p>
 *
 * @author Mark Gottschling on Jul 28, 2026
 */
class AgingChainRatesTest {

    private static final String RESOURCE =
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json";

    private static final String AGING_TYPE = "dungeons2:aging";

    /** Blocks the deep-decay chains are authored for. */
    private static final Set<String> DEEP_DECAY_SOURCES = Set.of(
            "minecraft:stone_bricks",
            "dungeonblocks:left_large_stone_brick",
            "dungeonblocks:right_large_stone_brick",
            "dungeonblocks:square_stone_brick",
            "dungeonblocks:left_large_brick",
            "dungeonblocks:right_large_brick",
            "dungeonblocks:square_brick",
            "dungeonblocks:large_bricks");

    /** The composed rates the shipped numbers work out to. Tolerance covers 4dp rounding. */
    private static final double DIRT_RATE = 0.0180;
    private static final double GRAVEL_RATE = 0.0060;
    private static final double EPSILON = 1.0e-3;

    /**
     * Ceiling gravel falls onto the floor as debris, and the ceiling is the same block as the
     * wall, so gravel cannot be scoped to the surfaces where it is harmless. It is kept as the
     * chain's LAST stage instead, which is what holds it to this rate.
     */
    private static final double MAX_FALLING_RATE = 0.01;

    private static JsonObject readJson() {
        try (InputStream in = AgingChainRatesTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "Missing shipped resource " + RESOURCE);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("Could not read " + RESOURCE, e);
        }
    }

    private static JsonObject agingProcessor() {
        for (var element : readJson().getAsJsonArray("processors")) {
            JsonObject processor = element.getAsJsonObject();
            if (AGING_TYPE.equals(processor.get("processor_type").getAsString())) {
                return processor;
            }
        }
        throw new AssertionError("No " + AGING_TYPE + " processor in the shipped list");
    }

    /**
     * Absolute probability of each final output block for {@code source}, composing the whole
     * rule list exactly as {@code AgingProcessor} walks it: chains in authored order, each
     * reached only if the previous ones missed; within a chain, stage <i>k</i> reached only if
     * <i>k-1</i> was, capped at {@code agings} stages; the deepest stage reached is the result.
     */
    private static Map<String, Double> composedRates(String source) {
        JsonObject processor = agingProcessor();
        int agings = processor.get("agings").getAsInt();

        Map<String, Double> rates = new LinkedHashMap<>();
        double remaining = 1.0;

        for (var element : processor.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (!source.equals(rule.get("block").getAsString())) {
                continue;
            }
            JsonArray stages = rule.getAsJsonArray("output_blocks");
            int depth = Math.min(agings, stages.size());

            // reach[k] = P(this block reaches stage k of this chain), unconditionally.
            double reach = remaining;
            double[] reached = new double[depth];
            for (int k = 0; k < depth; k++) {
                reach *= stages.get(k).getAsJsonObject().get("probability").getAsDouble();
                reached[k] = reach;
            }
            // Landing ON stage k means reaching it but not the next one.
            for (int k = 0; k < depth; k++) {
                double landed = reached[k] - (k + 1 < depth ? reached[k + 1] : 0.0);
                rates.merge(stages.get(k).getAsJsonObject().get("block").getAsString(),
                        landed, Double::sum);
            }
            remaining *= (1.0 - stages.get(0).getAsJsonObject().get("probability").getAsDouble());
        }
        return rates;
    }

    @Test
    void agingsIsDeepEnoughForEveryChainItCarries() {
        // A chain applies min(agings, stages) stages, so a cap below the longest chain
        // silently makes that chain's last stage unreachable -- no error, it just never
        // happens.
        int agings = agingProcessor().get("agings").getAsInt();
        for (var element : agingProcessor().getAsJsonArray("rules")) {
            JsonArray stages = element.getAsJsonObject().getAsJsonArray("output_blocks");
            assertTrue(agings >= stages.size(),
                    "agings=" + agings + " cannot reach stage " + stages.size() + " of "
                            + element.getAsJsonObject().get("block").getAsString());
        }
    }

    @Test
    void deepDecayProducesDirtAndGravelAtTheDocumentedRates() {
        for (String source : DEEP_DECAY_SOURCES) {
            Map<String, Double> rates = composedRates(source);
            assertEquals(DIRT_RATE, rates.getOrDefault("minecraft:dirt", 0.0), EPSILON,
                    source + " dirt rate drifted");
            assertEquals(GRAVEL_RATE, rates.getOrDefault("minecraft:gravel", 0.0), EPSILON,
                    source + " gravel rate drifted");
        }
    }

    @Test
    void noWallBlockDecaysToAir() {
        // A hole in a FLOOR is fine. A hole in an outer WALL breaches the dungeon shell, and
        // any water in the terrain behind it flows into the room -- which placing the room's
        // air cannot prevent, because the room is filled once at generation and the water
        // arrives afterwards.
        //
        // These blocks are the wall, the floor AND the ceiling (classic.json uses
        // minecraft:stone_bricks for all three), so there is no way to allow the safe case
        // without also allowing the breach. Air comes back only once the surfaces use
        // distinct blocks.
        for (String source : DEEP_DECAY_SOURCES) {
            assertEquals(0.0, composedRates(source).getOrDefault("minecraft:air", 0.0), 0.0,
                    source + " is a wall block and must not decay to air -- see the comment"
                            + " above the deep-decay chains in classic_weathering.json");
        }
    }

    /**
     * Stairs weather into <strong>dirt</strong> as well as into other stairs, and that is
     * deliberate &mdash; do not "fix" it.
     *
     * <p>It looks like a defect from the code: it is the only rule in the file that changes a
     * block's <em>shape</em> rather than its material, and at 2.5% it is commoner than the
     * deep-decay chain's own dirt (1.8%). I removed it on 2026-08-04 on exactly that reasoning and
     * was wrong. Gottsch wants it: an arch haunch gone to earth reads as the stair having fallen
     * out and the ground coming through, and dirt is the <em>only</em> thing {@code floor_growth}
     * and {@code hanging_growth} can sprout from &mdash; so removing it also silently took away
     * every ceiling-growth anchor in the dungeon. See
     * {@link #theDungeonHasADirtSourceForTheGrowthBehaviours}, which is the same linkage seen from
     * the other end.</p>
     *
     * <p>Kept as a rate assertion rather than deleted outright, so a change to the stairs chains is
     * at least visible in a diff.</p>
     *
     * <p><strong>Only {@code minecraft:stone_brick_stairs} is asserted</strong> because it is the
     * only stairs block classic authors (the corridor arch haunch and the projecting wall-trim
     * course are both it). The file's other {@code *_stairs} rules are keyed on blocks nothing
     * authors: they read as a continuing chain &mdash; {@code stone_brick_stairs ->
     * cobblestone_stairs -> andesite_stairs -> gravel} &mdash; but {@code AgingProcessor} looks its
     * rules up by the <em>input</em> block once and never re-resolves what it just produced, so a
     * rule keyed on an intermediate never fires. Those rules are unreachable, not tested.</p>
     */
    @Test
    void stairsDecayToDirtAtTheIntendedRate() {
        assertEquals(0.025, composedRates("minecraft:stone_brick_stairs")
                        .getOrDefault("minecraft:dirt", 0.0), EPSILON,
                "the stairs-to-dirt rate drifted -- it is deliberate, see this test's comment");
    }

    @Test
    void fallingDebrisStaysRare() {
        // Gravel and sand fall when the block under them is air. Harmless in a wall or a
        // floor, but a ceiling made of them rains debris onto the floor -- and the ceiling is
        // the same block as the wall, so the only control available is the RATE.
        //
        // Which in turn means the rate is controlled by chain POSITION: gravel is the third
        // stage, so it inherits the compounding of the two before it. Promote it to the first
        // stage and it is ~9x more common, which is what the floor debris looked like.
        Set<String> falling = Set.of("minecraft:gravel", "minecraft:sand",
                "minecraft:red_sand", "minecraft:suspicious_gravel", "minecraft:suspicious_sand");
        for (String source : DEEP_DECAY_SOURCES) {
            double total = composedRates(source).entrySet().stream()
                    .filter(entry -> falling.contains(entry.getKey()))
                    .mapToDouble(Map.Entry::getValue).sum();
            assertTrue(total <= MAX_FALLING_RATE + EPSILON,
                    source + " decays to falling blocks " + total + " of the time -- above the "
                            + MAX_FALLING_RATE + " ceiling-debris budget");
        }
    }

    @Test
    void theDungeonHasADirtSourceForTheGrowthBehaviours() {
        // The linkage that was missing until now, and the reason floor/hanging growth were
        // invisible in game: classic authors no dirt anywhere, so if aging stops producing
        // it, both behaviours quietly do nothing while every one of their own tests passes.
        Set<String> dirtProducers = new LinkedHashSet<>();
        for (var element : agingProcessor().getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            String source = rule.get("block").getAsString();
            if (composedRates(source).getOrDefault("minecraft:dirt", 0.0) > 0.0) {
                dirtProducers.add(source);
            }
        }

        assertTrue(dirtProducers.size() >= DEEP_DECAY_SOURCES.size(),
                "Expected every common wall block to decay to dirt eventually, got " + dirtProducers);
    }

    @Test
    void aWallBlockIsNotWeatheredAwayEntirely() {
        // Sanity on the other side: these chains stack on top of two cosmetic ones, so it
        // would be easy to push a wall block's total transformation towards 100% and leave a
        // room with no original stonework in it at all.
        for (String source : DEEP_DECAY_SOURCES) {
            double transformed = composedRates(source).values().stream()
                    .mapToDouble(Double::doubleValue).sum();
            assertTrue(transformed < 0.75,
                    source + " is transformed " + transformed + " of the time -- too little of"
                            + " the original wall survives");
        }
    }

}
