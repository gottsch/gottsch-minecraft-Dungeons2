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

import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingStage;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The surface gate, exercised a block at a time.
 *
 * <p>{@code StratumWeatheringListTest} asserts the shipped JSON is shaped right; this asserts the
 * processor actually honours it. Both matter and neither implies the other &mdash; a gate that
 * decoded correctly and then applied to everything would pass that test and fail every room.</p>
 *
 * <p>Probabilities are 1.0 throughout, so a chain either applies or it does not and no roll is
 * being tested. The rates are {@code StratumWeatheringListTest}'s business.</p>
 */
class SurfaceAgingProcessorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The piece origin. Relative Y 0 is the floor, so this Y is the floor's. */
    private static final BlockPos ORIGIN = new BlockPos(100, 60, -40);

    private static SurfaceAgingRule rule(PieceSurface surface, Block from, Block to) {
        return new SurfaceAgingRule(surface, from, List.of(new AgingStage(to, 1.0)));
    }

    private static SurfaceAgingProcessor processor(SurfaceAgingRule... rules) {
        return new SurfaceAgingProcessor(() -> null, 4, List.of(rules));
    }

    /** Runs one block at {@code relativeY} above the origin and returns what came back. */
    private static BlockState run(SurfaceAgingProcessor processor, BlockState state, int relativeY) {
        BlockPos pos = ORIGIN.above(relativeY);
        StructureTemplate.StructureBlockInfo info =
                new StructureTemplate.StructureBlockInfo(pos, state, null);
        return processor.processBlock(null, ORIGIN, ORIGIN, info, info, new StructurePlaceSettings())
                .state();
    }

    // ---------- the gate ----------

    @Test
    void aFloorRuleFiresOnLayerZeroAndNowhereElse() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.FLOOR, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE));

        assertSame(Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                run(processor, Blocks.COBBLESTONE.defaultBlockState(), 0));
        for (int relativeY = 1; relativeY <= 12; relativeY++) {
            assertSame(Blocks.COBBLESTONE.defaultBlockState(),
                    run(processor, Blocks.COBBLESTONE.defaultBlockState(), relativeY),
                    "a floor rule reached relative Y " + relativeY);
        }
    }

    /**
     * The case the whole design exists for: a cobble WALL on a band whose floor rule names
     * cobblestone must not decay. Keying on the block alone could not express this.
     */
    @Test
    void aCobbleWallIsUntouchedByTheCobbleFloorRule() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.FLOOR, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE));
        assertSame(Blocks.COBBLESTONE.defaultBlockState(),
                run(processor, Blocks.COBBLESTONE.defaultBlockState(), 3),
                "an all-cobble fortified room on this band would lose its walls to the floor rule");
    }

    @Test
    void anAboveFloorRuleFiresEverywhereButLayerZero() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.ABOVE_FLOOR, Blocks.MUD_BRICKS, Blocks.PACKED_MUD));

        assertSame(Blocks.MUD_BRICKS.defaultBlockState(),
                run(processor, Blocks.MUD_BRICKS.defaultBlockState(), 0));
        for (int relativeY = 1; relativeY <= 12; relativeY++) {
            assertSame(Blocks.PACKED_MUD.defaultBlockState(),
                    run(processor, Blocks.MUD_BRICKS.defaultBlockState(), relativeY),
                    "an above_floor rule missed relative Y " + relativeY);
        }
    }

    /**
     * The two gates partition the piece: one block, two rules, and exactly one applies at any
     * height. This is what makes the gates exclusive rather than additive.
     */
    @Test
    void theTwoGatesPartitionThePiece() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.FLOOR, Blocks.MUD_BRICKS, Blocks.DIRT),
                rule(PieceSurface.ABOVE_FLOOR, Blocks.MUD_BRICKS, Blocks.PACKED_MUD));

        assertSame(Blocks.DIRT.defaultBlockState(),
                run(processor, Blocks.MUD_BRICKS.defaultBlockState(), 0));
        assertSame(Blocks.PACKED_MUD.defaultBlockState(),
                run(processor, Blocks.MUD_BRICKS.defaultBlockState(), 1));
    }

    /** An ungated rule is exactly {@code dungeons2:aging}'s behaviour -- the superset claim. */
    @Test
    void anUngatedRuleDecaysEverySurface() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.ANY, Blocks.MUD_BRICKS, Blocks.PACKED_MUD));
        for (int relativeY = 0; relativeY <= 8; relativeY++) {
            assertSame(Blocks.PACKED_MUD.defaultBlockState(),
                    run(processor, Blocks.MUD_BRICKS.defaultBlockState(), relativeY));
        }
    }

    @Test
    void aBlockNoRuleNamesIsReturnedUntouched() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.FLOOR, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE));
        assertSame(Blocks.STONE_BRICKS.defaultBlockState(),
                run(processor, Blocks.STONE_BRICKS.defaultBlockState(), 0));
    }

    // ---------- inherited behaviour that must not have been lost in the copy ----------

    /**
     * Properties carry across, which is what lets one rule age a whole family of shaped blocks.
     * Asserted because this processor mirrors GottschCore's rather than extending it, so the
     * behaviour is duplicated code and could drift.
     */
    @Test
    void propertiesCarryAcrossTheDecay() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.FLOOR, Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS));
        BlockState upsideDown = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HALF, Half.TOP);

        BlockState aged = run(processor, upsideDown, 0);
        assertEquals(Blocks.MOSSY_STONE_BRICK_STAIRS, aged.getBlock());
        assertEquals(Half.TOP, aged.getValue(BlockStateProperties.HALF),
                "an upside-down cornice must stay upside down through the decay");
    }

    /**
     * The random is seeded from the block's absolute world position, so a piece spanning a chunk
     * seam resolves every cell the same way in both of {@code PieceProcessors}' passes. A
     * per-instance random would tear the piece along the seam.
     */
    @Test
    void theSameCellResolvesIdenticallyEveryTime() {
        SurfaceAgingProcessor processor = processor(new SurfaceAgingRule(PieceSurface.FLOOR,
                Blocks.COBBLESTONE, List.of(new AgingStage(Blocks.MOSSY_COBBLESTONE, 0.5))));

        BlockState first = run(processor, Blocks.COBBLESTONE.defaultBlockState(), 0);
        for (int run = 0; run < 20; run++) {
            assertSame(first, run(processor, Blocks.COBBLESTONE.defaultBlockState(), 0),
                    "the same cell resolved differently on run " + run);
        }
    }

    /** And two different cells are not all forced to the same answer -- the seed really varies. */
    @Test
    void differentCellsDoNotAllResolveTheSameWay() {
        SurfaceAgingProcessor processor = processor(new SurfaceAgingRule(PieceSurface.FLOOR,
                Blocks.COBBLESTONE, List.of(new AgingStage(Blocks.MOSSY_COBBLESTONE, 0.5))));

        int aged = 0;
        for (int x = 0; x < 40; x++) {
            BlockPos pos = ORIGIN.offset(x, 0, 0);
            StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(
                    pos, Blocks.COBBLESTONE.defaultBlockState(), null);
            if (processor.processBlock(null, ORIGIN, ORIGIN, info, info, new StructurePlaceSettings())
                    .state().is(Blocks.MOSSY_COBBLESTONE)) {
                aged++;
            }
        }
        assertTrue(aged > 5 && aged < 35,
                "at p=0.5 over 40 cells, expected a spread rather than " + aged + " -- a constant"
                        + " here would mean the seed is not varying with position");
    }
}
