/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.block.entity.DeferredDungeonGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 *
 * @author Mark Gottschling Oct 25, 2023
 *
 */
public class DeferredDungeonGeneratorBlock extends BaseEntityBlock {
    public DeferredDungeonGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        Dungeons.LOGGER.debug("created dungeon generate be");
        DeferredDungeonGeneratorBlockEntity blockEntity = null;
        try {
            blockEntity = new DeferredDungeonGeneratorBlockEntity(pos, state);
        }
        catch(Exception e) {
            Dungeons.LOGGER.error(e);
        }
        return (BlockEntity) blockEntity;
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() ? (lvl, pos, blockState, t) -> {
            if (t instanceof DeferredDungeonGeneratorBlockEntity entity) {
                entity.tickServer();
            }
        } : null;
    }

    @Override
    public boolean isAir(BlockState state) {
        return true;
    }
}
