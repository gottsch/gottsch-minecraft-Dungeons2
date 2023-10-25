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
package mod.gottsch.forge.dungeons2.core.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.registry.support.GeneratedDungeonContext;
import mod.gottsch.forge.dungeons2.core.registry.support.IGeneratedContext;
import mod.gottsch.forge.dungeons2.core.util.ModUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
public class DimensionalGeneratedRegistry {
	private static final String DIMENSION_NAME = "dimension";
	private static final String DUNGEON_REGISTRY_NAME = "registry";
	
	public static final Map<ResourceLocation, GeneratedRegistry<IGeneratedContext>> DUNGEON_REGISTRY = new HashMap<>();

	/**
	 * 
	 */
	private DimensionalGeneratedRegistry() {}
	
	/**
	 * Initialize from Config file.
	 */
	public static void initialize() {
		for (String dimensionName : Config.SERVER.dungeons.dimensionsWhitelist.get()) {
			Dungeons.LOGGER.debug("white list dimension -> {}", dimensionName);
			ResourceLocation dimension = ModUtil.asLocation(dimensionName);
			DUNGEON_REGISTRY.put(dimension, new GeneratedRegistry<>(Config.SERVER.dungeons.registrySize.get()));
		}
	}

	public static void clear() {
		DUNGEON_REGISTRY.forEach((dimension, registry) -> {
			registry.clear();
		});
	}
	
	/**
	 * 
	 * @param dimension
	 * @return
	 */
	public static GeneratedRegistry<IGeneratedContext> getGeneratedRegistry(ResourceLocation dimension) {
		return DUNGEON_REGISTRY.get(dimension);
	}
	
	/**
	 * 
	 * @param listTag
	 */
	public static void load(ListTag listTag) {
		listTag.forEach(tag -> {
			CompoundTag compound = (CompoundTag)tag;
			if (compound.contains(DIMENSION_NAME)) {
				ResourceLocation dimension = ModUtil.asLocation(compound.getString(DIMENSION_NAME));
				if (compound.contains(DUNGEON_REGISTRY_NAME)) {
					GeneratedRegistry<IGeneratedContext> registry  = DUNGEON_REGISTRY.get(dimension);
					if (registry != null) {
						loadRegistry(compound.getList(DUNGEON_REGISTRY_NAME, Tag.TAG_COMPOUND), registry, GeneratedDungeonContext::new);
					}
				}
			}
		});
	}
	
	/**
	 * 
	 * @return
	 */
	public static ListTag save() {
		ListTag listTag = new ListTag();
		DUNGEON_REGISTRY.forEach((resourceLocation, registry) -> {
			CompoundTag tag = new CompoundTag();
			tag.putString(DIMENSION_NAME, resourceLocation.toString());
			tag.put(DUNGEON_REGISTRY_NAME, saveRegistry(registry));
			listTag.add(tag);
		});
		return listTag;
	}
	
	/**
	 * Only saves the context data from the registry. The registries can be rebuilt from
	 * the data during load()
	 * @param registry
	 * @return
	 */
	public static Tag saveRegistry(GeneratedRegistry<IGeneratedContext> registry) {

			ListTag dataTag = new ListTag();
			registry.getValues().forEach(datum -> {
				CompoundTag datumTag = datum.save();
				// add entry to list
				dataTag.add(datumTag);	
			});

		return dataTag;
	}

	/**
	 * 
	 * @param listTag
	 * @param registry
	 * @param supplier
	 */
	public static void loadRegistry(ListTag listTag, 
			GeneratedRegistry<IGeneratedContext> registry,
			Supplier<IGeneratedContext> supplier) {
		
		listTag.forEach(datum -> {
			IGeneratedContext context = supplier.get();
			context.load((CompoundTag)datum);
			registry.register(context.getMinCoords(), context.getMaxCoords(), context);
		});
	}
}
