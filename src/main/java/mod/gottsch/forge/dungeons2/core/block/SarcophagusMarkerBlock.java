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

import mod.gottsch.forge.dungeons2.core.block.entity.SarcophagusMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The authoring marker for a sealed tomb (#104). ONE block, standing where the tomb's FOOT goes.
 *
 * <h2>One block for a two-block tomb</h2>
 * <p>A sarcophagus is a head and a foot, and {@code SarcophagusMarkerProcessor} writes both: the
 * foot in this cell and the head in the cell {@link #FACING} points at, which the author leaves as
 * AIR. Placed by hand it behaves like the sarcophagus itself or a bed &mdash; the head goes one block
 * further in the direction the author is looking &mdash; so authoring one reads the same as placing
 * the real thing. A two-block marker was the alternative and was not worth its placement and
 * breaking logic: the head cell is something a template author checks once, and the processor
 * refuses, loudly, a head cell that is not air.</p>
 *
 * <p>Carries a block entity for the other markers' reason (per-tomb contents), and renders as a
 * model, solid, so it survives to the processor and its author can see it. The front face, bones,
 * is the head end.</p>
 *
 * @author Mark Gottschling on Sep 26, 2026
 */
public class SarcophagusMarkerBlock extends BaseEntityBlock {

    /** Foot to head, as on the sarcophagus. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SarcophagusMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Head away from the author, as the sarcophagus itself places. See the class note. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** {@code BaseEntityBlock} would otherwise hide the marker from its author. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SarcophagusMarkerBlockEntity(pos, state);
    }
}
