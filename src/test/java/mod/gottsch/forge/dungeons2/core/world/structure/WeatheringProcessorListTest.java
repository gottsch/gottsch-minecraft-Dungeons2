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
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingProcessor;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.DecorationSweepProcessor;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.ChestMarkerProcessor;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.PotMarkerProcessor;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.SpawnerMarkerProcessor;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.DecorationProcessor;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** These decodes never re-serialize, so the processor type is never asked for. */
    private static final java.util.function.Supplier<
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?>> NO_TYPE = () -> null;

    private static final String RESOURCE =
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json";

    /** The aging processor's dispatch key as authored in the JSON. */
    private static final String AGING_TYPE = "dungeons2:aging";

    /** The decoration processor's dispatch key as authored in the JSON. */
    private static final String DECORATION_TYPE = "dungeons2:decoration";

    /** The spawner-marker processor's dispatch key as authored in the JSON (#10). */
    private static final String SPAWNER_TYPE = "dungeons2:spawner";

    /** The decoration sweep's dispatch key as authored in the JSON. */
    private static final String SWEEP_TYPE = "dungeons2:decoration_sweep";

    /** The support sweep's dispatch key as authored in the JSON. */
    private static final String SUPPORT_TYPE = "dungeons2:support_sweep";
    private static final String POT_TYPE = "dungeons2:pot";
    private static final String CHEST_TYPE = "dungeons2:chest";

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
    void everyProcessorInTheShippedListDecodes() {
        // Catches a typo'd predicate_type or block id, which would otherwise only show
        // up as "no dungeon is ever decorated" at runtime.
        //
        // Each processor is decoded with its own codec rather than through
        // StructureProcessorType.DIRECT_CODEC, because that dispatches on
        // BuiltInRegistries.STRUCTURE_PROCESSOR -- which Forge only populates at
        // runtime, and which Bootstrap.bootStrap() freezes, so a test cannot inject
        // dungeons2:aging into it. Decoding the bodies directly validates the same
        // content; processorTypeMatchesTheRegisteredName covers the dispatch key.
        JsonArray processors = readJson().getAsJsonArray("processors");
        assertEquals(7, processors.size(),
                "Expected the vanilla rule processor, aging, decoration, the decoration sweep,"
                        + " the #10 spawner marker, the #56 pot marker and the #61 chest marker");

        for (var element : processors) {
            JsonObject processor = element.getAsJsonObject();
            String type = processor.get("processor_type").getAsString();
            Codec<? extends StructureProcessor> codec = switch (type) {
                case "minecraft:rule" -> RuleProcessor.CODEC;
                case AGING_TYPE -> AgingProcessor.codec(NO_TYPE);
                case DECORATION_TYPE -> DecorationProcessor.codec(NO_TYPE);
                case SPAWNER_TYPE -> SpawnerMarkerProcessor.codec(NO_TYPE);
                case SWEEP_TYPE -> DecorationSweepProcessor.codec(NO_TYPE);
                case POT_TYPE -> PotMarkerProcessor.codec(NO_TYPE);
                case CHEST_TYPE -> ChestMarkerProcessor.codec(NO_TYPE);
                default -> throw new AssertionError("Unhandled processor_type " + type);
            };
            codec.parse(JsonOps.INSTANCE, processor).getOrThrow(false, msg -> {
                throw new AssertionError(type + " failed to decode: " + msg);
            });
        }
    }

    @Test
    void processorTypeMatchesTheRegisteredName() {
        // The JSON's dispatch key and the name Registration registers must agree, or
        // the list silently fails to load in game with nothing in the test suite to say so.
        // Both processors live in GottschCore, which registers no type of its own, so the
        // name is ours -- Registration holds it as a constant precisely so this can compare
        // it against the JSON instead of two independent string literals.
        assertEquals("dungeons2:" + Registration.AGING_PROCESSOR_NAME, AGING_TYPE);
        assertEquals("dungeons2:" + Registration.DECORATION_PROCESSOR_NAME, DECORATION_TYPE);
        assertEquals("dungeons2:" + Registration.DECORATION_SWEEP_PROCESSOR_NAME, SWEEP_TYPE);

        Set<String> used = new java.util.LinkedHashSet<>();
        readJson().getAsJsonArray("processors")
                .forEach(e -> used.add(e.getAsJsonObject().get("processor_type").getAsString()));
        assertTrue(used.contains(AGING_TYPE), "Shipped list should use the aging processor");
        assertTrue(used.contains(DECORATION_TYPE), "Shipped list should use the decoration processor");
        assertTrue(used.contains(SWEEP_TYPE), "Shipped list should use the decoration sweep");
    }

    @Test
    void onlyChunkSafeProcessorsAreUsed() {
        // A procedural piece is processed once per chunk it overlaps, so every processor
        // here has to be vetted for one of PieceProcessors' two passes: either keyed on
        // the block's world position so the repeat is harmless (minecraft:rule), or
        // marked LevelIndependentProcessor so it gets the whole piece unclipped
        // (dungeons2:aging, dungeons2:decoration). Anything else -- minecraft:capped, or
        // any other unmarked processor overriding finalizeProcessing -- would land in the
        // clipped pass and decide differently in each chunk the piece spans.
        // dungeons2:spawner (#10) is marked LevelIndependentProcessor like the other two, and in
        // practice cannot fire on a procedural piece at all -- it matches structure blocks, which
        // only an authored template contains.
        //
        // dungeons2:decoration_sweep is the ONE entry here that is deliberately NOT marked, and it
        // is vetted by a third route: it reads the level, so it must land in the clipped pass, and
        // it is written so that clipping can only make it do LESS. A cell it cannot see counts as
        // supported, so a piece spanning two chunks repairs on the pass that can see both sides and
        // does nothing on the pass that cannot -- never something different in each. It also cannot
        // meaningfully fire on a procedural piece: it only repairs damage done by a piece rendered
        // AFTER the one that decorated, and the standing rule (#18) is that procedural pieces
        // render first.
        //
        // dungeons2:pot (#56) is vetted the same way dungeons2:spawner is, and a little more
        // strongly. It is not marked LevelIndependentProcessor, so it lands in the clipped pass --
        // which is exactly where it wants to be, because clipping IS its deduplication: spawning an
        // entity is not idempotent the way writing a block is, so each chunk pass must spawn only
        // the markers inside its own box. Clipping cannot make it decide differently either, because
        // it decides nothing from the level: a marker is rolled from its own NBT and a seed derived
        // from its position, so every pass that sees a given marker rolls the same pot. Its other
        // effect, rewriting the marker cell to air, is an ordinary idempotent block write. And like
        // the spawner marker it cannot fire on a procedural piece at all -- it matches a marker
        // BLOCK, which only an authored template contains.
        //
        // dungeons2:support_sweep (2026-08-29) is vetted by the same third route as
        // decoration_sweep, and its argument is the cleaner of the two. It reads the level, so it
        // lands in the clipped pass -- and clipping PARTITIONS the block list, so every block is
        // judged by exactly one pass, the one whose box contains it. There is no "decides
        // differently in each chunk" to worry about, because no block is offered to two passes.
        // What clipping does cost is reach: a block against the seam is treated as SEEDED, since
        // the wall holding it up may be in the slice this pass cannot legally read. So the failure
        // mode is under-removal at chunk edges, never removing something a wider view would keep.
        //
        // Unlike the spawner and pot markers it CAN fire on a procedural piece, and that is fine
        // rather than merely tolerable: a procedural room's blocks are grounded by the terrain and
        // the pieces around them exactly as a prefab's are.
        //
        // dungeons2:chest (#61) is vetted exactly as dungeons2:pot is, minus the entity. It is not
        // marked LevelIndependentProcessor -- its Treasure2 branch reads the level -- so it lands
        // in the clipped pass, and clipping cannot make it decide differently: the table comes
        // either from the marker's own NBT or from a weighted draw seeded off the block's world
        // position, so every pass that sees a given marker resolves the same chest. Its write is an
        // ordinary idempotent block write, and like the other two markers it cannot fire on a
        // procedural piece at all -- it matches a marker BLOCK, which only a template contains.
        Set<String> chunkSafe = Set.of("minecraft:rule", AGING_TYPE, DECORATION_TYPE, SPAWNER_TYPE,
                SWEEP_TYPE, POT_TYPE, SUPPORT_TYPE, CHEST_TYPE);
        for (var element : readJson().getAsJsonArray("processors")) {
            String type = element.getAsJsonObject().get("processor_type").getAsString();
            assertTrue(chunkSafe.contains(type),
                    type + " is not vetted as chunk-safe for procedural pieces -- see PieceProcessors");
        }
    }

    @Test
    void theDecorationSweepIsAuthoredAfterTheDecorationProcessor() {
        // finalizeProcessing runs the list in order, and the sweep inspects what the decoration
        // pass decided. Authored before it, it would sweep against an un-decorated block list and
        // silently repair nothing -- the exact failure mode that is invisible in game.
        List<String> types = new java.util.ArrayList<>();
        readJson().getAsJsonArray("processors")
                .forEach(e -> types.add(e.getAsJsonObject().get("processor_type").getAsString()));

        assertTrue(types.indexOf(DECORATION_TYPE) < types.indexOf(SWEEP_TYPE),
                "dungeons2:decoration_sweep must be authored after dungeons2:decoration");
    }

    @Test
    void ourProcessorsAreMarkedLevelIndependent() {
        // The allowlist above is a datapack-side check; this is the code-side one it
        // relies on.
        //
        // For DecorationProcessor the marker is load-bearing: unmarked, it would move to
        // the clipped pass and start deciding from chunk slices in isolation, which is
        // exactly the seam artifact the whole split exists to prevent.
        //
        // For AgingProcessor it is about ORDER: marked, it shares a pass with decoration
        // and keeps the order the datapack authored, so decoration sees the air and dirt
        // aging produced -- as it would for a jigsaw prefab, where vanilla runs both from
        // one unsplit list.
        assertTrue(LevelIndependentProcessor.class.isAssignableFrom(DecorationProcessor.class),
                "DecorationProcessor must be a LevelIndependentProcessor or PieceProcessors"
                        + " will clip its input to the current chunk");
        assertTrue(LevelIndependentProcessor.class.isAssignableFrom(AgingProcessor.class),
                "AgingProcessor reads nothing from the level; unmarked it would run after"
                        + " decoration instead of before it");
    }

    @Test
    void theAgingProcessorIsAuthoredBeforeTheDecorationProcessor() {
        // Both are level-independent, so they run in the order this file lists them.
        // Decoration keys off air, solidity and block identity -- all things aging
        // changes -- so aging has to come first or growth decides from the un-aged room.
        java.util.List<String> types = new java.util.ArrayList<>();
        readJson().getAsJsonArray("processors")
                .forEach(e -> types.add(e.getAsJsonObject().get("processor_type").getAsString()));

        assertTrue(types.indexOf(AGING_TYPE) < types.indexOf(DECORATION_TYPE),
                "dungeons2:aging must be authored before dungeons2:decoration, got " + types);
    }

    @Test
    void shapedBlocksAreAgedRatherThanRuleMatched() {
        // The division of labour between the two processors, and the reason the aging
        // one exists: a vanilla ProcessorRule emits a fixed output_state and drops the
        // input's properties, so it cannot age stairs/slabs/walls without enumerating
        // every facing/half/shape combination. Those blocks (which the shipped prefabs
        // really do use) must therefore be handled by dungeons2:aging, never by rules.
        Set<String> shaped = Set.of(
                "minecraft:stone_brick_stairs",
                "minecraft:stone_brick_slab",
                "minecraft:stone_brick_wall");

        for (RuleEntry rule : ruleProcessorEntries()) {
            assertFalse(shaped.contains(rule.source),
                    rule.source + " is a shaped block -- aging it with minecraft:rule would"
                            + " silently reset its facing/half/shape");
        }
        assertTrue(agingSourceBlocks().containsAll(shaped),
                "Shaped blocks used by the prefabs should all have an aging rule");
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
        expected.put("dungeonblocks:rubble", 0.15);
        expected.put("minecraft:andesite", 0.30);
        // Added Aug 2026: chiseled_stone_bricks is the accent used by the speckle/cross/spokes
        // floors and the ceiling boss, and this chain converts it from "never ages" to the
        // in-family case -- it ages into a recognisable variant of itself rather than into the
        // shared rubble palette, so the pattern's silhouette survives weathering.
        expected.put("dungeonblocks:mossy_chiseled_stone_bricks", 0.20);

        Map<String, Double> actual = composedRates(ORIGINAL_TABLE_SOURCES);
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
        for (RuleEntry rule : ruleProcessorEntries()) {
            double left = remaining.getOrDefault(rule.source, 1.0);
            perSource.merge(rule.source, left * rule.probability, Double::sum);
            remaining.put(rule.source, left * (1.0 - rule.probability));
        }
        assertFalse(perSource.isEmpty(), "No rules parsed");
        perSource.forEach((source, total) -> {
            // The large stone bricks arrived from dungeons2:aging on 2026-08-05 carrying the
            // rates they already had there (44% total). The 0.30 below is the old substitution
            // table's budget for the stone-brick family and was never a claim about them.
            double budget = MIGRATED_SOURCES.contains(source) ? 0.45 : 0.30;
            assertTrue(total <= budget + EPSILON,
                    source + " weathers at " + total + ", above the intended " + budget);
        });
    }

    /** One parsed rule: which block it consumes, at what conditional probability, to what. */
    private record RuleEntry(String source, double probability, String output) {}

    /** Rules belonging to the vanilla {@code minecraft:rule} processor only. */
    private static java.util.List<RuleEntry> ruleProcessorEntries() {
        java.util.List<RuleEntry> out = new java.util.ArrayList<>();
        for (var processor : readJson().getAsJsonArray("processors")) {
            JsonObject processorObject = processor.getAsJsonObject();
            if (!"minecraft:rule".equals(processorObject.get("processor_type").getAsString())) {
                continue;
            }
            for (var element : processorObject.getAsJsonArray("rules")) {
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

    /** Source blocks the {@code dungeons2:aging} processor has a chain for. */
    private static Set<String> agingSourceBlocks() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (var processor : readJson().getAsJsonArray("processors")) {
            JsonObject processorObject = processor.getAsJsonObject();
            if (!AGING_TYPE.equals(processorObject.get("processor_type").getAsString())) {
                continue;
            }
            for (var element : processorObject.getAsJsonArray("rules")) {
                out.add(element.getAsJsonObject().get("block").getAsString());
            }
        }
        return out;
    }

    /** Absolute probability each output state is produced, composing the rule chain. */
    /**
     * Composed absolute output rates, restricted to {@code sources}.
     *
     * <p>Restricted because this processor stopped being about one block family on 2026-08-05, when
     * {@code left_/right_large_stone_brick} moved here from {@code dungeons2:aging}. Summing every
     * rule's output across all sources conflates two independent budgets -- both families produce
     * {@code cobblestone} and {@code dungeonblocks:rubble}, so the totals would no longer correspond to any
     * decision anyone made.</p>
     */
    private static Map<String, Double> composedRates(Set<String> sources) {
        Map<String, Double> rates = new LinkedHashMap<>();
        Map<String, Double> remaining = new LinkedHashMap<>();
        for (RuleEntry rule : ruleProcessorEntries()) {
            if (!sources.contains(rule.source)) {
                continue;
            }
            double left = remaining.getOrDefault(rule.source, 1.0);
            rates.merge(rule.output, left * rule.probability, Double::sum);
            remaining.put(rule.source, left * (1.0 - rule.probability));
        }
        return rates;
    }

    /**
     * The blocks this processor carried when the decorator table below was written. The large stone
     * bricks are deliberately not among them -- see {@link #composedRates}.
     */
    private static final Set<String> ORIGINAL_TABLE_SOURCES = Set.of(
            "minecraft:stone_bricks",
            "minecraft:cobblestone",
            "minecraft:polished_andesite",
            "minecraft:chiseled_stone_bricks",
            "dungeonblocks:chiseled_stone_bricks");

    /** Sources that moved here from aging and carry their own, larger budget. */
    private static final Set<String> MIGRATED_SOURCES = Set.of(
            "dungeonblocks:left_large_stone_brick",
            "dungeonblocks:right_large_stone_brick");
}
