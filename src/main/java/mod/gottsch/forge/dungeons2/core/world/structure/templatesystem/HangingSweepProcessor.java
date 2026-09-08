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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
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
 * Cuts a hanging run loose below the link that weathering ate: chain, and whatever the chain was
 * carrying, is removed from the break downward.
 *
 * <h2>The problem it exists for</h2>
 * <p>{@code minecraft:chain} and {@code dungeonblocks:swinging_chain} now age to air like everything
 * else in the room, and a chain is the one element in this dungeon where a hole is not a hole. Aging
 * decides one block at a time; when it takes the third link of a five-link run, the two links below
 * it and the lantern on the end do not fall &mdash; a structure template writes what it is told
 * &mdash; and the player sees a lantern hovering in the middle of the room under a stub of chain.
 * That reads as a bug rather than as a ruin, which is the whole reason the chains were not aged
 * before.</p>
 *
 * <h2>SUPPORT HERE IS VERTICAL, and that is what makes it a different processor from
 * {@code SupportSweepProcessor}</h2>
 * <p>That sweep asks whether a block is connected <em>in any of six directions</em> to something
 * that reaches the ground, precisely so that it keeps lintels, arches and corbels &mdash; things a
 * mason built to be held from the side. A chain is the opposite case. It is held at its ends and by
 * nothing else, so six-way connectivity is exactly the wrong test: it would keep every severed run
 * that happened to hang beside a wall, which in these templates is most of them (chains sit in
 * hallways, a block or two off the masonry). Running both is not a contradiction &mdash; one judges
 * architecture, this one judges the things architecture hangs from itself.</p>
 *
 * <h2>The rule</h2>
 * <p>A run is the contiguous column of {@link #hanging} blocks sharing an x/z. It survives if
 * <strong>either end</strong> is against something solid: the cell above its topmost link, or the
 * cell below its bottommost. Both ends, not just the top, because {@code hanging} is matched by
 * BLOCK and a {@code dungeonblocks:dungeon_lantern} is the same block whether it hangs from a
 * ceiling or stands on a floor &mdash; the floor lantern in {@code entrance_2} is held by the cell
 * beneath it and must not be swept. Testing both ends costs nothing and is also simply true of a
 * chain: one strung from the floor up is as supported as one hung from the ceiling down.</p>
 *
 * <p>An aged-out link is {@code minecraft:air} in the block list, so it is not a member of any run,
 * and the gap it leaves splits one run into two. The upper keeps the ceiling; the lower has air
 * above and air below and goes. The cascade is free &mdash; there is no iteration to a fixpoint,
 * because the runs were already cut apart before anything was judged.</p>
 *
 * <h2>REMOVAL IS AIR, NOT OMISSION &mdash; the opposite of {@code SupportSweepProcessor}</h2>
 * <p>That sweep drops a block from the list so the world keeps whatever it already had, because the
 * cells it clears are a building's foot dug into a hillside and writing air there would carve the
 * hill. A chain hangs in the middle of a room the template is <em>excavating</em>, and the only
 * thing clearing the surrounding stone is the template's own air. Omit a chain cell and the player
 * gets a lump of untouched terrain floating where the lantern was &mdash; strictly worse than the
 * artifact this exists to remove. So the entry stays in the list, with an air state.</p>
 *
 * <h2>{@code top} is fixed up on the survivor</h2>
 * <p>{@code SwingingChainBlock} keeps its BlockEntity &mdash; and therefore its swing &mdash; only
 * on the segment whose {@code top} is set, and the author set that on the link against the ceiling.
 * When aging takes that link, the run below it would render as a dead chain. So a surviving member
 * whose block declares a boolean {@code top} has it rewritten to say whether the cell above is still
 * the same block. Deliberately narrow: it fires only on blocks this processor's own config named,
 * and only for a property with that exact name and type.</p>
 *
 * <p><strong>Not a {@code LevelIndependentProcessor}</strong>: it reads the level for the anchor
 * cells the piece does not itself write, so under {@code PieceProcessors} it belongs in the clipped
 * second pass, exactly like the other two sweeps. Clipping is harmless here in a way worth stating:
 * a worldgen chunk box spans the full build height, so a vertical run is never split across two
 * passes, and the anchor cell above or below it is always in the same column and therefore the same
 * box. It must be authored AFTER every aging entry, since the break it cascades from is what those
 * left behind.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public class HangingSweepProcessor extends StructureProcessor {

    /** The property {@code SwingingChainBlock} hangs its BlockEntity off; see the class doc. */
    private static final String TOP = "top";

    /** {@code minecraft:chain} states one; a chain that is not {@code y} is a bar, not a run. */
    private static final String AXIS = "axis";

    private final Supplier<StructureProcessorType<?>> type;

    /**
     * Everything that takes its support from the cell above or below and from nothing else &mdash;
     * the chains, and the fixtures hung on the end of one.
     *
     * <p>Empty means the sweep does nothing at all, which is what a motif with no chains wants.</p>
     */
    private final BlockMatch hanging;

    public HangingSweepProcessor(Supplier<StructureProcessorType<?>> type, BlockMatch hanging) {
        this.type = type;
        this.hanging = hanging;
    }

    /** Strict throughout, per backlog #31: an undeclared key is a load error, not a shrug. */
    public static Codec<HangingSweepProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "hanging", BlockMatch.NONE)
                        .forGetter(processor -> processor.hanging)
        ).apply(instance, hanging -> new HangingSweepProcessor(type, hanging)));
    }

    /** Nothing to decide per block: a run is a property of the finished list. */
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

        if (hanging.isEmpty()) {
            return processedBlocks;
        }

        // What this piece is about to write, last-write-wins -- the same `pending` map every other
        // finalizeProcessing in this package builds, and for the same reason: a neighbour lookup
        // must see what the cell is ABOUT to be, not what the world still holds.
        Map<BlockPos, BlockState> pending = new HashMap<>(processedBlocks.size());
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            pending.put(info.pos(), info.state());
        }
        Set<BlockPos> members = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : pending.entrySet()) {
            if (hanging.matches(entry.getValue()) && hangs(entry.getValue())) {
                members.add(entry.getKey());
            }
        }
        if (members.isEmpty()) {
            return processedBlocks;
        }

        BoundingBox chunkBox = settings.getBoundingBox();
        Set<BlockPos> held = held(members, pending, level, chunkBox);

        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(processedBlocks.size());
        int cut = 0;
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            if (!members.contains(info.pos())) {
                out.add(info);
                continue;
            }
            if (!held.contains(info.pos())) {
                cut++;
                //   grep "D2-HANG" run/logs/dungeons2.log
                Dungeons.LOGGER.debug("[D2-HANG] cutting {} at {} -- the run above it is broken",
                        info.state(), info.pos().toShortString());
                // Air rather than omission: this cell is inside the volume the template is
                // excavating, and only the template's own air clears it. See the class doc.
                out.add(new StructureTemplate.StructureBlockInfo(
                        info.pos(), Blocks.AIR.defaultBlockState(), null));
                continue;
            }
            out.add(retopped(info, members, pending));
        }

        if (cut > 0) {
            Dungeons.LOGGER.info("[D2-HANG] cut {} unsupported hanging block(s) of {} at {}",
                    cut, members.size(), piecePos.toShortString());
        }
        // `out` even when nothing was cut: a `top` fixup may still have rewritten a survivor, which
        // is what happens when aging took a run's own topmost link and left the rest hanging.
        return out;
    }

    /**
     * Every member whose run is anchored at one end or the other.
     *
     * <p>Seeds are the members touching something solid above or below; the fill then walks the run
     * vertically in both directions. A member is enqueued exactly once, so this is linear in the
     * members and the queue never holds a duplicate.</p>
     */
    private static Set<BlockPos> held(Set<BlockPos> members, Map<BlockPos, BlockState> pending,
                                      ServerLevelAccessor level, @Nullable BoundingBox chunkBox) {
        Set<BlockPos> held = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        for (BlockPos pos : members) {
            if (anchored(pos, members, pending, level, chunkBox) && held.add(pos)) {
                queue.add(pos);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (BlockPos neighbour : new BlockPos[]{pos.above(), pos.below()}) {
                if (members.contains(neighbour) && held.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return held;
    }

    /**
     * Whether this member is an END of its run resting against something solid.
     *
     * <p>A member with another member above it is not judged here at all &mdash; whether THAT one is
     * held is what the fill answers. Only the cell past the end of a run counts, and only when it is
     * not air.</p>
     */
    private static boolean anchored(BlockPos pos, Set<BlockPos> members,
                                    Map<BlockPos, BlockState> pending, ServerLevelAccessor level,
                                    @Nullable BoundingBox chunkBox) {
        return solidAt(pos.above(), members, pending, level, chunkBox)
                || solidAt(pos.below(), members, pending, level, chunkBox);
    }

    /**
     * Whether {@code pos} will hold something a run can hang from or stand on.
     *
     * <p>A member is not an anchor (the fill decides those), and air is not. A cell this pass may
     * not legally read is treated as an anchor, exactly as every other support test in this package
     * does: a false positive deletes a lantern somebody authored, a false negative is a stub of
     * chain that should have gone and didn't.</p>
     */
    private static boolean solidAt(BlockPos pos, Set<BlockPos> members,
                                   Map<BlockPos, BlockState> pending, ServerLevelAccessor level,
                                   @Nullable BoundingBox chunkBox) {
        if (members.contains(pos)) {
            return false;
        }
        BlockState written = pending.get(pos);
        if (written != null) {
            return !written.isAir();
        }
        if (chunkBox != null && !chunkBox.isInside(pos)) {
            return true; // unreadable during worldgen; absent means supported
        }
        return !level.getBlockState(pos).isAir();
    }

    /**
     * The survivor with its {@code top} restated, or the entry unchanged.
     *
     * <p>{@code top} is true exactly when the cell above is not the same block &mdash; the same test
     * {@code SwingingChainBlock.updateShape} applies, restated here because a structure template
     * never runs it.</p>
     */
    private static StructureTemplate.StructureBlockInfo retopped(
            StructureTemplate.StructureBlockInfo info, Set<BlockPos> members,
            Map<BlockPos, BlockState> pending) {

        BooleanProperty top = topOf(info.state());
        if (top == null) {
            return info;
        }
        BlockPos above = info.pos().above();
        BlockState over = members.contains(above) ? pending.get(above) : null;
        boolean isTop = over == null || !over.is(info.state().getBlock());
        if (info.state().getValue(top) == isTop) {
            return info;
        }
        // Safe to rewrite in place: `top` is not a directional property, so vanilla's
        // state.mirror().rotate() at placement time leaves it exactly as written. That is the trap
        // DecorationSweepProcessor.stored() exists for, and this is why it does not apply here.
        return new StructureTemplate.StructureBlockInfo(
                info.pos(), info.state().setValue(top, isTop), info.nbt());
    }

    /**
     * Whether this state is standing in a run at all, as opposed to lying across one.
     *
     * <p>{@code minecraft:chain} is a bar as well as a chain: laid on its side it is a railing, a
     * winch drum, a barrier across a doorway &mdash; held by whatever is at each end of its own
     * axis, and never by the ceiling. Nothing shipped uses one that way today (every chain in every
     * template is {@code axis=y}), but the day somebody does, a vertical support test would quietly
     * delete it, and the deletion would look like the block "just not working". So a member states
     * its own axis or it is not judged.</p>
     */
    private static boolean hangs(BlockState state) {
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            if (entry.getKey().getName().equals(AXIS)) {
                return entry.getValue() == Direction.Axis.Y;
            }
        }
        return true; // no axis to state: swinging_chain, the lanterns, anything hung by design
    }

    /** The block's boolean {@code top}, or null when it declares no such property. */
    @Nullable
    private static BooleanProperty topOf(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(TOP) && property instanceof BooleanProperty booleanTop) {
                return booleanTop;
            }
        }
        return null;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return type.get();
    }
}
