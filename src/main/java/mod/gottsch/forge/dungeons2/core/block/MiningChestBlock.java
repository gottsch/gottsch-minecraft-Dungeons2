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

import mod.gottsch.forge.dungeons2.core.block.entity.DungeonsBlockEntities;
import mod.gottsch.forge.dungeons2.core.block.entity.MiningChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

/**
 * The Mining Chest (backlog #7): an iron chest with dark-iron bands, on Treasure2's ironbound
 * model.
 *
 * <p>A vanilla {@link ChestBlock} in every respect but the look, so the container, the lid
 * animation, hoppers, comparators and the open/close sounds all come from vanilla unchanged.</p>
 *
 * <h2>Always single</h2>
 * <p>The banded model has no double-chest form, so two of these placed side by side must not join.
 * Forcing {@code SINGLE} at placement is enough: vanilla's {@code updateShape} only joins a chest to a
 * neighbour that is already one half of a pair, and none ever is.</p>
 *
 * @author Mark Gottschling on Oct 4, 2026
 */
public class MiningChestBlock extends ChestBlock {

    public MiningChestBlock(Properties properties) {
        super(properties, DungeonsBlockEntities::miningChestType);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiningChestBlockEntity(pos, state);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(TYPE, ChestType.SINGLE);
    }
}
