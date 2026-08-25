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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Blocks the deep-decay chains are authored for.
     *
     * <p>{@code left_/right_large_stone_brick} are deliberately <strong>absent</strong>: as of
     * 2026-08-05 they weather through the {@code minecraft:rule} processor instead, being plain
     * full cubes with no properties for {@code AgingProcessor} to carry. Their rates are asserted by
     * {@link #largeStoneBrickKeepsItsRatesAfterTheMoveToVanillaRules} against that processor.
     *
     * <p><strong>Moving a block out of aging silently removes it from every assertion in this
     * class</strong>, which is the trap this note exists to close. Anything else that migrates needs
     * a rates test on the other side before it leaves, not after.
     */
    private static final Set<String> DEEP_DECAY_SOURCES = Set.of(
            "minecraft:stone_bricks",
            "dungeonblocks:square_stone_brick",
            "dungeonblocks:left_large_brick",
            "dungeonblocks:right_large_brick",
            "dungeonblocks:square_brick",
            "dungeonblocks:large_bricks");

    /**
     * Sources allowed to decay past {@link #DIRT_RATE}/{@link #GRAVEL_RATE}, because they are
     * <strong>wall-only</strong> blocks.
     *
     * <p>The budget the other sources are held to exists for one reason: {@code classic} uses
     * {@code minecraft:stone_bricks} for the wall, the floor <em>and</em> the ceiling, so a rule
     * cannot be scoped to the surfaces where gravel is harmless (Backlog #15). Gravel in a ceiling
     * rains debris; gravel in a wall does nothing.</p>
     *
     * <p>The large stone bricks are never a ceiling: {@code motif_config/classic/base.json} sets
     * {@code ceiling.ceiling} to plain stone bricks, and these two appear only in authored wall
     * courses. So the reason for the cap does not apply to them, and Mark's call on 2026-08-05 was
     * to let their rate stand at roughly 3.2% dirt / 1.1% gravel rather than retune it.</p>
     *
     * <p><strong>If either block is ever used as a ceiling</strong> -- a motif's
     * {@code ceiling.ceiling}, or a {@code border}/{@code coffers} ceiling pattern naming it -- take
     * it out of this set, because the exemption is about where the block lands, not about the
     * block.</p>
     */
    private static final Set<String> WALL_ONLY_SOURCES = Set.of(
            "dungeonblocks:left_large_stone_brick",
            "dungeonblocks:right_large_stone_brick");

    /** The looser bound the wall-only sources are held to: still bounded, just not ceiling-safe. */
    private static final double WALL_ONLY_MAX_DIRT = 0.05;
    private static final double WALL_ONLY_MAX_DEBRIS = 0.02;

    /** The composed rates the shipped numbers work out to. Tolerance covers 4dp rounding. */
    private static final double DIRT_RATE = 0.0180;
    private static final double RUBBLE_RATE = 0.0060;
    private static final double EPSILON = 1.0e-3;

    /**
     * Blocks that fall when the block under them is air.
     *
     * <p>The deep-decay chains ended in {@code minecraft:gravel} until 2026-08-25, and its
     * <em>position</em> as the last stage was the only control there was: a ceiling made of it
     * rains debris onto the player, and the ceiling is the same block as the wall (#15), so the
     * rate was all that could be tuned. {@code dungeonblocks:rubble} reads the same and does not
     * fall, so the constraint is GONE rather than merely respected -- which is why this set is now
     * asserted absent from the output rather than held to a budget. See
     * {@link #deepDecayNoLongerProducesAFallingBlock}.</p>
     */
    private static final Set<String> FALLING = Set.of(
            "minecraft:gravel", "minecraft:sand", "minecraft:red_sand",
            "minecraft:suspicious_gravel", "minecraft:suspicious_sand");

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
    void deepDecayProducesDirtAndRubbleAtTheDocumentedRates() {
        for (String source : DEEP_DECAY_SOURCES) {
            Map<String, Double> rates = composedRates(source);
            double dirt = rates.getOrDefault("minecraft:dirt", 0.0);
            double rubble = rates.getOrDefault("dungeonblocks:rubble", 0.0);

            if (WALL_ONLY_SOURCES.contains(source)) {
                // Bounded, not pinned -- see WALL_ONLY_SOURCES. The point is that it stays a
                // minority outcome, not that it hits a particular number.
                assertTrue(dirt <= WALL_ONLY_MAX_DIRT + EPSILON,
                        source + " dirt " + dirt + " exceeds the wall-only bound "
                                + WALL_ONLY_MAX_DIRT);
                assertTrue(rubble <= WALL_ONLY_MAX_DEBRIS + EPSILON,
                        source + " rubble " + rubble + " exceeds the wall-only bound "
                                + WALL_ONLY_MAX_DEBRIS);
                continue;
            }
            assertEquals(DIRT_RATE, dirt, EPSILON, source + " dirt rate drifted");
            assertEquals(RUBBLE_RATE, rubble, EPSILON, source + " rubble rate drifted");
        }
    }

    /**
     * The composed rates for the two blocks that moved to {@code minecraft:rule} on 2026-08-05.
     *
     * <p>The move had to be a <strong>no-op in the world</strong>, so this recomputes the rates from
     * the shipped file and checks them against what the aging chains produced before it. The
     * arithmetic is the same shape as {@link #composedRates}: rules are tried in order and each is
     * reached only when every earlier one missed. It differs in one way that matters -- a
     * {@code minecraft:rule} processor never re-reads its own output, so there are no stages to
     * compound, only alternatives. That is why the four probabilities in the file look unrelated to
     * the aging numbers they replaced.
     */
    @Test
    void largeStoneBrickKeepsItsRatesAfterTheMoveToVanillaRules() {
        for (String source : List.of("dungeonblocks:left_large_stone_brick",
                "dungeonblocks:right_large_stone_brick")) {
            Map<String, Double> rates = vanillaRuleRates(source);
            String mossy = source.replace("dungeonblocks:", "dungeonblocks:mossy_");

            assertEquals(0.300, rates.getOrDefault(mossy, 0.0), EPSILON, source + " -> mossy");
            assertEquals(0.098, rates.getOrDefault("minecraft:cobblestone", 0.0), EPSILON,
                    source + " -> cobblestone");
            assertEquals(0.0315, rates.getOrDefault("minecraft:dirt", 0.0), EPSILON,
                    source + " -> dirt");
            assertEquals(0.0105, rates.getOrDefault("dungeonblocks:rubble", 0.0), EPSILON,
                    source + " -> rubble");

            double debris = rates.getOrDefault("dungeonblocks:rubble", 0.0);
            assertTrue(debris <= WALL_ONLY_MAX_DEBRIS + EPSILON,
                    source + " rubble " + debris + " exceeds the wall-only bound");
        }
    }

    /**
     * Absolute probability of each output for {@code source} in the {@code minecraft:rule}
     * processor. Rules are tried in authored order and a probability miss falls through to the next,
     * so rule <i>k</i> fires with {@code p_k} times the product of {@code (1 - p_j)} before it.
     */
    private static Map<String, Double> vanillaRuleRates(String source) {
        JsonObject processor = null;
        for (var element : readJson().getAsJsonArray("processors")) {
            JsonObject candidate = element.getAsJsonObject();
            if ("minecraft:rule".equals(candidate.get("processor_type").getAsString())) {
                processor = candidate;
            }
        }
        assertNotNull(processor, "no minecraft:rule processor in the shipped list");

        Map<String, Double> rates = new LinkedHashMap<>();
        double remaining = 1.0;
        for (var element : processor.getAsJsonArray("rules")) {
            JsonObject input = element.getAsJsonObject().getAsJsonObject("input_predicate");
            if (!input.has("block") || !source.equals(input.get("block").getAsString())) {
                continue;
            }
            double p = input.has("probability") ? input.get("probability").getAsDouble() : 1.0;
            String out = element.getAsJsonObject().getAsJsonObject("output_state")
                    .get("Name").getAsString();
            rates.merge(out, remaining * p, Double::sum);
            remaining *= (1.0 - p);
        }
        return rates;
    }

    /**
     * The conditional-probability trap, stated as a test so it cannot be re-learned the hard way:
     * <strong>rules are tried in order and a later one only fires when the earlier ones missed</strong>,
     * so an authored probability is never the rate you get, and deleting a rule makes everything
     * below it fire MORE.
     *
     * <p>Learned twice in one day on 2026-08-05. First when the middle aging chain was deleted from
     * the large stone bricks to make them weather less: it did, but it also stopped shielding the
     * deep-decay chain, which went from being reached 0.399 of the time to 0.7 and roughly doubled
     * dirt and gravel. Then again when those blocks moved to {@code minecraft:rule}, where the same
     * arithmetic applies to a flat list of rules.</p>
     *
     * <p>Asserted against the migrated block because it is the one whose four rules are a single
     * ordered run on one source, which is exactly the shape that looks absolute and is not.</p>
     */
    @Test
    void authoredProbabilitiesAreConditionalNotAbsolute() {
        Map<String, Double> rates = vanillaRuleRates("dungeonblocks:left_large_stone_brick");

        // Authored 0.14, 0.0523, 0.0184 -- every one of them lands lower than it reads.
        assertTrue(rates.get("minecraft:cobblestone") < 0.14 - EPSILON,
                "cobblestone authored at 0.14 must compose to less: " + rates);
        assertTrue(rates.get("minecraft:dirt") < 0.0523 - EPSILON,
                "dirt authored at 0.0523 must compose to less: " + rates);
        assertTrue(rates.get("dungeonblocks:rubble") < 0.0184,
                "rubble authored at 0.0184 must compose to less: " + rates);

        // And the first rule is the one exception: nothing shields it, so it lands as authored.
        assertEquals(0.30, rates.get("dungeonblocks:mossy_left_large_stone_brick"), EPSILON,
                "the first rule in a run has nothing above it and does land at its authored rate");
    }

    /**
     * <strong>There is deliberately no test barring air from a wall block.</strong> There was one
     * until 2026-08-25, and this note stands in its place so its deletion does not read as an
     * oversight and get "restored".
     *
     * <p>The old rule: these chains apply to the wall, the floor and the ceiling alike (#15), so a
     * hole that is welcome in a floor is the same rule as a hole in an outer wall &mdash; which
     * breaches the shell and lets the terrain's water into the room. <strong>Mark accepted that
     * consequence.</strong> If it leaves a hole, it leaves a hole; a breached ruin is the look and
     * the occasional wet room is its price.
     *
     * <p>So air is now an ordinary rate to judge here, and a rate nobody has picked: the chains
     * produce no air today. Pinning that zero would be pinning an absence that is not a rule
     * &mdash; a test that fails the moment someone does the authoring it is waiting for. The one
     * air assertion left in this class is {@link #timberAgesAtTheIntendedRates}, which pins real
     * shipped numbers.
     */
    @Test
    void deepDecayReachesDirtOnEveryCommonWallBlock() {
        // What survives the ban's deletion as a real invariant: every one of these chains must
        // still pass THROUGH dirt, because dirt is what floor_growth and hanging_growth sprout
        // from and classic authors none (see theDungeonHasADirtSourceForTheGrowthBehaviours).
        // Lengthening a chain to reach air must not come at the cost of the dirt stage.
        for (String source : DEEP_DECAY_SOURCES) {
            assertTrue(composedRates(source).getOrDefault("minecraft:dirt", 0.0) > 0.0,
                    source + " no longer decays to dirt at all, so nothing can grow on it");
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
    void deepDecayNoLongerProducesAFallingBlock() {
        // This was a BUDGET -- falling debris <= 1% for anything that can be a ceiling -- because
        // gravel was the terminus and its rate was the only lever available. Since 2026-08-25 the
        // terminus is dungeonblocks:rubble, which does not fall, so the budget would now pass on
        // zero and assert nothing. A test that cannot fail is worse than no test (#46), so it
        // asserts the stronger thing the swap actually bought: these chains produce NO falling
        // block at all.
        //
        // What that protects: putting gravel or sand back into a deep-decay chain reintroduces a
        // hazard whose only mitigation -- burying it two stages deep -- has since been removed
        // from the file's rationale, so it would come back UNPRICED.
        for (String source : DEEP_DECAY_SOURCES) {
            Map<String, Double> rates = composedRates(source);
            for (String block : FALLING) {
                assertEquals(0.0, rates.getOrDefault(block, 0.0), 0.0,
                        source + " decays to " + block + ", which falls when the block below it is"
                                + " air. These chains are applied to the ceiling as well as the"
                                + " wall and the floor (#15), and rubble replaced gravel exactly"
                                + " so this could not happen -- use dungeonblocks:rubble");
            }
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

    /**
     * Backlog #37's timber chains, pinned the same way the stone budgets are.
     *
     * <p><strong>Timber is the only material here whose air is free of the #15 problem</strong>,
     * and that stayed true when the stone chains' air ban was lifted on 2026-08-25.
     * {@code classic} uses one block for wall, floor <em>and</em> ceiling, so a stone chain that
     * reaches air cannot tell a hole in a floor from a breach of the outer shell &mdash; Mark
     * accepted that. {@code spruce_log} and the spruce corbel are used for beams and brackets and
     * nothing else, and a joist hangs <em>below</em> the ceiling slab rather than forming it, so
     * decaying one leaves a gap in the run with the shell intact: nothing is being traded at all.
     * That is about where the block lands, not about timber being special; if a motif ever puts
     * {@code spruce_log} in a wall or ceiling, these rates become a compromise like any other.</p>
     */
    @Test
    void timberAgesAtTheIntendedRates() {
        // Single stage each since 2026-08-14, so the rate IS the outcome -- see
        // timberNeverWeathersToStrippedWood for why the intermediate stage went.
        Map<String, Double> beam = composedRates("minecraft:spruce_log");
        assertEquals(0.15, beam.getOrDefault("minecraft:air", 0.0), EPSILON,
                "the beam's break-through rate drifted");

        Map<String, Double> bracket = composedRates("dungeonblocks:spruce_corbel_block");
        assertEquals(0.1, bracket.getOrDefault("minecraft:air", 0.0), EPSILON,
                "the bracket's removal rate drifted -- deliberately below the beam's, because a"
                        + " bracket is one cell and reads as missing where a beam reads as broken");
    }

    /**
     * Stripped spruce is a clean, pale, evenly planed block. A stripped beam beside an untouched
     * one reads as two <em>different materials an author chose</em>, not as one material that
     * weathered &mdash; walked in game 2026-08-14 and rejected. The gap reads correctly, so the
     * chains go straight to it.
     *
     * <p>This is not a rate to retune, it is a block that must not appear as an aging output at
     * all. Restoring a middle stage needs a genuinely weathered log from {@code dungeonblocks},
     * which ships none: of its 694 blocks the only wood variants are {@code stripped_*} and the
     * species families, so {@code stripped} and {@code gone} were the whole vocabulary.</p>
     */
    @Test
    void timberNeverWeathersToStrippedWood() {
        for (String source : Set.of("minecraft:spruce_log", "dungeonblocks:spruce_corbel_block")) {
            for (String output : composedRates(source).keySet()) {
                assertFalse(output.contains("stripped"),
                        source + " ages to " + output + ". Stripped wood reads as a different"
                                + " material rather than as an aged one -- see the TIMBER block in"
                                + " classic_weathering.json.");
            }
        }
    }

    /**
     * The functional timber must not be aged at all. A ladder with a hole in it is not decoration
     * &mdash; the entrance descent is built from them, and the shaft is the only way down.
     */
    @Test
    void functionalTimberIsNotAged() {
        for (String functional : Set.of("minecraft:ladder", "minecraft:barrel",
                "dungeonblocks:spruce_dungeon_door", "dungeonblocks:spruce_dungeon_door_3",
                "dungeonblocks:dark_oak_dungeon_door")) {
            assertTrue(composedRates(functional).isEmpty(),
                    functional + " has an aging rule. Ladders carry the entrance descent, and"
                            + " doors already have a 'sometimes absent' feature in the motif's door"
                            + " probability -- ageing one to air double-counts it. See the TIMBER"
                            + " block in classic_weathering.json.");
        }
    }

    /**
     * The stripped variants must not become aging sources. They appear in eight authored
     * templates where the author chose them; a standalone rule would age that content too. #26 is
     * the same trap seen from the other end &mdash; three rules believed dead were live precisely
     * because prefab palettes author their inputs.
     *
     * <p>They are no longer produced as a stage either, which is a separate assertion &mdash; see
     * {@link #timberNeverWeathersToStrippedWood}.</p>
     */
    @Test
    void strippedTimberIsNeverAnAgingSource() {
        for (String stage : Set.of("minecraft:stripped_spruce_log",
                "dungeonblocks:stripped_spruce_corbel_block")) {
            assertTrue(composedRates(stage).isEmpty(),
                    stage + " has become an aging source. It is authored directly in shipped"
                            + " templates, so a rule keyed on it ages the author's own choice.");
        }
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
