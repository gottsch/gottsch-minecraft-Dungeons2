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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code dungeons2:hanging_sweep}: a chain broken in the middle takes everything under it.
 *
 * <h2>The two halves worth pinning</h2>
 * <p><strong>It cuts below a break</strong> &mdash; the artifact the chain aging rules would
 * otherwise produce is a lantern hovering in mid-air under a stub of chain. And <strong>it keeps
 * what is still held</strong>: the run above the break, and anything resting on the floor rather
 * than hanging from a ceiling. The second is the one a cheaper rule gets wrong, because
 * {@code dungeonblocks:dungeon_lantern} is the same BLOCK standing on a floor as hanging from a
 * chain, and a {@code BlockMatch} cannot tell them apart.</p>
 *
 * <p>The fixtures here are built from vanilla blocks rather than {@code dungeonblocks:} ones for
 * the reason every test in this package does it: the mod under test is the only one on the test
 * classpath as a real registry. {@code minecraft:chain} is the real thing anyway &mdash; it is half
 * of what the shipped templates hang &mdash; and a lantern behaves identically to the dungeon one
 * here, since the sweep judges position, not the block.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
class HangingSweepProcessorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Supplier<StructureProcessorType<?>> NO_TYPE = () -> null;

    /** A box big enough that nothing in these fixtures touches its edge and anchors by accident. */
    private static final BoundingBox ROOM = new BoundingBox(-32, -32, -32, 32, 32, 32);

    // Lazy, not static finals: a static field touching Blocks initialises when the class LOADS,
    // which is before @BeforeAll gets to run Bootstrap -- and the failure surfaces as a bare
    // ExceptionInInitializerError with nothing in it pointing at the cause.
    private static BlockState stone() {
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState chain() {
        return Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    }

    private static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
    }

    /** What the shipped lists configure: the links and the fixture on the end, judged as one run. */
    private static HangingSweepProcessor sweep() {
        return new HangingSweepProcessor(NO_TYPE,
                new BlockMatch(List.of(Blocks.CHAIN, Blocks.LANTERN), List.of()));
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

        Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        for (StructureTemplate.StructureBlockInfo info : out) {
            placed.put(info.pos(), info.state());
        }
        return placed;
    }

    /**
     * A hallway's chain: a ceiling slab at y=10, three links under it, a lantern on the end, and the
     * open air of the room below. {@code broken} is the link aging turned to air, or -1 for none.
     */
    private static Map<BlockPos, BlockState> hallway(int broken) {
        Map<BlockPos, BlockState> pending = new LinkedHashMap<>();
        pending.put(new BlockPos(0, 10, 0), stone());          // the ceiling it hangs from
        for (int y = 9; y >= 7; y--) {
            pending.put(new BlockPos(0, y, 0), y == broken ? air() : chain());
        }
        pending.put(new BlockPos(0, 6, 0), lantern(true));
        for (int y = 5; y >= 1; y--) {
            pending.put(new BlockPos(0, y, 0), air());          // the room the template excavates
        }
        pending.put(new BlockPos(0, 0, 0), stone());           // its floor
        return pending;
    }

    // ---------- what it cuts ----------

    /**
     * The artifact the chain aging rules would otherwise ship: aging took the middle link, and the
     * link and lantern below it were left hanging from nothing.
     */
    @Test
    void everythingBelowABrokenLinkGoes() {
        Map<BlockPos, BlockState> placed = run(Map.of(), hallway(8), ROOM);

        assertTrue(placed.get(new BlockPos(0, 9, 0)).is(Blocks.CHAIN),
                "the link still on the ceiling is held and stays");
        assertTrue(placed.get(new BlockPos(0, 7, 0)).isAir(),
                "the link under the break is hanging from nothing");
        assertTrue(placed.get(new BlockPos(0, 6, 0)).isAir(),
                "and the lantern goes with it -- that is the whole point");
    }

    /** The break at the top takes the entire run, lantern included, not just the next link down. */
    @Test
    void aBreakAtTheCeilingTakesTheWholeRun() {
        Map<BlockPos, BlockState> placed = run(Map.of(), hallway(9), ROOM);

        for (int y = 8; y >= 6; y--) {
            assertTrue(placed.get(new BlockPos(0, y, 0)).isAir(),
                    "y=" + y + " has no path to the ceiling any more");
        }
    }

    /**
     * REMOVAL IS AIR, NOT OMISSION, which is the one place this sweep deliberately disagrees with
     * {@code SupportSweepProcessor}. A chain hangs in a room the template is excavating; drop the
     * cell from the list and the world keeps the stone the template was going to clear, leaving a
     * lump of terrain floating exactly where the lantern was.
     */
    @Test
    void aCutCellIsWrittenAsAirSoTheRoomStaysHollow() {
        Map<BlockPos, BlockState> world = new LinkedHashMap<>();
        for (int y = 1; y <= 9; y++) {
            world.put(new BlockPos(0, y, 0), stone()); // the hillside the room is cut out of
        }
        Map<BlockPos, BlockState> placed = run(world, hallway(8), ROOM);

        assertTrue(placed.containsKey(new BlockPos(0, 6, 0)),
                "the cell must still be in the list, or the template never clears it");
        assertTrue(placed.get(new BlockPos(0, 6, 0)).isAir());
    }

    // ---------- what it keeps ----------

    /** With nothing broken, an intact chain is untouched from ceiling to lantern. */
    @Test
    void anIntactChainIsLeftAlone() {
        Map<BlockPos, BlockState> pending = hallway(-1);
        Map<BlockPos, BlockState> placed = run(Map.of(), pending, ROOM);

        assertEquals(pending, placed, "an unbroken run is not the sweep's business");
    }

    /**
     * The reason a run is anchored at EITHER end rather than only above. {@code entrance_2} stands a
     * lantern on the floor, and a floor lantern is the same BLOCK as a hanging one -- so a rule that
     * only looked up would delete it, and the deletion would look like the lantern "not working".
     */
    @Test
    void aLanternStandingOnTheFloorIsNeverSwept() {
        Map<BlockPos, BlockState> pending = new LinkedHashMap<>();
        pending.put(new BlockPos(4, 0, 0), stone());
        pending.put(new BlockPos(4, 1, 0), lantern(false));
        pending.put(new BlockPos(4, 2, 0), air());

        Map<BlockPos, BlockState> placed = run(Map.of(), pending, ROOM);

        assertTrue(placed.get(new BlockPos(4, 1, 0)).is(Blocks.LANTERN),
                "it is held by the cell beneath it, which is support like any other");
    }

    /**
     * A run against a wall is NOT held by that wall, and this is exactly where
     * {@code SupportSweepProcessor} would give the wrong answer: its six-way connectivity would find
     * the wall, find the wall grounded, and keep a chain that is attached to nothing.
     */
    @Test
    void aSeveredRunBesideAWallStillGoes() {
        Map<BlockPos, BlockState> pending = hallway(8);
        for (int y = 0; y <= 10; y++) {
            pending.put(new BlockPos(1, y, 0), stone()); // the hallway wall, one block away
        }
        Map<BlockPos, BlockState> placed = run(Map.of(), pending, ROOM);

        assertTrue(placed.get(new BlockPos(0, 6, 0)).isAir(),
                "a chain is held at its ends; masonry beside it holds nothing");
    }

    /**
     * ABSENT MEANS SUPPORTED, here as everywhere else in this package. A run whose anchor is in the
     * chunk slice this pass may not read is kept: a false positive deletes a lantern somebody
     * authored, a false negative is a stub of chain that should have gone and didn't.
     */
    @Test
    void aRunReachingOutOfTheChunkBoxIsKept() {
        Map<BlockPos, BlockState> pending = new LinkedHashMap<>();
        pending.put(new BlockPos(0, 6, 0), chain());
        pending.put(new BlockPos(0, 5, 0), lantern(true));

        // A box whose floor is y=5, so the cell above the run is unreadable and the cell below is
        // out of the box too -- exactly the seam case.
        Map<BlockPos, BlockState> placed =
                run(Map.of(), pending, new BoundingBox(-32, 5, -32, 32, 6, 32));

        assertTrue(placed.get(new BlockPos(0, 6, 0)).is(Blocks.CHAIN));
        assertTrue(placed.get(new BlockPos(0, 5, 0)).is(Blocks.LANTERN));
    }

    /**
     * The world, not just the piece, can be the anchor: a prefab that hangs a chain under a ceiling
     * an earlier piece already wrote is held by it.
     */
    @Test
    void aCeilingTheWorldAlreadyHoldsIsAnAnchor() {
        Map<BlockPos, BlockState> world = Map.of(new BlockPos(0, 10, 0), stone());
        Map<BlockPos, BlockState> pending = new LinkedHashMap<>();
        pending.put(new BlockPos(0, 9, 0), chain());
        pending.put(new BlockPos(0, 8, 0), lantern(true));

        Map<BlockPos, BlockState> placed = run(world, pending, ROOM);

        assertTrue(placed.get(new BlockPos(0, 9, 0)).is(Blocks.CHAIN));
        assertTrue(placed.get(new BlockPos(0, 8, 0)).is(Blocks.LANTERN));
    }

    /**
     * A chain laid on its side is a bar &mdash; a railing, a barrier across a doorway &mdash; held
     * by whatever is at the ends of its own axis and never by the ceiling. Nothing shipped uses one
     * that way today, and the guard is here so the day somebody does, a vertical support test does
     * not quietly delete it.
     */
    @Test
    void aHorizontalChainIsNotJudgedAtAll() {
        Map<BlockPos, BlockState> pending = new LinkedHashMap<>();
        for (int x = 0; x <= 2; x++) {
            pending.put(new BlockPos(x, 5, 0),
                    Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            pending.put(new BlockPos(x, 6, 0), air());
            pending.put(new BlockPos(x, 4, 0), air());
        }
        Map<BlockPos, BlockState> placed = run(Map.of(), pending, ROOM);

        for (int x = 0; x <= 2; x++) {
            assertFalse(placed.get(new BlockPos(x, 5, 0)).isAir(),
                    "a bar is not a run and the sweep has no opinion about it");
        }
    }

    // ---------- configuration ----------

    /** An empty {@code hanging} is the honest way for a motif with no chains to turn this off. */
    @Test
    void anEmptyConfigIsANoOp() {
        Map<BlockPos, BlockState> pending = hallway(8);
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        List<StructureTemplate.StructureBlockInfo> processed = new ArrayList<>();
        pending.forEach((pos, state) ->
                processed.add(new StructureTemplate.StructureBlockInfo(pos, state, null)));
        StructurePlaceSettings settings = new StructurePlaceSettings().setBoundingBox(ROOM);

        List<StructureTemplate.StructureBlockInfo> out =
                new HangingSweepProcessor(NO_TYPE, BlockMatch.NONE).finalizeProcessing(
                        level.level(), BlockPos.ZERO, BlockPos.ZERO, processed, processed, settings);

        assertEquals(processed, out);
    }
}
