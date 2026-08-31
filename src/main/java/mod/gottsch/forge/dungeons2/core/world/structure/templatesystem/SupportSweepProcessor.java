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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.gottschcore.json.StrictCodecs;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.BlockMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Drops whatever severe weathering left hanging in mid-air: any part of a piece no longer connected
 * to the ground is not placed at all.
 *
 * <h2>The problem it exists for</h2>
 * <p>Aging a surface building hard enough to look ruined eats blocks out of the middle of walls, and
 * what is left above a hole does not fall &mdash; a structure template writes what it is told. The
 * result is a course of masonry floating over a gap, which reads as a bug rather than as a ruin.
 * The fix has to run over the FINISHED piece: aging decides one block at a time and cannot know
 * that the block it just removed was holding up the six above it.</p>
 *
 * <h2>SUPPORT IS GROUNDED CONNECTIVITY, NOT "SOMETHING DIRECTLY BELOW"</h2>
 * <p>This is the whole design, and the naive rule was tried on paper first and rejected. "Remove
 * anything with air beneath it" is one bottom-up pass and is cheaper, but it deletes <strong>every
 * lintel over a doorway, every arch, every corbel and every overhanging course</strong> &mdash; all
 * of which have air below by design, and all of which a mason built precisely because a block CAN be
 * held from the side. An entrance building would lose the top of its own door.</p>
 *
 * <p>So a block survives if it is connected, through the piece's own blocks in any of the six
 * directions, to something that reaches the ground. What that removes is exactly the thing that
 * looks wrong: an ISLAND, a chunk of wall weathering isolated from everything holding it up. What
 * it keeps is anything still attached to a wall that stands on something.</p>
 *
 * <p>It is deliberately not a physics model. A cantilever thirty blocks long is "supported" here,
 * and that is the right trade: this exists to delete debris left by a decay pass, not to second-
 * guess what a template author drew.</p>
 *
 * <h2>Cost: one flood fill, linear in the piece</h2>
 * <p>Every block is visited at most twice &mdash; once to seed, once by the fill &mdash; and its six
 * neighbours are {@code HashMap} lookups against the piece's own blocks. <strong>The level is read
 * only for cells the piece does not write</strong>, which is the thin shell around it, and only
 * until one of them turns out to be support. No iteration to a fixpoint: a cascade (a block held up
 * by a block held up by a block that just went) falls out of connectivity for free, because the fill
 * simply never reaches it. That is the reason to phrase this as reachability rather than as repeated
 * sweeps, which is the shape the naive rule forces.</p>
 *
 * <h2>What "reaches the ground" means</h2>
 * <p>A piece block is a <strong>seed</strong> if any of its six neighbours is a cell this piece does
 * not write and that cell is not air &mdash; terrain under a wall, a hillside against its flank, the
 * shaft the entrance sits on. Contact in any direction counts, not just downward: a building set
 * into a slope is held by the slope.</p>
 *
 * <h2>ABSENT MEANS SUPPORTED, and here it also means UNREADABLE</h2>
 * <p>A cell outside {@link StructurePlaceSettings#getBoundingBox()} cannot legally be read during
 * worldgen, so a block touching one is treated as seeded. That matters more here than in
 * {@code DecorationSweepProcessor}, because vanilla clips {@code processBlockInfos} to the chunk box
 * before a processor ever sees the list: <strong>a building spanning a chunk boundary is handed to
 * this processor in slices</strong>, and a wall grounded in the other slice would otherwise look
 * like an island. Seeding at the seam under-removes at chunk edges, which is the direction to fail
 * in &mdash; a false positive deletes somebody's architecture, a false negative is one block that
 * should have gone and didn't.</p>
 *
 * <h2>Removal is OMISSION, never air</h2>
 * <p>A removed block is dropped from the returned list rather than replaced with
 * {@code minecraft:air}. Writing air would <em>carve</em>: where a template block was going to
 * replace terrain (a building's foot dug into a slope), air there would punch a hole in the hill
 * that the piece never asked for. Omitting simply leaves the cell as the world already had it, which
 * is what "this block was not placed" should mean.</p>
 *
 * <p>Air the piece writes ITSELF is untouched and carries no support: it stays in the list (the
 * template may be clearing a room out with it) but is not part of the connectivity graph and never
 * seeds. So a block resting on a cell the piece is about to empty is correctly ungrounded.</p>
 *
 * <p><strong>Not a {@code LevelIndependentProcessor}</strong>: it reads the level, so under
 * {@code PieceProcessors} it belongs in the clipped second pass, exactly like
 * {@code DecorationSweepProcessor}. It must also run LAST in a list &mdash; after every aging and
 * decoration entry &mdash; since it can only judge what those passes finally left behind.</p>
 *
 * @author Mark Gottschling on Aug 29, 2026
 */
public class SupportSweepProcessor extends StructureProcessor {

    private final Supplier<StructureProcessorType<?>> type;

    /**
     * Blocks this sweep will never remove, however ungrounded they look.
     *
     * <p>Empty by default, and most lists want it that way. It exists for the block whose support
     * this processor cannot see &mdash; anything a template hangs off a neighbouring piece, or a
     * marker another processor is going to resolve into something else after this runs.</p>
     */
    private final BlockMatch ignore;

    public SupportSweepProcessor(Supplier<StructureProcessorType<?>> type, BlockMatch ignore) {
        this.type = type;
        this.ignore = ignore;
    }

    /** Strict throughout, per backlog #31: an undeclared key is a load error, not a shrug. */
    public static Codec<SupportSweepProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "ignore", BlockMatch.NONE)
                        .forGetter(processor -> processor.ignore)
        ).apply(instance, ignore -> new SupportSweepProcessor(type, ignore)));
    }

    /** Nothing to decide per block: the whole point is that support is a property of the piece. */
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level, BlockPos piecePos, BlockPos relativePos,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        return current;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level, BlockPos piecePos, BlockPos relativePos,
            List<StructureTemplate.StructureBlockInfo> originalBlocks,
            List<StructureTemplate.StructureBlockInfo> processedBlocks,
            StructurePlaceSettings settings) {

        // What this piece is about to write, last-write-wins -- the same `pending` map every other
        // finalizeProcessing in this package builds, and for the same reason: a neighbour lookup
        // must see what the cell is ABOUT to be, not what the world still holds.
        Map<BlockPos, BlockState> pending = new HashMap<>(processedBlocks.size());
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            pending.put(info.pos(), info.state());
        }

        BoundingBox chunkBox = settings.getBoundingBox();
        Set<BlockPos> grounded = flood(pending, level, chunkBox);

        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(processedBlocks.size());
        int dropped = 0;
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            if (info.state().isAir() || ignore.matches(info.state())
                    || grounded.contains(info.pos())) {
                out.add(info);
                continue;
            }
            dropped++;
            //   grep "D2-SUPPORT" run/logs/dungeons2.log
            Dungeons.LOGGER.debug("[D2-SUPPORT] dropping {} at {} -- no path to the ground",
                    info.state(), info.pos().toShortString());
        }
        if (dropped == 0) {
            return processedBlocks;
        }
        Dungeons.LOGGER.info("[D2-SUPPORT] dropped {} floating block(s) of {} at {}",
                dropped, processedBlocks.size(), piecePos.toShortString());
        return out;
    }

    /**
     * Every piece block with a path to the ground.
     *
     * <p>Seeds first, then one breadth-first fill through the piece's own blocks. A block is
     * enqueued exactly once, so this is linear in the piece and the queue never holds a duplicate.
     * The level is touched only where a neighbour is not a piece cell.</p>
     */
    private Set<BlockPos> flood(Map<BlockPos, BlockState> pending, ServerLevelAccessor level,
                                @Nullable BoundingBox chunkBox) {
        Set<BlockPos> grounded = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        // Adjacent piece blocks share neighbours, so the same world cell is asked about up to six
        // times. Reads are the expensive part of this pass -- everything else is a map lookup --
        // so they are memoised for the piece. Measured on a 64x64x48 shell, this is most of the
        // cost of the whole sweep.
        Map<BlockPos, Boolean> reads = new HashMap<>();

        for (Map.Entry<BlockPos, BlockState> entry : pending.entrySet()) {
            if (entry.getValue().isAir()) {
                continue; // air the piece writes is a hole, not a support and not a member
            }
            if (seeded(entry.getKey(), pending, level, chunkBox, reads)
                    && grounded.add(entry.getKey())) {
                queue.add(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = pos.relative(direction);
                BlockState state = pending.get(neighbour);
                if (state == null || state.isAir()) {
                    continue; // not this piece's block, or a cell it is emptying
                }
                if (grounded.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return grounded;
    }

    /**
     * Whether this block touches the world &mdash; terrain in any of the six directions, or a cell
     * this pass is not allowed to read.
     *
     * <p>Cells the piece writes itself are skipped here on purpose: they are members of the graph,
     * not ground. Whether they are grounded is what the fill is for, and treating one as ground
     * would let an island seed itself.</p>
     */
    private static boolean seeded(BlockPos pos, Map<BlockPos, BlockState> pending,
                                  ServerLevelAccessor level, @Nullable BoundingBox chunkBox,
                                  Map<BlockPos, Boolean> reads) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (pending.containsKey(neighbour)) {
                continue;
            }
            if (!readable(neighbour, chunkBox)) {
                return true; // the chunk seam: the other slice may well be holding this up
            }
            Boolean solid = reads.get(neighbour);
            if (solid == null) {
                solid = !level.getBlockState(neighbour).isAir();
                reads.put(neighbour.immutable(), solid);
            }
            if (solid) {
                return true;
            }
        }
        return false;
    }

    /** Reads outside the chunk box are illegal during worldgen; a null box is a single-shot caller. */
    private static boolean readable(BlockPos pos, @Nullable BoundingBox chunkBox) {
        return chunkBox == null || chunkBox.isInside(pos);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return type.get();
    }
}
