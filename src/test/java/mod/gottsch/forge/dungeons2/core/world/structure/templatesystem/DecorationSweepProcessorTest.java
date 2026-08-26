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
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.BlockMatch;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression cover for the 2026-08-26 mold-on-a-facade bug, and for the sweep's refusal to
 * over-reach.
 *
 * <h2>The case being reproduced</h2>
 * <p>In the save it was {@code dungeonblocks:mold} at {@code -38 1 1801} clinging south to a
 * {@code polished_andesite_quarter_facade_block} the {@code 5x11_hallway_2} prefab had stamped over
 * the wall block a previously-rendered piece grew it on. Here the two DungeonBlocks blocks stand in
 * as {@code minecraft:glow_lichen} (a {@link MultifaceBlock}, same as {@code Mold}, which extends
 * {@code GlowLichenBlock}) and {@code minecraft:stone_brick_stairs} (passes {@code canOcclude},
 * fails {@code isSolidRender} &mdash; the exact pair of answers the facade gives). Forge locks the
 * block registry, so a headless test cannot see a DungeonBlocks block at all; what is under test is
 * the shape predicate, and vanilla has blocks with the same shapes.
 *
 * @author Mark Gottschling on Aug 26, 2026
 */
class DecorationSweepProcessorTest {

    /** These never re-serialize, so the processor type is never asked for. */
    private static final Supplier<StructureProcessorType<?>> NO_TYPE = () -> null;

    /** The cell that held the mold, and the wall cell south of it that the prefab re-skinned. */
    private static final BlockPos GROWTH = new BlockPos(-38, 1, 1801);
    private static final BlockPos WALL = GROWTH.south();

    /** Generous enough to hold both, so nothing under test is decided by clipping. */
    private static final BoundingBox CHUNK =
            new BoundingBox(-48, 0, 1792, -33, 15, 1807);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Configured like the shipped entry, in vanilla stand-ins. */
    private static DecorationSweepProcessor sweep() {
        return new DecorationSweepProcessor(NO_TYPE,
                new BlockMatch(List.of(Blocks.GLOW_LICHEN), List.of()),
                new BlockMatch(List.of(Blocks.COBWEB), List.of()),
                BlockMatch.NONE,
                new BlockMatch(List.of(Blocks.DIRT), List.of()),
                new BlockMatch(List.of(Blocks.BROWN_MUSHROOM), List.of()),
                new BlockMatch(List.of(Blocks.HANGING_ROOTS), List.of()),
                BlockMatch.NONE);
    }

    private static BlockState lichenFacing(Direction direction) {
        return Blocks.GLOW_LICHEN.defaultBlockState()
                .setValue(MultifaceBlock.getFaceProperty(direction), true);
    }

    /** Runs the sweep over a piece writing {@code pending}, against a level holding {@code world}. */
    private static Map<BlockPos, BlockState> run(Map<BlockPos, BlockState> world,
                                                 Map<BlockPos, BlockState> pending,
                                                 BoundingBox box) {
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        world.forEach((pos, state) -> level.level().setBlock(pos, state, 2));

        List<StructureTemplate.StructureBlockInfo> processed = new ArrayList<>();
        pending.forEach((pos, state) ->
                processed.add(new StructureTemplate.StructureBlockInfo(pos, state, null)));

        StructurePlaceSettings settings = new StructurePlaceSettings();
        if (box != null) {
            settings.setBoundingBox(box);
        }
        List<StructureTemplate.StructureBlockInfo> out = sweep().finalizeProcessing(
                level.level(), BlockPos.ZERO, BlockPos.ZERO, processed, processed, settings);

        // Last write stands, exactly as placeInWorld would apply the list.
        return out.stream().collect(Collectors.toMap(
                StructureTemplate.StructureBlockInfo::pos,
                StructureTemplate.StructureBlockInfo::state,
                (first, second) -> second,
                java.util.LinkedHashMap::new));
    }

    @Test
    void growthStrandedByAFacadeThisPieceIsAboutToWriteIsCleared() {
        // Exactly the save: the mold is already in the world, on a full cube, and this piece is
        // about to replace that full cube with a quarter slab.
        Map<BlockPos, BlockState> result = run(
                Map.of(GROWTH, lichenFacing(Direction.SOUTH),
                        WALL, Blocks.STONE_BRICKS.defaultBlockState()),
                Map.of(WALL, Blocks.STONE_BRICK_STAIRS.defaultBlockState()),
                CHUNK);

        assertEquals(Blocks.AIR.defaultBlockState(), result.get(GROWTH),
                "growth whose only face lost its full-cube support should be cleared");
    }

    @Test
    void theRepairIsAppendedAfterThePiecesOwnBlocks() {
        // Order is load-bearing: it is the piece's own write that makes the repair necessary, so a
        // repair placed before it would be overwritten by the very block that stranded the growth.
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        level.level().setBlock(GROWTH, lichenFacing(Direction.SOUTH), 2);
        level.level().setBlock(WALL, Blocks.STONE_BRICKS.defaultBlockState(), 2);

        List<StructureTemplate.StructureBlockInfo> processed = List.of(
                new StructureTemplate.StructureBlockInfo(
                        WALL, Blocks.STONE_BRICK_STAIRS.defaultBlockState(), null));

        List<StructureTemplate.StructureBlockInfo> out = sweep().finalizeProcessing(
                level.level(), BlockPos.ZERO, BlockPos.ZERO, processed, processed,
                new StructurePlaceSettings().setBoundingBox(CHUNK));

        assertEquals(2, out.size());
        assertEquals(WALL, out.get(0).pos(), "the piece's own block comes first");
        assertEquals(GROWTH, out.get(1).pos(), "the repair is appended");
    }

    @Test
    void growthKeptOnAFullCubeIsLeftAlone() {
        // The control the bug report needs: the sibling mold two cells west survived because the
        // prefab happened to put a full cube in ITS support cell. Nothing should touch that one.
        Map<BlockPos, BlockState> result = run(
                Map.of(GROWTH, lichenFacing(Direction.SOUTH),
                        WALL, Blocks.STONE_BRICKS.defaultBlockState()),
                Map.of(WALL, Blocks.CRACKED_STONE_BRICKS.defaultBlockState()),
                CHUNK);

        assertFalse(result.containsKey(GROWTH),
                "a still-supported growth should not appear in the output at all");
    }

    @Test
    void onlyTheDeadFaceOfAMultifaceGrowthIsDropped() {
        // A patch wrapping a corner keeps the faces whose walls survived. Clearing the block
        // outright would take good growth with it.
        BlockState twoFaces = lichenFacing(Direction.SOUTH)
                .setValue(MultifaceBlock.getFaceProperty(Direction.EAST), true);

        Map<BlockPos, BlockState> result = run(
                Map.of(GROWTH, twoFaces,
                        WALL, Blocks.STONE_BRICKS.defaultBlockState(),
                        GROWTH.east(), Blocks.STONE_BRICKS.defaultBlockState()),
                Map.of(WALL, Blocks.STONE_BRICK_STAIRS.defaultBlockState()),
                CHUNK);

        BlockState repaired = result.get(GROWTH);
        assertFalse(repaired.getValue(MultifaceBlock.getFaceProperty(Direction.SOUTH)),
                "the face whose wall became a slab should go");
        assertTrue(repaired.getValue(MultifaceBlock.getFaceProperty(Direction.EAST)),
                "the face whose wall is untouched should stay");
    }

    @Test
    void aCellThisPieceWritesItselfIsNeverRepaired() {
        // It is about to be overwritten regardless, and treating it as a candidate would mean
        // reading a state the piece has already decided to replace.
        Map<BlockPos, BlockState> result = run(
                Map.of(GROWTH, lichenFacing(Direction.SOUTH),
                        WALL, Blocks.STONE_BRICKS.defaultBlockState()),
                Map.of(WALL, Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
                        GROWTH, Blocks.TORCH.defaultBlockState()),
                CHUNK);

        assertEquals(Blocks.TORCH.defaultBlockState(), result.get(GROWTH),
                "the piece's own block for that cell should stand unaltered");
    }

    @Test
    void architectureTheConfigDoesNotNameIsNeverTouched() {
        // The sweep removes decoration, never architecture. A ladder is multiface-adjacent in
        // spirit and would fail a naive support test; it is not in the config, so it is not the
        // sweep's business.
        Map<BlockPos, BlockState> result = run(
                Map.of(GROWTH, Blocks.LADDER.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LadderBlock.FACING,
                                        Direction.NORTH),
                        WALL, Blocks.STONE_BRICKS.defaultBlockState()),
                Map.of(WALL, Blocks.STONE_BRICK_STAIRS.defaultBlockState()),
                CHUNK);

        assertFalse(result.containsKey(GROWTH), "an unnamed block should never be repaired");
    }

    @Test
    void aCellOutsideTheChunkBoxIsLeftForThePassThatOwnsIt() {
        // Reading outside the current WorldGenRegion during worldgen is illegal, so a candidate
        // the box excludes is skipped entirely -- the piece's pass over the neighbouring chunk is
        // where that cell gets its chance.
        BoundingBox wallOnly = new BoundingBox(WALL.getX(), WALL.getY(), WALL.getZ(),
                WALL.getX(), WALL.getY(), WALL.getZ());

        Map<BlockPos, BlockState> result = run(
                Map.of(GROWTH, lichenFacing(Direction.SOUTH),
                        WALL, Blocks.STONE_BRICKS.defaultBlockState()),
                Map.of(WALL, Blocks.STONE_BRICK_STAIRS.defaultBlockState()),
                wallOnly);

        assertFalse(result.containsKey(GROWTH),
                "a cell the sweep cannot legally read must be left alone");
    }

    @Test
    void anIdleSweepReturnsTheSameListInstance() {
        // A motif that configures nothing should cost one boolean check and no allocation.
        DecorationSweepProcessor idle = new DecorationSweepProcessor(NO_TYPE,
                BlockMatch.NONE, BlockMatch.NONE, BlockMatch.NONE, BlockMatch.NONE,
                BlockMatch.NONE, BlockMatch.NONE, BlockMatch.NONE);

        List<StructureTemplate.StructureBlockInfo> processed = List.of(
                new StructureTemplate.StructureBlockInfo(
                        WALL, Blocks.STONE_BRICKS.defaultBlockState(), null));

        assertSame(processed, idle.finalizeProcessing(FakeWorldGenLevel.create().level(),
                BlockPos.ZERO, BlockPos.ZERO, processed, processed,
                new StructurePlaceSettings().setBoundingBox(CHUNK)));
    }
}
