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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import mod.gottsch.forge.dungeons2.diagnostic.FakeWorldGenLevel;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Can one {@code minecraft:rule} output feed another rule's input?
 *
 * <h2>Why this matters before restructuring the weathering file</h2>
 * <p>Raised 2026-08-05: {@code left_large_stone_brick} is a plain full cube, so it needs none of
 * {@code AgingProcessor}'s property carry-over (which exists for stairs, slabs and other directional
 * blocks) and could move to vanilla {@code minecraft:rule} entries -- on the understanding that
 * "vanilla rules output can feed the next rule's input", giving the multi-stage decay for free.</p>
 *
 * <p>That is <strong>half true</strong>, and the half that is false is the one that would bite. The
 * two cases are separated below.</p>
 *
 * @author Mark Gottschling on Aug 05, 2026
 */
class VanillaRuleChainingTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A rule that always converts {@code from} to {@code to}. */
    private static ProcessorRule always(BlockState from, BlockState to) {
        return new ProcessorRule(new BlockMatchTest(from.getBlock()),
                new BlockMatchTest(Blocks.AIR), to);
    }

    /** Runs {@code input} through the processors in order, each seeing the previous one's output. */
    private static BlockState run(BlockState input, BlockPos pos, RuleProcessor... processors) {
        FakeWorldGenLevel fake = FakeWorldGenLevel.create();
        StructureTemplate.StructureBlockInfo info =
                new StructureTemplate.StructureBlockInfo(pos, input, null);
        StructureTemplate.StructureBlockInfo current = info;
        for (RuleProcessor processor : processors) {
            current = processor.processBlock(fake.level(), BlockPos.ZERO, BlockPos.ZERO,
                    info, current, new StructurePlaceSettings());
            if (current == null) {
                return null;
            }
        }
        return current.state();
    }

    /**
     * <strong>Within ONE {@code minecraft:rule} processor, rules do NOT chain.</strong>
     *
     * <p>{@code RuleProcessor.processBlock} walks its rule list and <em>returns on the first
     * match</em>, testing every rule against the state it was handed rather than against the state a
     * previous rule produced. So authoring {@code A -> B} and {@code B -> C} as two rules in one
     * processor gives B, not C -- exactly what {@code AgingProcessor} does, and exactly the shape
     * that left six rules dormant in the shipped file (Backlog #26).</p>
     */
    @Test
    void withinOneRuleProcessorTheRulesDoNotChain() {
        RuleProcessor twoRules = new RuleProcessor(List.of(
                always(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState()),
                always(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState())));

        Map<String, Integer> outcomes = new LinkedHashMap<>();
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                outcomes.merge(String.valueOf(
                        run(Blocks.STONE_BRICKS.defaultBlockState(), new BlockPos(x, 64, z), twoRules)),
                        1, Integer::sum);
            }
        }
        System.out.println("  one processor, two rules -> " + outcomes);

        assertEquals(400, outcomes.getOrDefault(
                        String.valueOf(Blocks.MOSSY_STONE_BRICKS.defaultBlockState()), 0),
                "first match wins and returns; the second rule never sees the first's output: "
                        + outcomes);
    }

    /**
     * <strong>Across TWO processors in the list, they DO chain.</strong>
     *
     * <p>{@code PieceProcessors} feeds each processor the previous one's output, so a second
     * {@code minecraft:rule} entry in the {@code processors} array does see what the first
     * produced. This is the mechanism that makes the idea work -- it is just one stage per
     * <em>processor</em>, not one per rule.</p>
     */
    @Test
    void acrossTwoRuleProcessorsTheyDoChain() {
        RuleProcessor first = new RuleProcessor(List.of(
                always(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState())));
        RuleProcessor second = new RuleProcessor(List.of(
                always(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState())));

        Map<String, Integer> outcomes = new LinkedHashMap<>();
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                outcomes.merge(String.valueOf(
                        run(Blocks.STONE_BRICKS.defaultBlockState(), new BlockPos(x, 64, z), first, second)),
                        1, Integer::sum);
            }
        }
        System.out.println("  two processors, one rule each -> " + outcomes);

        assertEquals(400, outcomes.getOrDefault(
                        String.valueOf(Blocks.COBBLESTONE.defaultBlockState()), 0),
                "the second processor should see the first's output: " + outcomes);
    }

    /**
     * A rule whose <em>probability</em> misses falls through to the next rule in the same processor.
     *
     * <p>This is what makes several rules in one processor the equivalent of
     * {@code AgingProcessor}'s several chains keyed on one block: alternatives, tried in order, each
     * reached only when the earlier ones did not fire. Combined with the two tests above, the
     * mapping for a rewrite is exact:</p>
     *
     * <ul>
     *   <li>{@code AgingProcessor} <em>chains</em> (several rules on one block) &rarr; several rules
     *       in <strong>one</strong> {@code minecraft:rule} processor.</li>
     *   <li>{@code AgingProcessor} <em>stages</em> ({@code output_blocks} within a rule) &rarr;
     *       several {@code minecraft:rule} <strong>processors</strong> in the list.</li>
     * </ul>
     */
    @Test
    void aProbabilityMissFallsThroughToTheNextRuleInTheSameProcessor() {
        RuleProcessor alternatives = new RuleProcessor(List.of(
                new ProcessorRule(
                        new RandomBlockMatchTest(Blocks.STONE_BRICKS, 0.5f),
                        new BlockMatchTest(Blocks.AIR),
                        Blocks.MOSSY_STONE_BRICKS.defaultBlockState()),
                always(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState())));

        Map<String, Integer> outcomes = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                outcomes.merge(String.valueOf(
                        run(Blocks.STONE_BRICKS.defaultBlockState(), new BlockPos(x, 64, z), alternatives)),
                        1, Integer::sum);
            }
        }
        System.out.println("  one processor, 0.5 rule then a catch-all -> " + outcomes);

        int mossy = outcomes.getOrDefault(
                String.valueOf(Blocks.MOSSY_STONE_BRICKS.defaultBlockState()), 0);
        int cobble = outcomes.getOrDefault(
                String.valueOf(Blocks.COBBLESTONE.defaultBlockState()), 0);
        assertTrue(mossy > 0, "the 0.5 rule should fire sometimes: " + outcomes);
        assertTrue(cobble > 0,
                "a probability miss must fall through to the next rule, or several rules in one "
                        + "processor cannot express alternatives at all: " + outcomes);
        assertEquals(1600, mossy + cobble, "every block should take one branch or the other");
    }

    /**
     * The catch that makes a two-processor chain different from an {@code AgingProcessor} chain:
     * <strong>the stages are not independent draws on the same block</strong>. Every block that
     * reached stage 1 is offered to stage 2, so with vanilla rules the composed rate of the later
     * stage is the product of the probabilities -- which is the same arithmetic, but the
     * <em>ordering</em> of the whole file now matters, because every rule processor sees whatever
     * every earlier one did.
     *
     * <p>Demonstrated with certainty rather than rates: a third processor keyed on the second's
     * output fires too, so a chain is as long as the list is.</p>
     */
    @Test
    void chainsAreAsLongAsTheProcessorListIs() {
        RuleProcessor a = new RuleProcessor(List.of(
                always(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState())));
        RuleProcessor b = new RuleProcessor(List.of(
                always(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState())));
        RuleProcessor c = new RuleProcessor(List.of(
                always(Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState())));

        assertEquals(Blocks.GRAVEL.defaultBlockState(),
                run(Blocks.STONE_BRICKS.defaultBlockState(), new BlockPos(3, 64, 7), a, b, c));
    }
}
