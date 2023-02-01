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
package mod.gottsch.forge.dungeons2.core.persistence;

import mod.gottsch.forge.dungeons2.core.Dungeons;
import mod.gottsch.forge.dungeons2.core.registry.DimensionalGeneratedRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * 
 * @author Mark Gottschling Feb 1, 2023
 *
 */
public class DungeonsSavedData extends SavedData {

	private static final String DUNGEONS2 = Dungeons.MOD_ID;
	private static final String DIM_GEN_REGISTRY = "dimensionalGeneratedRegistry";
	
	/**
	 * 
	 * @return
	 */
	public static DungeonsSavedData create() {
		return new DungeonsSavedData();
	}

	/**
	 * 
	 * @param tag
	 * @return
	 */
	public static DungeonsSavedData load(CompoundTag tag) {
		Dungeons.LOGGER.info("world data loading...");

		// clear the registry
		DimensionalGeneratedRegistry.clear();
		
		if (tag.contains(DIM_GEN_REGISTRY)) {
			DimensionalGeneratedRegistry.load(tag.getList(DIM_GEN_REGISTRY, Tag.TAG_COMPOUND));
		}
		return create();
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		Dungeons.LOGGER.info("world data saving...");
		tag.put(DIM_GEN_REGISTRY, DimensionalGeneratedRegistry.save());
		return tag;
	}
	
	/**
	 * @param world
	 * @return
	 */
	public static DungeonsSavedData get(Level world) {
		DimensionDataStorage storage = ((ServerLevel)world).getDataStorage();
		DungeonsSavedData data = (DungeonsSavedData) storage.computeIfAbsent(
				DungeonsSavedData::load, DungeonsSavedData::create, DUNGEONS2);
		return data;
	}
}
