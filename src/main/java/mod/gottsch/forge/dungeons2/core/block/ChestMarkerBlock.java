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
package mod.gottsch.forge.dungeons2.core.block;

import mod.gottsch.forge.dungeons2.core.block.entity.ChestMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * The authoring marker a template places where a chest should go. Backlog #48 step 3.
 *
 * <h2>Why it carries a block entity, unlike {@code spawner_marker}</h2>
 * <p>{@code SpawnerMarkerProcessor}'s note says a DATA marker could carry a free-text string and
 * "a block cannot", which is why the mob set became a codec field on the processor. That is true of
 * a <strong>block-entity-less</strong> block only: a structure template stores block-entity NBT per
 * cell, and a processor is handed it as {@code current.nbt()}. Village Dungeons' markers have always
 * relied on that to carry their {@code mobSet} string.</p>
 *
 * <p>Chests need it. Which loot table this chest draws, and whether it opts in to a Treasure2 chest,
 * are <em>per marker</em> decisions &mdash; the boss chest differs from the three ordinary chests in
 * the same template &mdash; and a codec field on the processor is per <em>pool</em>. Without a block
 * entity every distinct chest would need its own marker block registered.</p>
 *
 * <h2>Visible and solid, deliberately</h2>
 * <p>{@link BaseEntityBlock} defaults {@code getRenderShape} to {@code INVISIBLE}, which is right for
 * the spawner it becomes and wrong for a marker: an author has to see it in the structure editor.
 * Overridden back to {@code MODEL}, and left solid so nothing replaces it before the processor
 * runs.</p>
 *
 * <p>It carries {@code FACING} so the author decides which way the chest opens, and rotates with the
 * template like any horizontal block.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public class ChestMarkerBlock extends BaseEntityBlock {

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            HorizontalDirectionalBlock.FACING;

    public ChestMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Facing the placer, like a chest placed by hand -- the marker should orient the way the
        // author expects the finished chest to.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** See the class note: {@code BaseEntityBlock} would otherwise hide the marker from its author. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChestMarkerBlockEntity(pos, state);
    }
}
