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
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code dungeons2:support_sweep}: whatever severe weathering left hanging is never placed.
 *
 * <h2>The two halves worth pinning</h2>
 * <p><strong>It removes islands</strong> &mdash; a chunk of wall that decay cut off from everything
 * holding it up. And <strong>it keeps overhangs</strong>: a lintel, an arch, a corbel, a projecting
 * course. Those two pull in opposite directions and the second is the one a cheaper rule gets wrong.
 * "Remove anything with air directly beneath it" passes every island test in this file and deletes
 * the top of every doorway, which is why support here is connectivity to the ground rather than a
 * look downward.</p>
 *
 * @author Mark Gottschling on Aug 29, 2026
 */
class SupportSweepProcessorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Supplier<StructureProcessorType<?>> NO_TYPE = () -> null;
    // Lazy, not static finals: a static field touching Blocks initialises when the class LOADS,
    // which is before @BeforeAll gets to run Bootstrap -- and the failure surfaces as a bare
    // ExceptionInInitializerError with nothing in it pointing at the cause.
    private static BlockState stone() {
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    private static BlockState dirt() {
        return Blocks.DIRT.defaultBlockState();
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    /** A box big enough that nothing in these fixtures touches its edge and seeds by accident. */
    private static final BoundingBox ROOM = new BoundingBox(-32, -32, -32, 32, 32, 32);

    private static SupportSweepProcessor sweep() {
        return new SupportSweepProcessor(NO_TYPE, BlockMatch.NONE);
    }

    /** Runs the sweep over a piece writing {@code pending}, against a level holding {@code world}. */
    private static Map<BlockPos, BlockState> run(SupportSweepProcessor sweep,
                                                 Map<BlockPos, BlockState> world,
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
        List<StructureTemplate.StructureBlockInfo> out = sweep.finalizeProcessing(
                level.level(), BlockPos.ZERO, BlockPos.ZERO, processed, processed, settings);

        Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        for (StructureTemplate.StructureBlockInfo info : out) {
            placed.put(info.pos(), info.state());
        }
        return placed;
    }

    /** Terrain at y=0 under the whole fixture, which is what a surface building stands on. */
    private static Map<BlockPos, BlockState> ground() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                world.put(new BlockPos(x, 0, z), dirt());
            }
        }
        return world;
    }

    /** A wall standing on the ground at x, running up from y=1. */
    private static void column(Map<BlockPos, BlockState> pending, int x, int z, int fromY, int toY) {
        for (int y = fromY; y <= toY; y++) {
            pending.put(new BlockPos(x, y, z), stone());
        }
    }

    // ---------- what it removes ----------

    /**
     * The failure this exists for: aging ate the middle of a wall and the course above it stayed
     * put, floating over the gap.
     */
    @Test
    void aCourseLeftFloatingByAGapInItsWallIsDropped() {
        Map<BlockPos, BlockState> pending = new HashMap<>();
        column(pending, 0, 0, 1, 3);            // the surviving foot of the wall
        // y=4 is the hole weathering made -- the piece writes nothing there at all
        column(pending, 0, 0, 5, 7);            // the course left hanging above it

        Map<BlockPos, BlockState> placed = run(sweep(), ground(), pending, ROOM);

        for (int y = 1; y <= 3; y++) {
            assertTrue(placed.containsKey(new BlockPos(0, y, 0)),
                    "y=" + y + " stands on the ground and must survive");
        }
        for (int y = 5; y <= 7; y++) {
            assertFalse(placed.containsKey(new BlockPos(0, y, 0)),
                    "y=" + y + " has no path to the ground and must be dropped");
        }
    }

    /**
     * A CASCADE costs no extra pass. The block resting on the block resting on the one that went is
     * not reached by the fill either, which is the whole reason this is phrased as reachability
     * rather than as "air below me" swept repeatedly to a fixpoint.
     */
    @Test
    void aWholeIslandGoesAtOnceHoweverTallItIs() {
        Map<BlockPos, BlockState> pending = new HashMap<>();
        column(pending, 0, 0, 1, 2);
        column(pending, 0, 0, 4, 24);           // twenty-one blocks, one gap under all of them

        Map<BlockPos, BlockState> placed = run(sweep(), ground(), pending, ROOM);

        for (int y = 4; y <= 24; y++) {
            assertFalse(placed.containsKey(new BlockPos(0, y, 0)), "y=" + y + " should have gone");
        }
        assertTrue(placed.containsKey(new BlockPos(0, 2, 0)), "the grounded foot stays");
    }

    /** Air the piece writes itself is a hole, so it neither supports nor grounds what sits on it. */
    @Test
    void aBlockRestingOnACellThePieceIsEmptyingIsNotSupported() {
        Map<BlockPos, BlockState> pending = new HashMap<>();
        column(pending, 0, 0, 1, 3);
        pending.put(new BlockPos(0, 4, 0), air()); // the template clears this cell
        column(pending, 0, 0, 5, 6);

        Map<BlockPos, BlockState> placed = run(sweep(), ground(), pending, ROOM);

        assertTrue(placed.containsKey(new BlockPos(0, 4, 0)),
                "the piece's own air is left in the list -- it may be clearing a room out");
        assertFalse(placed.containsKey(new BlockPos(0, 5, 0)),
                "and it must not hold up what sits on it");
    }

    // ---------- what it keeps, which is the harder half ----------

    /**
     * <strong>The doorway test.</strong> A lintel has air below it by design and is held from the
     * side; the naive rule deletes it and takes the top of the door with it.
     */
    @Test
    void aLintelOverADoorwaySurvives() {
        Map<BlockPos, BlockState> pending = new HashMap<>();
        column(pending, -1, 0, 1, 4);           // jamb
        column(pending, 1, 0, 1, 4);            // jamb
        pending.put(new BlockPos(0, 4, 0), stone()); // the lintel: nothing beneath it but the doorway

        Map<BlockPos, BlockState> placed = run(sweep(), ground(), pending, ROOM);

        assertTrue(placed.containsKey(new BlockPos(0, 4, 0)),
                "the lintel spans the opening and is held by the jambs either side -- a rule that"
                        + " only looks down deletes every doorway in the building");
    }

    /** A corbel or a projecting course: attached on one side, air below and beyond. */
    @Test
    void anOverhangingCourseSurvives() {
        Map<BlockPos, BlockState> pending = new HashMap<>();
        column(pending, 0, 0, 1, 6);
        for (int x = 1; x <= 3; x++) {
            pending.put(new BlockPos(x, 6, 0), stone()); // three blocks of cantilever
        }

        Map<BlockPos, BlockState> placed = run(sweep(), ground(), pending, ROOM);

        for (int x = 1; x <= 3; x++) {
            assertTrue(placed.containsKey(new BlockPos(x, 6, 0)),
                    "x=" + x + " is still attached to a wall that reaches the ground");
        }
    }

    /** Ground contact SIDEWAYS counts: a building cut into a slope is held by the slope. */
    @Test
    void terrainAgainstAFlankGroundsAPiece() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        world.put(new BlockPos(-1, 5, 0), dirt()); // hillside, and nothing underneath

        Map<BlockPos, BlockState> pending = new HashMap<>();
        pending.put(new BlockPos(0, 5, 0), stone());

        Map<BlockPos, BlockState> placed = run(sweep(), world, pending, ROOM);

        assertTrue(placed.containsKey(new BlockPos(0, 5, 0)),
                "support is contact with the world in ANY direction, not just from below");
    }

    // ---------- the chunk seam, and the config ----------

    /**
     * Vanilla clips {@code processBlockInfos} to the chunk box before a processor sees it, so a
     * building spanning a boundary arrives in slices. A block touching the edge is seeded rather
     * than judged: the half that would hold it up is in the slice this pass cannot read.
     *
     * <p>Under-removing at a seam is the direction to fail in &mdash; a false positive deletes
     * somebody's architecture, a false negative is one block that should have gone and didn't.</p>
     */
    @Test
    void aBlockAtTheChunkSeamIsSeededRatherThanDropped() {
        BoundingBox slice = new BoundingBox(0, 0, 0, 15, 32, 15);

        Map<BlockPos, BlockState> pending = new HashMap<>();
        pending.put(new BlockPos(0, 9, 8), stone()); // hard against the slice's west edge

        Map<BlockPos, BlockState> placed = run(sweep(), Map.of(), pending, slice);

        assertTrue(placed.containsKey(new BlockPos(0, 9, 8)),
                "the wall holding this up may be in the neighbouring slice, which this pass is not"
                        + " allowed to read");
    }

    /** With no box at all -- a command or a test -- nothing is seeded by unreadability. */
    @Test
    void aFloatingBlockWithNoChunkBoxIsStillDropped() {
        Map<BlockPos, BlockState> pending = new HashMap<>();
        pending.put(new BlockPos(0, 9, 0), stone());

        assertFalse(run(sweep(), Map.of(), pending, null).containsKey(new BlockPos(0, 9, 0)),
                "nothing is holding this up and every neighbour was readable");
    }

    /** `ignore` is the escape hatch for a block whose support this pass cannot see. */
    @Test
    void anIgnoredBlockIsNeverDropped() {
        SupportSweepProcessor sweep = new SupportSweepProcessor(
                NO_TYPE, new BlockMatch(List.of(Blocks.STONE_BRICKS), List.of()));

        Map<BlockPos, BlockState> pending = new HashMap<>();
        pending.put(new BlockPos(0, 9, 0), stone());

        assertTrue(run(sweep, Map.of(), pending, ROOM).containsKey(new BlockPos(0, 9, 0)));
    }

    /** A piece with nothing floating is handed back untouched, not rebuilt. */
    @Test
    void aSoundPieceReturnsTheSameListInstance() {
        Map<BlockPos, BlockState> pending = new HashMap<>();
        column(pending, 0, 0, 1, 4);

        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        ground().forEach((pos, state) -> level.level().setBlock(pos, state, 2));

        List<StructureTemplate.StructureBlockInfo> processed = new ArrayList<>();
        pending.forEach((pos, state) ->
                processed.add(new StructureTemplate.StructureBlockInfo(pos, state, null)));

        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setBoundingBox(ROOM);

        assertSame(processed, sweep().finalizeProcessing(level.level(), BlockPos.ZERO,
                        BlockPos.ZERO, processed, processed, settings),
                "no allocation when there is nothing to drop");
    }
}
