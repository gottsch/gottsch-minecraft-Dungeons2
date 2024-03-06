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
package mod.gottsch.forge.dungeons2.core.event;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.cache.FeatureCaches;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.registry.BlockProivderRegistry;
import mod.gottsch.forge.gottschcore.world.WorldInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.nio.file.Path;

/**
 *
 * @author Mark Gottschling Mar 3, 2024
 *
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public class WorldEvents {
	private static Path worldSavePath;
	private static boolean isLoaded = false;
	private static boolean isClientLoaded = false;

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onWorldLoad(LevelEvent.Load event) {
		if (WorldInfo.isServerSide((Level)event.getLevel())) {
			ResourceLocation dimension = WorldInfo.getDimension((Level) event.getLevel());
			Dungeons.LOGGER.info("In world load event for dimension {}", dimension.toString());

			/*
			 *  cache the world save folder and pass into each registry.
			 */

				if ((!isLoaded && Config.SERVER.dungeons.dimensionsWhitelist.get().contains(dimension.toString()))) {
					// initialize feature caches
					FeatureCaches.initialize();

					// initialize/register all block providers
					// TODO pulled from the BlockProvidersConfiguration config property
					BlockProivderRegistry.register(Config.blockProviderConfiguration.getMotifs());
				}
			}

//		if (!event.getLevel().isClientSide()) {
//			Level world = (Level) event.getLevel();
//			Dungeons.LOGGER.info("In world load event for dimension {}", WorldInfo.getDimension(world).toString());
//			if (WorldInfo.isSurfaceWorld(world)) {
//				Dungeons.LOGGER.info("loading Dungeons data...");
//				DungeonsSavedData.get(world);
//			}
//		}
	}
//	@SubscribeEvent
//	public static void onBiomeLoading(final BiomeLoadingEvent event) {
//		/*
//		 * NOTE:
//		 * generation must occur in the correct order according to GenerationStep.Decoration
//		 */
////		TreasureOreGeneration.generateOres(event);
//
//		if (event.getCategory() != BiomeCategory.OCEAN) {
//			event.getGeneration().addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, ConfiguredFeatures.DUNGEON_PLACED_FEATURE.getHolder().get());
//		}
//	}
}
