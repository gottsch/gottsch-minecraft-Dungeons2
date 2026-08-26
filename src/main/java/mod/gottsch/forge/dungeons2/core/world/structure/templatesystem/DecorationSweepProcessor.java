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
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Clears decoration that <em>this</em> piece's blocks are about to strand, in the cells around it
 * that another piece already wrote.
 *
 * <h2>The bug this exists for</h2>
 * <p>Found in game 2026-08-26, at {@code -38 1 1801}: a {@code dungeonblocks:mold} with
 * {@code south=true} clinging to a {@code polished_andesite_quarter_facade_block} &mdash; a quarter
 * slab, so the mold's quad hung 12/16 of a block off the only thing it was supposed to be growing
 * on. {@code DecorationProcessor} guards against exactly that ({@code isFullCube}, not
 * {@code canOcclude}, precisely so a facade fails it), and the guard was working.
 *
 * <p>What defeated it was <strong>ownership, not the test</strong>. That wall row is shared: the
 * piece north of it and the {@code 5x11_hallway_2} prefab south of it both write {@code z=1802}.
 * The northern piece rendered first, its decoration pass saw its own full-cube wall block there,
 * grew the mold against it &mdash; and then the prefab, authored and therefore rendered last
 * (backlog #18's standing rule), re-skinned the cell with the facade. Every input the processor
 * validated was true when it ran and false by the time a player saw it. No amount of care inside a
 * piece-local processor can see this coming: the piece that breaks the support is a different
 * piece, running later, and it is the only one in a position to notice.
 *
 * <h2>So the sweep runs from the breaking side, before it breaks anything</h2>
 * <p>{@code finalizeProcessing} runs <strong>before</strong> the piece's blocks are written &mdash;
 * {@code StructureTemplate.processBlockInfos} returns the list that {@code placeInWorld} then
 * places. That timing is what makes this cheap and legal: the level still holds the previous
 * piece's work, the list holds what is about to land on top of it, and comparing the two is a plain
 * map lookup. Nothing here writes to the world directly. Positions to repair are <em>appended to
 * the returned list</em>, so they reach the world through vanilla's own placement loop, with
 * vanilla's own chunk clipping, after the piece's own blocks. Last write stands.
 *
 * <h2>Only cells this piece does not own</h2>
 * <p>A neighbour the piece is itself writing needs no repair &mdash; it is about to be overwritten
 * regardless. So the candidate set is the thin shell of positions adjacent to the piece but outside
 * it, which for a room is its surface and not its volume. That is also what keeps the cost down:
 * every interior cell is rejected by a {@code HashMap} hit with no level read at all.
 *
 * <p>Reads are clipped to {@link StructurePlaceSettings#getBoundingBox()}, the chunk box, for the
 * same reason vanilla's {@code RuleProcessor} clips its {@code location_predicate}: reading outside
 * the current {@code WorldGenRegion} during worldgen is illegal. A null box is the honest signal of
 * a single-shot caller (a command, a test) with a real level and no chunk to clip to.
 *
 * <h2>Absent means supported</h2>
 * <p>Every support test treats "I cannot see that cell" as support, never as a missing one. It
 * inherits the reasoning from {@code DecorationProcessor.hasSupport}: a false positive deletes
 * something somebody authored, a false negative is one mold quad that should have gone and didn't.
 * The sweep also refuses to touch any block its own config does not name &mdash; it will never
 * remove architecture just because it fails a support test, only the growth and webs the decoration
 * pass is capable of having placed.
 *
 * <h2>The config mirrors the decoration rule it inverts</h2>
 * <p>Each field here answers "what did {@code dungeons2:decoration} require when it placed this?"
 * for one of its behaviours, so the two halves have to name the same blocks. They are separate
 * fields rather than one shared object because a processor cannot see its siblings in the list.
 * {@code DecorationSweepParityTest} reads both entries out of each shipped {@code processor_list}
 * and fails the build when they drift.
 *
 * <p><strong>Not a {@code LevelIndependentProcessor}</strong>: it reads the level, so under
 * {@code PieceProcessors} it belongs in the clipped second pass. That is correct rather than merely
 * tolerable &mdash; the bug is authored-over-generated and the fix lands on the authored piece,
 * which goes down vanilla's unsplit template path anyway.
 *
 * @author Mark Gottschling on Aug 26, 2026
 */
public class DecorationSweepProcessor extends StructureProcessor {

    private final Supplier<StructureProcessorType<?>> type;

    /** Multiface growth ({@code wall_growth}): every set face needs a full cube behind it. */
    private final BlockMatch growth;
    /** Plain {@code cobwebs}: at least one horizontally adjacent solid block. */
    private final BlockMatch webs;
    /** {@code corner_cobwebs}: a full-cube wall behind, plus the floor or ceiling its half names. */
    private final BlockMatch cornerWebs;
    /** What {@code floor_growth} / {@code hanging_growth} root in. Must match the decoration rule's. */
    private final BlockMatch dirt;
    /** {@code floor_growth}: needs {@link #dirt} below. */
    private final BlockMatch floorGrowth;
    /** {@code hanging_growth}: needs {@link #dirt} above. */
    private final BlockMatch hangingGrowth;
    /** The {@code unsupported} corbels and ledges: need something non-air behind their facing. */
    private final BlockMatch unsupported;

    public DecorationSweepProcessor(Supplier<StructureProcessorType<?>> type, BlockMatch growth,
                                    BlockMatch webs, BlockMatch cornerWebs, BlockMatch dirt,
                                    BlockMatch floorGrowth, BlockMatch hangingGrowth,
                                    BlockMatch unsupported) {
        this.type = type;
        this.growth = growth;
        this.webs = webs;
        this.cornerWebs = cornerWebs;
        this.dirt = dirt;
        this.floorGrowth = floorGrowth;
        this.hangingGrowth = hangingGrowth;
        this.unsupported = unsupported;
    }

    /**
     * Every field defaults to {@link BlockMatch#NONE}, i.e. "sweep nothing of this kind". A motif
     * that turns a decoration behaviour off simply leaves the matching field out here. Strict
     * throughout, per backlog #31: an undeclared key or a malformed value is a load error, not a
     * silently ignored one.
     */
    public static Codec<DecorationSweepProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "growth", BlockMatch.NONE)
                        .forGetter(p -> p.growth),
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "webs", BlockMatch.NONE)
                        .forGetter(p -> p.webs),
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "corner_webs", BlockMatch.NONE)
                        .forGetter(p -> p.cornerWebs),
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "dirt", BlockMatch.NONE)
                        .forGetter(p -> p.dirt),
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "floor_growth", BlockMatch.NONE)
                        .forGetter(p -> p.floorGrowth),
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "hanging_growth", BlockMatch.NONE)
                        .forGetter(p -> p.hangingGrowth),
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "unsupported", BlockMatch.NONE)
                        .forGetter(p -> p.unsupported)
        ).apply(instance, (growth, webs, cornerWebs, dirt, floorGrowth, hangingGrowth, unsupported) ->
                new DecorationSweepProcessor(type, growth, webs, cornerWebs, dirt, floorGrowth,
                        hangingGrowth, unsupported)));
    }

    /** Nothing to do per block; the whole behaviour needs the finished list. */
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

        if (!hasWork()) {
            return processedBlocks;
        }

        // What this piece is about to write. A later duplicate wins, matching the "last write
        // stands" semantics of placing the list in order -- same rule DecorationProcessor uses.
        Map<BlockPos, BlockState> pending = new HashMap<>(processedBlocks.size());
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            pending.put(info.pos(), info.state());
        }

        BoundingBox chunkBox = settings.getBoundingBox();
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (BlockPos owned : pending.keySet()) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = owned.relative(direction);
                if (!pending.containsKey(neighbour) && readable(neighbour, chunkBox)) {
                    candidates.add(neighbour);
                }
            }
        }

        List<StructureTemplate.StructureBlockInfo> repairs = new ArrayList<>();
        for (BlockPos pos : candidates) {
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                continue;
            }
            BlockState repaired = repair(current, pos, pending, level, chunkBox);
            if (repaired == null || repaired.equals(current)) {
                continue;
            }
            // Vanilla applies mirror+rotation to whatever a processor returns, and `current` came
            // out of the world already transformed -- so it has to be un-transformed on the way
            // back in or it lands rotated twice. Air is invariant; a partly-cleared multiface is
            // not. Same problem, same fix, as DecorationProcessor.storedFor.
            BlockState stored = stored(repaired, settings);
            if (stored == null) {
                continue;
            }
            //   grep "D2-SWEEP" run/logs/dungeons2.log
            Dungeons.LOGGER.debug("[D2-SWEEP] {} -> {} at {} (orphaned by this piece)",
                    current, stored, pos.toShortString());
            repairs.add(new StructureTemplate.StructureBlockInfo(pos, stored, null));
        }

        if (repairs.isEmpty()) {
            return processedBlocks;
        }
        // Appended, not merged: these must be written AFTER the piece's own blocks, since it is
        // the piece's own blocks that decide whether the repair was needed.
        List<StructureTemplate.StructureBlockInfo> out =
                new ArrayList<>(processedBlocks.size() + repairs.size());
        out.addAll(processedBlocks);
        out.addAll(repairs);
        return out;
    }

    /** True if any behaviour is configured, so an idle sweep costs one check. */
    private boolean hasWork() {
        return !growth.isEmpty() || !webs.isEmpty() || !cornerWebs.isEmpty()
                || !floorGrowth.isEmpty() || !hangingGrowth.isEmpty() || !unsupported.isEmpty();
    }

    /**
     * What {@code state} should become, or null to leave it alone entirely.
     *
     * <p>The first matching behaviour decides. A block this config does not name returns null
     * without any support test being run at all &mdash; the sweep removes decoration, never
     * architecture.
     */
    @Nullable
    private BlockState repair(BlockState state, BlockPos pos, Map<BlockPos, BlockState> pending,
                              ServerLevelAccessor level, @Nullable BoundingBox chunkBox) {
        if (growth.matches(state) && state.getBlock() instanceof MultifaceBlock) {
            return repairMultiface(state, pos, pending, level, chunkBox);
        }
        if (cornerWebs.matches(state)) {
            return cornerWebSurvives(state, pos, pending, level, chunkBox) ? state : air();
        }
        if (webs.matches(state)) {
            return webSurvives(pos, pending, level, chunkBox) ? state : air();
        }
        if (floorGrowth.matches(state)) {
            return rootedIn(pos.below(), pending, level, chunkBox) ? state : air();
        }
        if (hangingGrowth.matches(state)) {
            return rootedIn(pos.above(), pending, level, chunkBox) ? state : air();
        }
        if (unsupported.matches(state)) {
            Direction facing = facingOf(state);
            if (facing == null) {
                return null;
            }
            // "Behind", and only behind -- a corbel is bracketed onto a wall and takes support
            // from nothing else. DecorationProcessor.hasSupport explains the convention.
            BlockState behind = look(pos.relative(facing.getOpposite()), pending, level, chunkBox);
            return behind == null || !behind.isAir() ? state : air();
        }
        return null;
    }

    /** Drops the faces whose support this piece is about to remove; air when none are left. */
    private BlockState repairMultiface(BlockState state, BlockPos pos,
                                       Map<BlockPos, BlockState> pending, ServerLevelAccessor level,
                                       @Nullable BoundingBox chunkBox) {
        BlockState result = state;
        int faces = 0;
        for (Direction direction : Direction.values()) {
            BooleanProperty face = MultifaceBlock.getFaceProperty(direction);
            if (!state.hasProperty(face) || !state.getValue(face)) {
                continue;
            }
            BlockState support = look(pos.relative(direction), pending, level, chunkBox);
            if (support == null || isFullCube(support)) {
                faces++;
                continue;
            }
            result = result.setValue(face, false);
        }
        return faces == 0 ? air() : result;
    }

    /** A corner web lies against two perpendicular surfaces, so both have to be full cubes. */
    private boolean cornerWebSurvives(BlockState state, BlockPos pos,
                                      Map<BlockPos, BlockState> pending, ServerLevelAccessor level,
                                      @Nullable BoundingBox chunkBox) {
        Direction facing = facingOf(state);
        if (facing == null || facing.getAxis().isVertical()) {
            // No horizontal facing means nothing to test against; leave it be.
            return true;
        }
        if (!fullCubeAt(pos.relative(facing.getOpposite()), pending, level, chunkBox)) {
            return false;
        }
        if (!state.hasProperty(BlockStateProperties.HALF)) {
            // A web with no `half` is only ever placed at a ceiling junction -- see
            // DecorationProcessor.maybeCornerCobweb.
            return fullCubeAt(pos.above(), pending, level, chunkBox);
        }
        return state.getValue(BlockStateProperties.HALF) == Half.TOP
                ? fullCubeAt(pos.above(), pending, level, chunkBox)
                : fullCubeAt(pos.below(), pending, level, chunkBox);
    }

    /** A plain web strings itself across a gap: anything non-air beside it will do. */
    private boolean webSurvives(BlockPos pos, Map<BlockPos, BlockState> pending,
                                ServerLevelAccessor level, @Nullable BoundingBox chunkBox) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState neighbour = look(pos.relative(direction), pending, level, chunkBox);
            if (neighbour == null || isSolid(neighbour)) {
                return true;
            }
        }
        return false;
    }

    private boolean rootedIn(BlockPos pos, Map<BlockPos, BlockState> pending,
                             ServerLevelAccessor level, @Nullable BoundingBox chunkBox) {
        BlockState state = look(pos, pending, level, chunkBox);
        return state == null || dirt.matches(state);
    }

    private boolean fullCubeAt(BlockPos pos, Map<BlockPos, BlockState> pending,
                               ServerLevelAccessor level, @Nullable BoundingBox chunkBox) {
        BlockState state = look(pos, pending, level, chunkBox);
        return state == null || isFullCube(state);
    }

    /**
     * The state {@code pos} will hold once this piece has been placed, or null when the sweep
     * cannot see it &mdash; which every caller reads as "supported", never as "unsupported".
     */
    @Nullable
    private BlockState look(BlockPos pos, Map<BlockPos, BlockState> pending,
                            ServerLevelAccessor level, @Nullable BoundingBox chunkBox) {
        BlockState written = pending.get(pos);
        if (written != null) {
            return written;
        }
        return readable(pos, chunkBox) ? level.getBlockState(pos) : null;
    }

    /** Reading outside the chunk box during worldgen is illegal; a null box is a single-shot call. */
    private static boolean readable(BlockPos pos, @Nullable BoundingBox chunkBox) {
        return chunkBox == null || chunkBox.isInside(pos);
    }

    /**
     * The state to <strong>store</strong> so vanilla's {@code state.mirror(m).rotate(r)} at write
     * time yields {@code wanted}; null when no state does. Searches rather than inverts, for the
     * reason {@code DecorationProcessor.storedFor} gives: whether a block transforms a given
     * property at all is up to the block, and this class cannot know which do.
     */
    @Nullable
    private static BlockState stored(BlockState wanted, StructurePlaceSettings settings) {
        Mirror mirror = settings.getMirror();
        Rotation rotation = settings.getRotation();
        if (mirror == Mirror.NONE && rotation == Rotation.NONE) {
            return wanted;
        }
        for (BlockState candidate : wanted.getBlock().getStateDefinition().getPossibleStates()) {
            if (candidate.mirror(mirror).rotate(rotation).equals(wanted)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The block's {@code facing} direction, or null. Looked up by NAME rather than against
     * {@code BlockStateProperties.FACING}, so it covers the 4-way and 6-way vanilla properties and
     * GottschCore's own {@code FacingBlock}, which declares its own. Same lookup, same reason, as
     * {@code DecorationProcessor.facingOf}.
     */
    @Nullable
    private static Direction facingOf(BlockState state) {
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            if (entry.getKey().getName().equals("facing")
                    && entry.getValue() instanceof Direction facing) {
                return facing;
            }
        }
        return null;
    }

    /**
     * Whether the block fills its cell. <strong>Deliberately duplicated</strong> from
     * {@code DecorationProcessor.isFullCube}, which is private: this is the exact predicate whose
     * answer changed between the two pieces, so the sweep has to ask it the same way or it repairs
     * the wrong cells. {@code canOcclude()} is NOT this test -- it is true of stairs, slabs and
     * DungeonBlocks' facade shapes, and a facade passing it is the whole bug.
     * {@code EmptyBlockGetter} rather than the real level is what keeps the dynamic-shape fallback
     * from reading the world.
     */
    private static boolean isFullCube(BlockState state) {
        return state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** "Something is there" -- what a web strings itself across. Not a shape test. */
    private static boolean isSolid(BlockState state) {
        return state.canOcclude() && state.getFluidState().isEmpty();
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return type.get();
    }
}
