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

import mod.gottsch.forge.dungeons2.core.Dungeons;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
public class ConfiguredFeatures {
	private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registry.FEATURE_REGISTRY, Dungeons.MOD_ID);
	private static final DeferredRegister<ConfiguredFeature<?, ?>> CONFIGURED_FEATURES = DeferredRegister.create(Registry.CONFIGURED_FEATURE_REGISTRY, Dungeons.MOD_ID);
    private static final DeferredRegister<PlacedFeature> PLACED_FEATURES = DeferredRegister.create(Registry.PLACED_FEATURE_REGISTRY, Dungeons.MOD_ID);
    
	// Feature
	public static final RegistryObject<Feature<NoneFeatureConfiguration>> DUNGEON_FEATURE = FEATURES.register("dungeon",
			() -> new DungeonFeature(NoneFeatureConfiguration.CODEC));

	public static final RegistryObject<ConfiguredFeature<?, ?>> DUNGEON_CONFIGURED_FEATURE = CONFIGURED_FEATURES.register("dungeon",
			() -> new ConfiguredFeature<>(DUNGEON_FEATURE.get(), NoneFeatureConfiguration.INSTANCE));

	// Placement
	public static final RegistryObject<PlacedFeature> DUNGEON_PLACED_FEATURE = PLACED_FEATURES.register("dungeon",
			() -> new PlacedFeature(DUNGEON_CONFIGURED_FEATURE.getHolder().get(), List.of(BiomeFilter.biome())));

			
	public static void register() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		CONFIGURED_FEATURES.register(bus);
		PLACED_FEATURES.register(bus);
		FEATURES.register(bus);
	}
}
