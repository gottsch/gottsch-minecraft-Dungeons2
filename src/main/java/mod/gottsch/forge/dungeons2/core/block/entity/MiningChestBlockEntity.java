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
package mod.gottsch.forge.dungeons2.core.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Mining Chest's block entity: a vanilla chest under its own type, so it gets its own renderer.
 * The {@code Items} NBT the generator writes is vanilla's container format and loads unchanged.
 *
 * @author Mark Gottschling on Oct 4, 2026
 */
public class MiningChestBlockEntity extends ChestBlockEntity {

    public MiningChestBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonsBlockEntities.miningChestType(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.dungeons2.mining_chest");
    }
}
