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
package mod.gottsch.forge.dungeons2.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.electronwill.nightconfig.core.CommentedConfig;

import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.registry.DimensionalGeneratedRegistry;
import mod.gottsch.forge.dungeons2.core.setup.CommonSetup;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 
 * @author Mark Gottschling Jan 30, 2023
 *
 */
@Mod(value = Dungeons.MOD_ID)
public class Dungeons {
	// logger
	public static Logger LOGGER = LogManager.getLogger(Dungeons.MOD_ID);

	public static final String MOD_ID = "dungeons2";

	/**
	 * 
	 */
	public Dungeons() {
		// TODO change to the new Echelons style of config setup
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SERVER_CONFIG);
		
		// register the deferred registries
        Registration.init();
        
		// Register the setup method for modloading
		IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
		eventBus.addListener(CommonSetup::common);
		eventBus.addListener(this::config);
	}
	
	/**
	 * On a config event.
	 * @param event
	 */
	private void config(final ModConfigEvent event) {
		if (event.getConfig().getModId().equals(MOD_ID)) {
			if (event.getConfig().getType() == Type.SERVER) {
				IConfigSpec<?> spec = event.getConfig().getSpec();
				// get the toml config data
//	CommentedConfig commentedConfig = event.getConfig().getConfigData();

				if (spec == Config.SERVER_CONFIG) {
					// transform/copy the toml into the config
//					Config.transform(commentedConfig);
				} 
			}
		}
	}
}
