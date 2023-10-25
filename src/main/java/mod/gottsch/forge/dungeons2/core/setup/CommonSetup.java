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
package mod.gottsch.forge.dungeons2.core.setup;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.registry.DimensionalGeneratedRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonSetup {
	/**
	 * 
	 * @param event
	 */
	public static void common(final FMLCommonSetupEvent event) {
		// add mod specific logging
		Config.instance.addRollingFileAppender(Dungeons.MOD_ID);
//		Dungeons2Networking.register();
		Dungeons.LOGGER.info("common setup complete");
		Dungeons.LOGGER.debug("initializing dimensional generated registries");
		DimensionalGeneratedRegistry.initialize();
	}
	
	/**
	 * attach defined attributes to the entity.
	 * @param event
	 */
//	@SubscribeEvent
//	public static void onAttributeCreate(EntityAttributeCreationEvent event) {
//		event.put(Registration.MAGE_FLAME_ENTITY.get(), Dungeons2Entity.createAttributes().build());
//		event.put(Registration.LESSER_REVELATION_ENTITY.get(), LesserRevelationEntity.createAttributes().build());
//		event.put(Registration.GREATER_REVELATION_ENTITY.get(), GreaterRevelationEntity.createAttributes().build());
//		event.put(Registration.WINGED_TORCH_ENTITY.get(), WingedTorchEntity.createAttributes().build());

//	}
	
	// NOTE not sure if these are needed since they are manually spawned
//	@SubscribeEvent
//	public static void registerEntitySpawn(RegistryEvent.Register<EntityType<?>> event) {
//		SpawnPlacements.register(Registration.MAGE_FLAME_ENTITY.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Dungeons2Entity::checkSpawnRules);
//		SpawnPlacements.register(Registration.LESSER_REVELATION_ENTITY.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, LesserRevelationEntity::checkSpawnRules);
//		SpawnPlacements.register(Registration.GREATER_REVELATION_ENTITY.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, GreaterRevelationEntity::checkSpawnRules);
//		SpawnPlacements.register(Registration.WINGED_TORCH_ENTITY.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, GreaterRevelationEntity::checkSpawnRules);

//	}
}
