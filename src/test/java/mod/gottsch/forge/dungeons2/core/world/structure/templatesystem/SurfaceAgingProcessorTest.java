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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The surface gate, exercised a piece at a time -- which is the only way it can be, since
 * backlog #15 the processor decides in {@code finalizeProcessing} from the whole block list.
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
        FILL = Blocks.DEEPSLATE;
    }

    /** The piece origin. Relative Y 0 is the floor, so this Y is the floor's. */
    private static final BlockPos ORIGIN = new BlockPos(100, 60, -40);

    private static SurfaceAgingRule rule(PieceSurface surface, Block from, Block to) {
        return new SurfaceAgingRule(surface, from, List.of(new AgingStage(to, 1.0)));
    }

    private static SurfaceAgingProcessor processor(SurfaceAgingRule... rules) {
        return new SurfaceAgingProcessor(() -> null, 4, List.of(rules));
    }

    /**
     * A filler no rule in this class names, so a scaffold column never decays and only the cell
     * under test can change.
     *
     * <p>Assigned in {@link #bootstrap()} rather than initialised inline: touching {@link Blocks}
     * from a static initialiser loads the block registry before {@code Bootstrap.bootStrap()} has
     * run, which fails the whole class with an {@code ExceptionInInitializerError} whenever it is
     * the first to load -- i.e. when this class is run on its own.</p>
     */
    private static Block FILL;

    /** How tall the scaffold column is; anything above the tallest {@code relativeY} tested. */
    private static final int COLUMN_TOP = 14;

    private static StructureTemplate.StructureBlockInfo at(BlockPos pos, BlockState state) {
        return new StructureTemplate.StructureBlockInfo(pos, state, null);
    }

    /**
     * Runs a whole piece through the processor and returns it keyed by position.
     *
     * <p>Through {@code StructureTemplate.processBlockInfos} rather than by calling one method,
     * because the processor no longer decides in a fixed phase: rules answerable from Y stay in
     * {@code processBlock} and geometric ones move to {@code finalizeProcessing}. Only vanilla's
     * own loop runs both, so only it tests what a dungeon actually does.
     * {@code ProcessorPhaseOrderTest} is where that split is pinned directly.</p>
     */
    private static Map<BlockPos, BlockState> run(
            SurfaceAgingProcessor processor, List<StructureTemplate.StructureBlockInfo> piece) {

        // processBlockInfos offsets what it is given by the origin, so hand it the piece the way
        // a template stores one: relative. What comes back is in world space.
        List<StructureTemplate.StructureBlockInfo> relative = new ArrayList<>(piece.size());
        for (StructureTemplate.StructureBlockInfo info : piece) {
            relative.add(new StructureTemplate.StructureBlockInfo(
                    info.pos().subtract(ORIGIN), info.state(), info.nbt()));
        }

        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.addProcessor(processor);

        Map<BlockPos, BlockState> byPos = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo info : StructureTemplate.processBlockInfos(
                FakeWorldGenLevel.create().level(), ORIGIN, ORIGIN, settings, relative, null)) {
            byPos.put(info.pos(), info.state());
        }
        return byPos;
    }

    /**
     * Puts {@code state} at {@code relativeY} in a solid column standing on the floor, and returns
     * what came back for that cell.
     *
     * <p>The column matters: a surface is a question about a cell's neighbours now, so a lone block
     * hanging in space is not the case any of these tests mean. Standing on something makes every
     * cell above layer 0 a {@link PieceSurface#WALL}, which is the ordinary case
     * {@link PieceSurface#ABOVE_FLOOR} has always described.</p>
     */
    private static BlockState run(SurfaceAgingProcessor processor, BlockState state, int relativeY) {
        List<StructureTemplate.StructureBlockInfo> piece = new ArrayList<>();
        for (int y = 0; y <= COLUMN_TOP; y++) {
            piece.add(at(ORIGIN.above(y), y == relativeY ? state : FILL.defaultBlockState()));
        }
        return run(processor, piece).get(ORIGIN.above(relativeY));
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

    // ---------- backlog #15: the surfaces above the floor ----------

    /**
     * A room reduced to what the classifier reads: a floor that insets by one (so the wall ring
     * stands on nothing the piece placed), a wall ring, a ceiling over the interior only, and one
     * joist hanging under it.
     *
     * @param post {@code true} to stand a post on the floor at the room's centre, which is the
     *             cell a joist rule must not reach
     */
    private static List<StructureTemplate.StructureBlockInfo> room(BlockState beam, boolean post) {
        List<StructureTemplate.StructureBlockInfo> piece = new ArrayList<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean ring = x == 0 || x == 4 || z == 0 || z == 4;
                if (ring) {
                    // Walls span [1 .. height-2], and the floor does NOT reach under them.
                    for (int y = 1; y <= 4; y++) {
                        piece.add(at(ORIGIN.offset(x, y, z), FILL.defaultBlockState()));
                    }
                } else {
                    piece.add(at(ORIGIN.offset(x, 0, z), FILL.defaultBlockState()));
                    piece.add(at(ORIGIN.offset(x, 5, z), FILL.defaultBlockState()));
                }
            }
        }
        if (post) {
            // A post of the same material, standing on the floor, at the room's centre.
            for (int y = 1; y <= 4; y++) {
                piece.add(at(ORIGIN.offset(2, y, 2), beam));
            }
        }
        // A beam across the interior, hanging one course under the ceiling. Where a post is
        // present it already owns the centre cell, so the beam runs to either side of it.
        for (int x = 1; x <= 3; x++) {
            if (!(post && x == 2)) {
                piece.add(at(ORIGIN.offset(x, 4, 2), beam));
            }
        }
        return piece;
    }

    /**
     * The wall ring's top course is the highest block in its own column, because the ceiling insets
     * by one. Reading a ceiling as "the highest block in the column" would decay the top course of
     * every procedural room on the ceiling's schedule.
     */
    @Test
    void aWallRunsTopCourseIsNotACeiling() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.CEILING, FILL, Blocks.DIRT));

        Map<BlockPos, BlockState> result = run(processor, room(FILL.defaultBlockState(), false));

        assertSame(Blocks.DIRT.defaultBlockState(), result.get(ORIGIN.offset(2, 5, 2)),
                "the ceiling slab itself should have decayed");
        assertSame(FILL.defaultBlockState(), result.get(ORIGIN.offset(0, 4, 0)),
                "the top course of the wall ring is a wall, however high it reaches");
        assertSame(FILL.defaultBlockState(), result.get(ORIGIN.offset(0, 1, 0)),
                "the bottom course stands on the floor plane even where the floor insets away");
    }

    /**
     * Backlog #45's blocker, in one test. A {@code spruce_log} beam may crumble to a gap because
     * the shell above it stays intact; the identical block used as a post may not, because that
     * leaves whatever it carries floating. Both are {@link PieceSurface#ABOVE_FLOOR} and no rule
     * keyed on the block could tell them apart.
     */
    @Test
    void aJoistDecaysWhereThePostOfTheSameTimberDoesNot() {
        SurfaceAgingProcessor processor = processor(
                rule(PieceSurface.JOIST, Blocks.SPRUCE_LOG, Blocks.AIR));

        Map<BlockPos, BlockState> result =
                run(processor, room(Blocks.SPRUCE_LOG.defaultBlockState(), true));

        assertSame(Blocks.AIR.defaultBlockState(), result.get(ORIGIN.offset(1, 4, 2)),
                "the beam should have gapped");
        for (int y = 1; y <= 4; y++) {
            assertSame(Blocks.SPRUCE_LOG.defaultBlockState(), result.get(ORIGIN.offset(2, y, 2)),
                    "the post is standing on something at relative Y " + y + " and is not a joist");
        }
    }

    /**
     * The union still covers all three, so an existing rule keeps its reach &mdash; and it reaches
     * the same cells <strong>whether or not the piece was scanned</strong>.
     *
     * <p>That second half is the point. A processor whose rules only name {@code any},
     * {@code floor} or {@code above_floor} skips building the {@link PieceSurfaceMap} entirely,
     * since none of those needs the geometry; the two runs here differ only in a
     * {@link PieceSurface#JOIST} rule on an unrelated block, which is enough to switch the scan on.
     * If the skipped path ever stopped agreeing with the scanned one, every list shipped today
     * would quietly change its decay and no other test would notice.</p>
     */
    @Test
    void anAboveFloorRuleReadsTheSameScannedOrNot() {
        List<StructureTemplate.StructureBlockInfo> piece =
                room(Blocks.SPRUCE_LOG.defaultBlockState(), true);

        Map<BlockPos, BlockState> unscanned = run(processor(
                rule(PieceSurface.ABOVE_FLOOR, Blocks.SPRUCE_LOG, Blocks.AIR)), piece);
        Map<BlockPos, BlockState> scanned = run(processor(
                rule(PieceSurface.ABOVE_FLOOR, Blocks.SPRUCE_LOG, Blocks.AIR),
                rule(PieceSurface.JOIST, Blocks.OAK_LOG, Blocks.AIR)), piece);

        assertSame(Blocks.AIR.defaultBlockState(), unscanned.get(ORIGIN.offset(1, 4, 2)),
                "above_floor must still reach a joist");
        assertSame(Blocks.AIR.defaultBlockState(), unscanned.get(ORIGIN.offset(2, 1, 2)),
                "above_floor must still reach a wall");
        assertEquals(unscanned, scanned,
                "skipping the scan changed the piece, so the two paths have diverged");
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

        List<StructureTemplate.StructureBlockInfo> floor = new ArrayList<>();
        for (int x = 0; x < 40; x++) {
            floor.add(at(ORIGIN.offset(x, 0, 0), Blocks.COBBLESTONE.defaultBlockState()));
        }

        int aged = 0;
        for (BlockState state : run(processor, floor).values()) {
            if (state.is(Blocks.MOSSY_COBBLESTONE)) {
                aged++;
            }
        }
        assertTrue(aged > 5 && aged < 35,
                "at p=0.5 over 40 cells, expected a spread rather than " + aged + " -- a constant"
                        + " here would mean the seed is not varying with position");
    }

    // ---------- the elevation gate (2026-09-18) ----------

    /**
     * Bands are thirds of the piece's OWN height, so the same rule reads the same way on a tall
     * entrance and a squat one. COLUMN_TOP is 14, giving a span of 14: base 0-4, middle 5-9,
     * crown 10-14.
     */
    @Test
    void aBandRuleFiresOnlyInsideItsThirdOfThePiece() {
        SurfaceAgingProcessor processor = processor(new SurfaceAgingRule(
                PieceSurface.ANY, PieceElevation.CROWN, Blocks.STONE_BRICKS,
                List.of(new AgingStage(Blocks.AIR, 1.0))));

        for (int relativeY = 0; relativeY <= COLUMN_TOP; relativeY++) {
            BlockState result = run(processor, Blocks.STONE_BRICKS.defaultBlockState(), relativeY);
            boolean crown = relativeY >= 10;
            assertSame(crown ? Blocks.AIR.defaultBlockState()
                            : Blocks.STONE_BRICKS.defaultBlockState(), result,
                    "relative Y " + relativeY + " was " + (crown ? "not " : "") + "collapsed by a"
                            + " crown rule");
        }
    }

    /** The three bands partition the piece: every cell matches exactly one of them. */
    @Test
    void theThreeBandsCoverThePieceWithoutOverlapping() {
        SurfaceAgingProcessor processor = processor(
                new SurfaceAgingRule(PieceSurface.ANY, PieceElevation.BASE, Blocks.STONE_BRICKS,
                        List.of(new AgingStage(Blocks.COBBLESTONE, 1.0))),
                new SurfaceAgingRule(PieceSurface.ANY, PieceElevation.MIDDLE, Blocks.STONE_BRICKS,
                        List.of(new AgingStage(Blocks.GRAVEL, 1.0))),
                new SurfaceAgingRule(PieceSurface.ANY, PieceElevation.CROWN, Blocks.STONE_BRICKS,
                        List.of(new AgingStage(Blocks.AIR, 1.0))));

        for (int relativeY = 0; relativeY <= COLUMN_TOP; relativeY++) {
            BlockState result = run(processor, Blocks.STONE_BRICKS.defaultBlockState(), relativeY);
            assertTrue(!result.is(Blocks.STONE_BRICKS),
                    "relative Y " + relativeY + " matched no band, so the partition has a hole");
        }
    }

    /**
     * Surface and elevation are ANDed, which is the case the two-field design exists for: a rule
     * can say "the floor, but only where the piece is low" and nothing else.
     */
    @Test
    void surfaceAndElevationBothHaveToMatch() {
        SurfaceAgingProcessor processor = processor(new SurfaceAgingRule(
                PieceSurface.FLOOR, PieceElevation.CROWN, Blocks.COBBLESTONE,
                List.of(new AgingStage(Blocks.AIR, 1.0))));

        // Layer 0 IS the floor, but in a 15-high column it is the base band, never the crown.
        assertSame(Blocks.COBBLESTONE.defaultBlockState(),
                run(processor, Blocks.COBBLESTONE.defaultBlockState(), 0),
                "the floor matched a crown rule");
        assertSame(Blocks.COBBLESTONE.defaultBlockState(),
                run(processor, Blocks.COBBLESTONE.defaultBlockState(), COLUMN_TOP),
                "the crown matched a floor rule");
    }

    /**
     * Air is not a block for the purposes of the extent. A piece whose top course has already been
     * knocked out by an earlier pass has not thereby become a shorter building -- if the holes
     * counted, the bands would creep downward as the ruin deepened and the collapse would eat
     * itself.
     */
    @Test
    void holesDoNotShortenThePiece() {
        List<StructureTemplate.StructureBlockInfo> piece = new ArrayList<>();
        for (int y = 0; y <= COLUMN_TOP; y++) {
            piece.add(at(ORIGIN.above(y), Blocks.STONE_BRICKS.defaultBlockState()));
        }
        // the whole crown, already gone
        for (int y = 10; y <= COLUMN_TOP; y++) {
            piece.set(y, at(ORIGIN.above(y), Blocks.AIR.defaultBlockState()));
        }

        SurfaceAgingProcessor processor = processor(new SurfaceAgingRule(
                PieceSurface.ANY, PieceElevation.CROWN, Blocks.STONE_BRICKS,
                List.of(new AgingStage(Blocks.GRAVEL, 1.0))));

        Map<BlockPos, BlockState> result = run(processor, piece);
        for (int y = 0; y <= 9; y++) {
            assertSame(Blocks.STONE_BRICKS.defaultBlockState(), result.get(ORIGIN.above(y)),
                    "relative Y " + y + " was treated as the crown once the real crown was air");
        }
    }
}
