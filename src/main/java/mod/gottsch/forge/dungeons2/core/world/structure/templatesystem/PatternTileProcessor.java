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
import mod.gottsch.forge.dungeonblocks.core.block.PatternTileBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lines up {@code dungeonblocks} pattern tiles (the flagstones, Dungeon Blocks 3.1.0) with the world.
 *
 * <p>A pattern tile shows one slice of a pattern larger than a block, and which slice is its
 * {@code tile} property: a pure function of the block's WORLD position
 * ({@link PatternTileBlock#tileAt}). The block sets it itself only when a player places it.
 * Nothing in worldgen does, so a template comes out with the tiles its author's dev world gave it
 * &mdash; scrambled the moment the template lands somewhere else, or rotated, since {@code tile}
 * does not rotate &mdash; and a procedural floor comes out all slice 0.</p>
 *
 * <p>This processor recomputes {@code tile} from each tile's final world position. One processor
 * covers all three routes: an authored template, a procedural room (which runs the same processor
 * list, see {@code PieceProcessors}), and a tile that weathering swapped in, because it keys on the
 * block CLASS and so reaches any future mossy or cracked twin that carries the same property.</p>
 *
 * <h2>Last in the list, and in {@code finalizeProcessing}</h2>
 * <p>It has to run after anything that can put a tile there or replace one. Vanilla runs every
 * {@code processBlock} before any {@code finalizeProcessing}, and the finalize phase in list order,
 * so doing the work in {@code finalizeProcessing} from the end of the list puts it after every
 * aging rule, every sweep and every other finalizer. It is deliberately NOT a
 * {@code LevelIndependentProcessor}: that would move it into the procedural path's first pass,
 * ahead of the level-reading processors that come after it in the list.</p>
 *
 * @author Mark Gottschling on 10/22/2026
 */
public class PatternTileProcessor extends StructureProcessor {

    private final Supplier<StructureProcessorType<?>> type;

    public PatternTileProcessor(Supplier<StructureProcessorType<?>> type) {
        this.type = type;
    }

    /** No fields: the entry is just {@code {"processor_type": "dungeons2:pattern_tile"}}. */
    public static Codec<PatternTileProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return Codec.unit(() -> new PatternTileProcessor(type));
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset,
            BlockPos pos, StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        return current;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level,
            BlockPos offset, BlockPos pos, List<StructureTemplate.StructureBlockInfo> original,
            List<StructureTemplate.StructureBlockInfo> processed, StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> out = null;
        for (int i = 0; i < processed.size(); i++) {
            StructureTemplate.StructureBlockInfo info = processed.get(i);
            BlockState aligned = aligned(info.state(), info.pos());
            if (aligned == info.state()) {
                if (out != null) {
                    out.add(info);
                }
                continue;
            }
            if (out == null) {
                out = new ArrayList<>(processed.subList(0, i));
            }
            out.add(new StructureTemplate.StructureBlockInfo(info.pos(), aligned, info.nbt()));
        }
        return out == null ? processed : out;
    }

    /** {@code state} with its {@code tile} set for {@code worldPos}; any other block unchanged. */
    static BlockState aligned(BlockState state, BlockPos worldPos) {
        if (!(state.getBlock() instanceof PatternTileBlock)
                || !state.hasProperty(PatternTileBlock.TILE)) {
            return state;
        }
        int tile = PatternTileBlock.tileAt(worldPos);
        return state.getValue(PatternTileBlock.TILE) == tile ? state
                : state.setValue(PatternTileBlock.TILE, tile);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return type.get();
    }
}
