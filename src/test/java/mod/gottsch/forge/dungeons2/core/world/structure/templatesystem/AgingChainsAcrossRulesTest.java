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

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingProcessor;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Does {@code AgingProcessor} follow a chain <em>across</em> rules -- i.e. can a block it just
 * produced be matched by a second rule keyed on that output?
 *
 * <h2>Why this is a test and not a claim</h2>
 * <p>The answer has been asserted from bytecode reading since Aug 04 2026, and repeated in the
 * backlog (#26), two memories and several file comments, without ever being executed. It was
 * challenged on 2026-08-05 with real in-game evidence: large stone brick had visibly weathered to
 * cobblestone in a corridor wall, which looks exactly like
 * {@code large_stone_brick -> mossy_large_stone_brick -> cobblestone} chaining across two rules.
 *
 * <p>That evidence does not actually discriminate, because {@code classic_weathering.json} also
 * reaches {@code cobblestone} from {@code left_large_stone_brick} in a <strong>single</strong> rule
 * (the deep-decay chain, ~5.6% composed). Both models predict the screenshot. So the mechanism
 * needs testing on its own, which is what this does.</p>
 *
 * <h2>Vanilla blocks only, deliberately</h2>
 * <p>The real rules name {@code dungeonblocks:*} ids, and under a bare {@code Bootstrap} those
 * resolve to {@code minecraft:air} rather than failing (backlog #13) -- a rule keyed on air would
 * make this test meaningless. Everything here is vanilla, and the question is about the processor's
 * control flow, not about any particular block.</p>
 *
 * @author Mark Gottschling on Aug 05, 2026
 */
class AgingChainsAcrossRulesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Supplier<StructureProcessorType<?>> NO_TYPE = () -> null;

    /**
     * Two rules, deliberately shaped like the ones in the shipped file: A produces B at 100%, and a
     * separate rule turns B into C at 100%. If the processor re-resolves its own output, everything
     * comes out C. If it looks the input block up once, everything comes out B.
     *
     * <p>Probabilities are 1.0 so the answer is not a rate to interpret -- one run of one block
     * settles it.</p>
     */
    private static AgingProcessor twoRuleChain() {
        String json = """
                {
                  "agings": 3,
                  "rules": [
                    {
                      "block": "minecraft:stone_bricks",
                      "output_blocks": [
                        { "block": "minecraft:mossy_stone_bricks", "probability": 1.0 }
                      ]
                    },
                    {
                      "block": "minecraft:mossy_stone_bricks",
                      "output_blocks": [
                        { "block": "minecraft:cobblestone", "probability": 1.0 }
                      ]
                    }
                  ]
                }
                """;
        return AgingProcessor.codec(NO_TYPE)
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, msg -> {
                    throw new AssertionError("rules failed to decode: " + msg);
                });
    }

    private static BlockState age(AgingProcessor processor, BlockState input, BlockPos pos) {
        StructureTemplate.StructureBlockInfo info =
                new StructureTemplate.StructureBlockInfo(pos, input, null);
        StructureTemplate.StructureBlockInfo out =
                processor.processBlock(null, BlockPos.ZERO, BlockPos.ZERO, info, info,
                        new StructurePlaceSettings());
        return out == null ? null : out.state();
    }

    /**
     * The disputed behaviour, settled. Run over many positions because the processor keys its
     * randomness off the block position ({@code Mth.getSeed(pos)}), so a single position could
     * coincidentally look like either answer.
     */
    @Test
    void agingDoesNotReResolveItsOwnOutput() {
        AgingProcessor processor = twoRuleChain();
        Map<String, Integer> outcomes = new LinkedHashMap<>();

        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                BlockState result = age(processor, Blocks.STONE_BRICKS.defaultBlockState(),
                        new BlockPos(x, 64, z));
                outcomes.merge(String.valueOf(result), 1, Integer::sum);
            }
        }

        System.out.println("  stone_bricks through a two-rule chain -> " + outcomes);

        String mossy = String.valueOf(Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        String cobble = String.valueOf(Blocks.COBBLESTONE.defaultBlockState());

        assertEquals(1600, outcomes.getOrDefault(mossy, 0),
                "every block should stop at the FIRST rule's output; a cobblestone here would mean "
                        + "the processor re-resolves what it just produced, and Backlog #26 plus "
                        + "several file comments are wrong: " + outcomes);
        assertEquals(0, outcomes.getOrDefault(cobble, 0),
                "cobblestone is only reachable by matching the second rule against the first "
                        + "rule's output: " + outcomes);
    }

    /**
     * The other half of the claim, and the one that makes those rules <em>latent</em> rather than
     * dead: the second rule is perfectly good, it just never gets an input. Feed it one directly and
     * it fires.
     *
     * <p>This is why "unreachable" must not be read as "safe to delete" -- authoring the input block
     * into a scheme wakes the rule up.</p>
     */
    @Test
    void theSecondRuleFiresWhenItsInputIsPlacedDirectly() {
        AgingProcessor processor = twoRuleChain();
        int cobble = 0;
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                BlockState result = age(processor, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                        new BlockPos(x, 64, z));
                if (Blocks.COBBLESTONE.defaultBlockState().equals(result)) {
                    cobble++;
                }
            }
        }
        assertEquals(1600, cobble,
                "the rule itself works -- it simply never receives an input during weathering");
    }

    /**
     * And the reason the in-game screenshot does not settle the question: the shipped file reaches
     * cobblestone from large stone brick inside a <em>single</em> rule, so cobblestone in a wall is
     * predicted with or without cross-rule chaining.
     */
    @Test
    void oneRuleCanReachCobblestoneOnItsOwn() {
        AgingProcessor processor = AgingProcessor.codec(NO_TYPE)
                .parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        {
                          "agings": 3,
                          "rules": [
                            {
                              "block": "minecraft:stone_bricks",
                              "output_blocks": [
                                { "block": "minecraft:cobblestone", "probability": 0.114 },
                                { "block": "minecraft:dirt", "probability": 0.3 }
                              ]
                            }
                          ]
                        }
                        """))
                .getOrThrow(false, msg -> {
                    throw new AssertionError(msg);
                });

        int cobble = 0;
        for (int x = 0; x < 60; x++) {
            for (int z = 0; z < 60; z++) {
                if (Blocks.COBBLESTONE.defaultBlockState().equals(
                        age(processor, Blocks.STONE_BRICKS.defaultBlockState(), new BlockPos(x, 64, z)))) {
                    cobble++;
                }
            }
        }
        assertTrue(cobble > 0,
                "a single rule must be able to produce cobblestone -- otherwise the screenshot "
                        + "really would need cross-rule chaining to explain it");
    }
}
