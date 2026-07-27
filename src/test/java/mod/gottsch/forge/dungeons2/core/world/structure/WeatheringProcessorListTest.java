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
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipped {@code dungeons2:classic_weathering} processor list &mdash; the
 * single datapack file that now decorates BOTH the procedural pieces (via
 * {@link PieceProcessors}) and the jigsaw prefabs (via the pool JSONs'
 * {@code "processors"} field).
 *
 * <p>Worth testing because both failure modes are silent in game: an invalid file
 * just fails to load and every dungeon generates undecorated, and a mis-derived
 * probability produces a dungeon that looks subtly wrong with nothing in the log.</p>
 *
 * @author Mark Gottschling on Jul 26, 2026
 */
class WeatheringProcessorListTest {

    private static final String RESOURCE =
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json";

    /**
     * Tolerance on the derived rates. The JSON's per-rule probabilities are rounded
     * to 4 decimal places, which is worth ~1e-4 on the composed result.
     */
    private static final double EPSILON = 1.0e-3;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject readJson() {
        try (InputStream in = WeatheringProcessorListTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "Missing shipped resource " + RESOURCE);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("Could not read " + RESOURCE, e);
        }
    }

    @Test
    void shippedListDecodesWithTheVanillaCodec() {
        // Catches a typo'd predicate_type / block id / processor_type, which would
        // otherwise only show up as "no dungeon is ever decorated" at runtime.
        StructureProcessorList list = StructureProcessorType.DIRECT_CODEC
                .parse(JsonOps.INSTANCE, readJson())
                .getOrThrow(false, msg -> {
                    throw new AssertionError("classic_weathering.json failed to decode: " + msg);
                });

        assertEquals(1, list.list().size(), "Expected a single rule processor");
    }

    @Test
    void noWholeListProcessorsAreUsed() {
        // PieceProcessors runs per chunk-slice of a piece, so a processor that decides
        // from the whole block list (minecraft:capped and friends, via
        // finalizeProcessing) would decide differently in each chunk the piece spans.
        // Rule processors are position-keyed and safe; nothing else is vetted.
        for (var element : readJson().getAsJsonArray("processors")) {
            assertEquals("minecraft:rule",
                    element.getAsJsonObject().get("processor_type").getAsString(),
                    "Only minecraft:rule is chunk-safe for procedural pieces -- see PieceProcessors");
        }
    }

    @Test
    void composedRatesMatchTheDecoratorTableTheyReplace() {
        // A vanilla RuleProcessor has no weighted multi-variant output: one rule, one
        // output state, first match wins. Several variants of the same source block
        // are therefore expressed as consecutive rules, and because each rule draws
        // its own nextFloat() only after the previous rule missed, the authored
        // probabilities are CONDITIONAL, not absolute. This asserts the composed
        // absolute rates still equal the substitution table this list replaced:
        // stone_bricks 30% split three ways, cobblestone 30% split two ways,
        // polished_andesite 30%.
        //
        // (RandomBlockMatchTest short-circuits on the block check before drawing, so
        // rules for a different source block never perturb this composition.)
        Map<String, Double> expected = new LinkedHashMap<>();
        expected.put("minecraft:mossy_stone_bricks", 0.10);
        expected.put("minecraft:cracked_stone_bricks", 0.10);
        expected.put("minecraft:cobblestone", 0.10);
        expected.put("minecraft:mossy_cobblestone", 0.15);
        expected.put("minecraft:gravel", 0.15);
        expected.put("minecraft:andesite", 0.30);

        Map<String, Double> actual = composedRates();
        assertEquals(expected.keySet(), actual.keySet(), "Output states changed");
        expected.forEach((output, rate) -> assertEquals(rate, actual.get(output), EPSILON,
                "Composed rate for " + output + " drifted from the table it replaces"));
    }

    @Test
    void noSourceBlockIsOverWeathered() {
        // Sanity on the conditional-probability chain: if the per-rule numbers were
        // ever edited as if they were absolute, a source block's total could exceed
        // its intended 30% (or, in the limit, 100%).
        Map<String, Double> perSource = new LinkedHashMap<>();
        Map<String, Double> remaining = new LinkedHashMap<>();
        for (RuleEntry rule : rules()) {
            double left = remaining.getOrDefault(rule.source, 1.0);
            perSource.merge(rule.source, left * rule.probability, Double::sum);
            remaining.put(rule.source, left * (1.0 - rule.probability));
        }
        assertFalse(perSource.isEmpty(), "No rules parsed");
        perSource.forEach((source, total) -> assertTrue(total <= 0.30 + EPSILON,
                source + " weathers at " + total + ", above the intended 0.30"));
    }

    /** One parsed rule: which block it consumes, at what conditional probability, to what. */
    private record RuleEntry(String source, double probability, String output) {}

    private static java.util.List<RuleEntry> rules() {
        java.util.List<RuleEntry> out = new java.util.ArrayList<>();
        JsonArray processors = readJson().getAsJsonArray("processors");
        for (var processor : processors) {
            for (var element : processor.getAsJsonObject().getAsJsonArray("rules")) {
                JsonObject rule = element.getAsJsonObject();
                JsonObject input = rule.getAsJsonObject("input_predicate");
                out.add(new RuleEntry(
                        input.get("block").getAsString(),
                        input.get("probability").getAsDouble(),
                        rule.getAsJsonObject("output_state").get("Name").getAsString()));
            }
        }
        return out;
    }

    /** Absolute probability each output state is produced, composing the rule chain. */
    private static Map<String, Double> composedRates() {
        Map<String, Double> rates = new LinkedHashMap<>();
        Map<String, Double> remaining = new LinkedHashMap<>();
        for (RuleEntry rule : rules()) {
            double left = remaining.getOrDefault(rule.source, 1.0);
            rates.merge(rule.output, left * rule.probability, Double::sum);
            remaining.put(rule.source, left * (1.0 - rule.probability));
        }
        return rates;
    }
}
