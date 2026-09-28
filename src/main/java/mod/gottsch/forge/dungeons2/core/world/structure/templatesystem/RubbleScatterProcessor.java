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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.gottschcore.json.StrictCodecs;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.BlockMatch;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Rubble at the foot of a broken wall: where weathering knocked a hole in the masonry, the stone
 * that came out lies on the floor below it.
 *
 * <h2>What counts as broken</h2>
 * <p>A cell whose AUTHORED block was something else and whose aged block is one of {@link #broken}
 * &mdash; air, or rubble &mdash; with the piece's own non-air blocks directly above AND below it.
 * The sandwich is what makes it a hole in a WALL: a marker that empties on the floor (a pot, a chest)
 * has room air above it, and a ceiling block that aged out has nothing of the piece above. Found by
 * comparing the original list with the processed one, so this has to be authored AFTER every aging
 * entry, whose output it reads.</p>
 *
 * <h2>Where the rubble lands</h2>
 * <p>For each broken cell, each of its four horizontal neighbour columns is walked DOWN from the
 * hole's height to the first cell the piece writes as air with something solid under it &mdash; the
 * walkable cell at the wall's foot on that side, whether the hole is at knee height or up under the
 * ceiling. Only the piece's own air is ever used, so the far side of an outer wall (terrain, not in
 * the list) gets nothing, and a shared wall scatters into whichever piece's aging broke it.</p>
 *
 * <p>Every foot cell collects one point per broken cell above it. It takes a scatter with
 * {@link #probability}, and the scatter's {@code chips} (when the block has that property) is the
 * points plus a coin toss, capped at the block's maximum &mdash; so the more of a wall came down, the
 * thicker the pile.</p>
 *
 * <h2>Chunk safety</h2>
 * <p>A {@link LevelIndependentProcessor}: it reads only the piece's lists, which that marker hands
 * over whole and unclipped, and every roll is seeded from the foot cell's world position. Every pass
 * over the piece therefore decides every cell the same way, and vanilla's own placement clip does the
 * rest.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public class RubbleScatterProcessor extends StructureProcessor implements LevelIndependentProcessor {

    private static final String CHIPS = "chips";
    private static final String DEFAULT_SCATTER = "dungeonblocks:rubble_scatter";
    private static final double DEFAULT_PROBABILITY = 0.5;
    private static final int DEFAULT_MAX_DROP = 8;

    private final Supplier<StructureProcessorType<?>> type;
    /** What a wall cell must have aged INTO to count as broken. Empty = this processor does nothing. */
    private final BlockMatch broken;
    private final String scatterBlock;
    private final double probability;
    /** How far below a hole the floor may be and still catch what fell out of it. */
    private final int maxDrop;

    public RubbleScatterProcessor(Supplier<StructureProcessorType<?>> type, BlockMatch broken,
                                  String scatterBlock, double probability, int maxDrop) {
        this.type = type;
        this.broken = broken;
        this.scatterBlock = scatterBlock;
        this.probability = probability;
        this.maxDrop = maxDrop;
    }

    /** Strict throughout, per backlog #31. */
    public static Codec<RubbleScatterProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                StrictCodecs.strictOptionalFieldOf(BlockMatch.CODEC, "broken", BlockMatch.NONE)
                        .forGetter(p -> p.broken),
                StrictCodecs.strictOptionalFieldOf(Codec.STRING, "scatter_block", DEFAULT_SCATTER)
                        .forGetter(p -> p.scatterBlock),
                StrictCodecs.strictOptionalFieldOf(Codec.doubleRange(0.0, 1.0), "probability",
                        DEFAULT_PROBABILITY).forGetter(p -> p.probability),
                StrictCodecs.strictOptionalFieldOf(Codec.intRange(0, 32), "max_drop", DEFAULT_MAX_DROP)
                        .forGetter(p -> p.maxDrop)
        ).apply(instance, (broken, block, probability, drop) ->
                new RubbleScatterProcessor(type, broken, block, probability, drop)));
    }

    /** Nothing to decide per block: a hole is a comparison between two finished lists. */
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
        if (broken.isEmpty() || probability <= 0) {
            return processedBlocks;
        }
        BlockState scatter = BlockStateCodec.block(scatterBlock, Blocks.AIR);
        if (scatter.isAir()) {
            return processedBlocks;
        }
        // Last-write-wins views of both lists, as every finalizeProcessing in this package builds.
        //
        // THE ORIGINAL LIST IS NOT IN WORLD SPACE. Vanilla's processBlockInfos hands finalize the
        // input infos untouched -- template-relative, unrotated -- while the processed list has been
        // moved into the world. Measured, not recalled: origin (100,64,200), original (1,2,3),
        // processed (101,66,203). Keyed by the raw position, no cell ever matched and nothing ever
        // broke. So each original is placed exactly as vanilla placed its processed twin.
        Map<BlockPos, BlockState> authored = new HashMap<>(originalBlocks.size());
        for (StructureTemplate.StructureBlockInfo info : originalBlocks) {
            authored.put(StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(piecePos),
                    info.state());
        }
        Map<BlockPos, BlockState> pending = new HashMap<>(processedBlocks.size());
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            pending.put(info.pos(), info.state());
        }

        // Foot cell -> how many broken cells above it shed onto it. Keyed by asLong in a TreeMap so
        // the result is independent of hash order.
        Map<Long, Integer> feet = new TreeMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : pending.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!isBroken(pos, entry.getValue(), authored, pending)) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos foot = footBelow(pos.relative(side), pos.getY(), pending);
                if (foot != null) {
                    feet.merge(foot.asLong(), 1, Integer::sum);
                }
            }
        }
        if (feet.isEmpty()) {
            return processedBlocks;
        }

        Map<BlockPos, BlockState> placed = new HashMap<>();
        for (Map.Entry<Long, Integer> foot : feet.entrySet()) {
            BlockPos pos = BlockPos.of(foot.getKey());
            RandomSource random = RandomSource.create(Mth.getSeed(pos));
            if (random.nextDouble() >= probability) {
                continue;
            }
            placed.put(pos, withChips(scatter, foot.getValue() + random.nextInt(2)));
        }
        if (placed.isEmpty()) {
            return processedBlocks;
        }
        //   grep "D2-RUBBLE" run/logs/dungeons2.log
        Dungeons.LOGGER.debug("[D2-RUBBLE] {} scatter(s) under broken walls at {}", placed.size(),
                piecePos.toShortString());
        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(processedBlocks.size());
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            BlockState state = placed.get(info.pos());
            out.add(state == null ? info : new StructureTemplate.StructureBlockInfo(info.pos(), state, null));
        }
        return out;
    }

    /** Aged into one of {@link #broken}, with the piece's own masonry above and below. */
    private boolean isBroken(BlockPos pos, BlockState now, Map<BlockPos, BlockState> authored,
                             Map<BlockPos, BlockState> pending) {
        BlockState was = authored.get(pos);
        if (was == null || was.isAir() || was.is(now.getBlock()) || !broken.matches(now)) {
            return false;
        }
        BlockState above = pending.get(pos.above());
        BlockState below = pending.get(pos.below());
        return above != null && !above.isAir() && below != null && !below.isAir();
    }

    /**
     * The walkable cell at the foot of this column: the highest cell at or below {@code fromY}, no
     * more than {@link #maxDrop} down, that the piece writes as AIR and that stands on something the
     * piece writes solid. Null when the column is not the piece's (terrain), or is solid all the way.
     */
    private BlockPos footBelow(BlockPos column, int fromY, Map<BlockPos, BlockState> pending) {
        for (int y = fromY; y >= fromY - maxDrop; y--) {
            BlockPos cell = new BlockPos(column.getX(), y, column.getZ());
            BlockState state = pending.get(cell);
            if (state == null) {
                return null; // left the piece: this side of the wall is the terrain
            }
            if (!state.isAir()) {
                continue;
            }
            BlockState under = pending.get(cell.below());
            if (under != null && !under.isAir()) {
                return cell;
            }
        }
        return null;
    }

    /** The scatter with its {@code chips} set, clamped to what the block allows; unchanged otherwise. */
    private static BlockState withChips(BlockState scatter, int chips) {
        for (Property<?> property : scatter.getProperties()) {
            if (property.getName().equals(CHIPS) && property instanceof IntegerProperty integer) {
                int lo = integer.getPossibleValues().stream().min(Integer::compare).orElse(1);
                int hi = integer.getPossibleValues().stream().max(Integer::compare).orElse(lo);
                return scatter.setValue(integer, Math.max(lo, Math.min(hi, chips)));
            }
        }
        return scatter;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return type.get();
    }
}
