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
package mod.gottsch.forge.dungeons2.core.util;

import java.util.Random;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import mod.gottsch.forge.gottschcore.world.WorldInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.NaturalSpawner;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
public class ModUtil {
	public static ResourceLocation asLocation(String name) {
		return hasDomain(name) ? new ResourceLocation(name) : new ResourceLocation(Dungeons.MOD_ID, name);
	}

	public static boolean hasDomain(String name) {
		return name.indexOf(":") >= 0;
	}
	
	/**
	 * 
	 * @author Mark Gottschling on Jul 25, 2021
	 *
	 */
	public static class SpawnEntityHelper {
		
		/**
		 * 
		 * @param level
		 * @param random
		 * @param entityType
		 * @param mob
		 * @param coords
		 * @return
		 */
		public static Entity spawn(ServerLevel level, Random random, EntityType<?> entityType, Entity mob, ICoords coords) {

			for (int i = 0; i < 20; i++) { // 20 tries
				int spawnX = coords.getX() + Mth.nextInt(random, 1, 2) * Mth.nextInt(random, -1, 1);
				int spawnY = coords.getY() + Mth.nextInt(random, 1, 2) * Mth.nextInt(random, -1, 1);
				int spawnZ = coords.getZ() + Mth.nextInt(random, 1, 2) * Mth.nextInt(random, -1, 1);
				ICoords spawnCoords = new Coords(spawnX, spawnY, spawnZ);

				boolean isSpawned = false;
				if (!WorldInfo.isClientSide(level)) {
					SpawnPlacements.Type placement = SpawnPlacements.getPlacementType(entityType);
					if (NaturalSpawner.isSpawnPositionOk(placement, level, spawnCoords.toPos(), entityType)) {
						mob.setPos((double)spawnX, (double)spawnY, (double)spawnZ);
						level.addFreshEntityWithPassengers(mob);
						isSpawned = true;
					}
					if (isSpawned) {
						break;
					}
				}
			}
			return mob;
		}
	}
}
