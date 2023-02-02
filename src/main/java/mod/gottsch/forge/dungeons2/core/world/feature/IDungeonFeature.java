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
package mod.gottsch.forge.dungeons2.core.world.feature;

import java.util.List;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.registry.GeneratedRegistry;
import mod.gottsch.forge.dungeons2.core.registry.support.IGeneratedContext;
import mod.gottsch.forge.gottschcore.biome.BiomeHelper;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import mod.gottsch.forge.gottschcore.world.WorldInfo;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
public interface IDungeonFeature {

	/**
	 * 
	 * @param dimension
	 * @return
	 */
	default public boolean meetsDimensionCriteria(ResourceLocation dimension) {
		// test the dimension white list
		return Config.SERVER.dungeons.dimensionsWhitelist.get().contains(dimension.toString());
	}
	
	/**
	 * 
	 * @param level
	 * @param dimension
	 * @param spawnCoords
	 * @param minDistance
	 * @return
	 */
	default public boolean meetsProximityCriteria(ServerLevel level, GeneratedRegistry<IGeneratedContext> registry, ICoords spawnCoords, int minDistance) {
		if (isRegisteredDungeonWithinDistance(level, registry, spawnCoords, minDistance)) {
			Dungeons.LOGGER.trace("The distance to the nearest dungeon is less than the minimun required.");
			return false;
		}	
		return true;
	}
	
	/**
	 * 
	 * @param level
	 * @param dimension
	 * @param key
	 * @param coords
	 * @param minDistance
	 * @return
	 */
	public boolean isRegisteredDungeonWithinDistance(Level level, GeneratedRegistry<IGeneratedContext> registry, ICoords coords, int minDistance);

	
	/**
	 * 
	 * @param level
	 * @param spawnCoords
	 * @param whitelist
	 * @param blacklist
	 * @return
	 */
	default public boolean meetsBiomeCriteria(ServerLevel level, ICoords spawnCoords, List<? extends String> whitelist, List<? extends String> blacklist) {
		Holder<Biome> biome = level.getBiome(spawnCoords.toPos());
		BiomeHelper.Result biomeCheck =BiomeHelper.isBiomeAllowed(biome.value(), whitelist, blacklist);
		if(biomeCheck == BiomeHelper.Result.BLACK_LISTED ) {
			if (WorldInfo.isClientSide(level)) {
				Dungeons.LOGGER.debug("biome {} is not a valid biome at -> {}", biome.value().getRegistryName().toString(), spawnCoords.toShortString());
			}
			else {
				// TODO test if this crashes with the getRegistryName because in 1.12 this was a client side only
				Dungeons.LOGGER.debug("biome {} is not valid at -> {}",biome.value().getRegistryName().toString(), spawnCoords.toShortString());
			}					
			return false;
		}
		return true;
	}
}
