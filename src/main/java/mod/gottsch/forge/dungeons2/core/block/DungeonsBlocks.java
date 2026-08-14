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

import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * <strong>This registers nothing today &mdash; see
 * {@link mod.gottsch.forge.dungeons2.core.block.entity.DungeonsBlockEntities} for the full
 * explanation.</strong> Nothing loads this class during startup, so the deferred generator block is
 * absent from the registry; that is harmless only because the Phase 2 feature that placed it is
 * itself unregistered. Verified 2026-08-13.
 *
 * @author Mark Gottschling Oct 25, 2023
 *
 */
public class DungeonsBlocks {
    public static final RegistryObject<Block> DEFERRED_DUNGEON_GENERATOR = Registration.BLOCKS.register("deferred_dungeon_generator", () -> new DeferredDungeonGeneratorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
            .strength(3.0F).sound(SoundType.STONE).replaceable().noCollission().noLootTable().air()));

}
