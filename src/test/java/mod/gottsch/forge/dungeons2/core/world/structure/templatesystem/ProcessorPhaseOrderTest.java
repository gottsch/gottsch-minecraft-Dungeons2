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
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingStage;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * <strong>Which processors in a list can feed which.</strong> A weathering list is authored as a
 * sequence and reads like a pipeline, but vanilla runs it in <em>two</em> passes, and a processor
 * can only be fed by one that decided in the same pass or an earlier one.
 *
 * <h2>The question this answers</h2>
 * <p>Raised by Gottsch, 2026-09-04, on hearing that {@link SurfaceAgingProcessor} had moved into
 * {@code finalizeProcessing} for backlog #15: <em>"that means I lose the ability to do multi-step
 * aging using multiple processors"</em>. <strong>No &mdash; that much survives</strong>: two
 * {@code surface_aging} entries chain in either phase, which is what
 * {@link #oneSurfaceAgingProcessorFeedsTheNext} says. What is genuinely out of reach is narrower,
 * and it is why the phase is now chosen per processor rather than imposed on all of them.
 * {@link VanillaRuleChainingTest} answers the same question one level down, for two rules inside
 * one pass; this answers it for two processors across the list, which is the level the weathering
 * files are authored at.</p>
 *
 * <p>{@code StructureTemplate.processBlockInfos} loops <strong>blocks on the outside, processors on
 * the inside</strong> for {@code processBlock} -- so every processBlock-phase processor sees the
 * previous one's output, per block, in list order. Only when every block has been through all of
 * them does it make a second loop calling {@code finalizeProcessing} in list order, each handed the
 * previous one's returned list.</p>
 *
 * <p>So the pipeline is real <em>within</em> a phase and in one direction between them:</p>
 * <ul>
 *   <li>processBlock &rarr; processBlock: <strong>chains</strong> (list order)</li>
 *   <li>finalize &rarr; finalize: <strong>chains</strong> (list order)</li>
 *   <li>processBlock &rarr; finalize: <strong>chains</strong>, always, whatever the list order</li>
 *   <li>finalize &rarr; processBlock: <strong>never</strong>, whatever the list order</li>
 * </ul>
 *
 * <p>The last line is the one with teeth, and it is why
 * {@code StratumWeatheringListTest.aSurfaceAgedListCarriesNoRuleProcessor} exists: a
 * {@code minecraft:rule} entry authored <em>after</em> a geometric {@code dungeons2:surface_aging}
 * looks like a second stage and is not one. The file would read correctly and generate wrongly.</p>
 *
 * <p><strong>Which phase a {@code surface_aging} entry lands in is decided by its own rules.</strong>
 * {@code any} / {@code floor} / {@code above_floor} are answerable from Y, so it stays in
 * {@code processBlock} and nothing about it changed; only {@code wall} / {@code ceiling} /
 * {@code joist} need the whole list and move it. That is the compatibility guarantee, and
 * {@link #agingThatAsksNoGeometricQuestionStillFeedsARuleProcessor} is where it is pinned.</p>
 *
 * @author Mark Gottschling on Sep 4, 2026
 */
class ProcessorPhaseOrderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockPos ORIGIN = BlockPos.ZERO;

    private static SurfaceAgingProcessor aging(PieceSurface surface, Block from, Block to) {
        return new SurfaceAgingProcessor(() -> null, 1, List.of(new SurfaceAgingRule(
                surface, from, List.of(new AgingStage(to, 1.0)))));
    }

    /**
     * Aging that asks a geometric question, so it decides in {@code finalizeProcessing}.
     * {@code wall} is the one every ordinary block of a piece is, so the surface itself is not what
     * any of these tests turn on -- the phase it forces is.
     */
    private static SurfaceAgingProcessor late(Block from, Block to) {
        return aging(PieceSurface.WALL, from, to);
    }

    /** Aging answerable from Y, which stays in {@code processBlock} where it always was. */
    private static SurfaceAgingProcessor early(Block from, Block to) {
        return aging(PieceSurface.ABOVE_FLOOR, from, to);
    }

    /** A vanilla rule processor that always converts {@code from} to {@code to}. */
    private static RuleProcessor rule(Block from, Block to) {
        return new RuleProcessor(List.of(new ProcessorRule(new BlockMatchTest(from),
                new BlockMatchTest(Blocks.AIR), to.defaultBlockState())));
    }

    /**
     * Runs one block at relative Y 1 through {@code processors} exactly as vanilla would, both
     * phases, and returns what came out.
     */
    private static BlockState run(BlockState input, StructureProcessor... processors) {
        StructurePlaceSettings settings = new StructurePlaceSettings();
        for (StructureProcessor processor : processors) {
            settings.addProcessor(processor);
        }
        List<StructureTemplate.StructureBlockInfo> out = StructureTemplate.processBlockInfos(
                FakeWorldGenLevel.create().level(), ORIGIN, ORIGIN, settings,
                List.of(new StructureTemplate.StructureBlockInfo(ORIGIN.above(), input, null)),
                null);
        return out.get(0).state();
    }

    /** Two surface-aging entries chain, so multi-step aging across processors still works. */
    @Test
    void oneSurfaceAgingProcessorFeedsTheNext() {
        assertSame(Blocks.DIRT.defaultBlockState(),
                run(Blocks.MUD_BRICKS.defaultBlockState(),
                        late(Blocks.MUD_BRICKS, Blocks.PACKED_MUD),
                        late(Blocks.PACKED_MUD, Blocks.DIRT)),
                "two finalize-phase processors must chain in list order, or a weathering file"
                        + " cannot express a stage the first processor's output feeds");

        assertSame(Blocks.DIRT.defaultBlockState(),
                run(Blocks.MUD_BRICKS.defaultBlockState(),
                        early(Blocks.MUD_BRICKS, Blocks.PACKED_MUD),
                        early(Blocks.PACKED_MUD, Blocks.DIRT)),
                "and so must two processBlock-phase ones");
    }

    /**
     * <strong>The compatibility guarantee.</strong> Aging that asks nothing of the piece's geometry
     * stays in {@code processBlock}, so it still feeds a {@code minecraft:rule} after it exactly as
     * it did before backlog #15. This is what lets {@code classic_weathering.json} move to
     * {@code surface_aging} without giving up its rule entries.
     */
    @Test
    void agingThatAsksNoGeometricQuestionStillFeedsARuleProcessor() {
        assertSame(Blocks.DIRT.defaultBlockState(),
                run(Blocks.MUD_BRICKS.defaultBlockState(),
                        early(Blocks.MUD_BRICKS, Blocks.PACKED_MUD),
                        rule(Blocks.PACKED_MUD, Blocks.DIRT)));
    }

    /** A rule before geometric aging feeds it -- and so does one after, which is the trap. */
    @Test
    void aRuleProcessorFeedsGeometricAgingWhicheverWayRound() {
        assertSame(Blocks.DIRT.defaultBlockState(),
                run(Blocks.MUD_BRICKS.defaultBlockState(),
                        rule(Blocks.MUD_BRICKS, Blocks.PACKED_MUD),
                        late(Blocks.PACKED_MUD, Blocks.DIRT)));

        assertSame(Blocks.DIRT.defaultBlockState(),
                run(Blocks.MUD_BRICKS.defaultBlockState(),
                        late(Blocks.PACKED_MUD, Blocks.DIRT),
                        rule(Blocks.MUD_BRICKS, Blocks.PACKED_MUD)),
                "a rule runs in the earlier phase whatever the authored order says");
    }

    /**
     * The loss, stated as a test so it cannot be forgotten: <em>geometric</em> surface aging cannot
     * feed a rule processor, however the file is ordered. Authoring the second stage as another
     * {@code surface_aging} entry is what expresses it.
     */
    @Test
    void geometricSurfaceAgingCannotFeedARuleProcessor() {
        assertSame(Blocks.PACKED_MUD.defaultBlockState(),
                run(Blocks.MUD_BRICKS.defaultBlockState(),
                        late(Blocks.MUD_BRICKS, Blocks.PACKED_MUD),
                        rule(Blocks.PACKED_MUD, Blocks.DIRT)),
                "if this ever reaches dirt, finalize started feeding processBlock and"
                        + " aSurfaceAgedListCarriesNoRuleProcessor can be relaxed");
    }
}
