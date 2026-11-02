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
package mod.gottsch.neo.dungeons2;

import mod.gottsch.neo.dungeons2.core.config.Config;
import mod.gottsch.neo.dungeons2.core.config.DungeonGenerationConfigRegistries;
import mod.gottsch.neo.dungeons2.core.config.MotifConfigRegistries;
import mod.gottsch.neo.dungeons2.core.setup.CommonSetup;
import mod.gottsch.neo.dungeons2.core.setup.Registration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
	public Dungeons(IEventBus eventBus, ModContainer container) {
		container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);

		// register the deferred registries
        Registration.init(eventBus);

		// Register the setup method for modloading
		eventBus.addListener(CommonSetup::common);
		eventBus.register(DungeonGenerationConfigRegistries.class);
		eventBus.register(MotifConfigRegistries.class);
	}
}
