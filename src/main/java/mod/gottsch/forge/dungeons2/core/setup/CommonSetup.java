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
import mod.gottsch.forge.dungeons2.api.DungeonsApi;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns;
import mod.gottsch.forge.dungeons2.core.decorator.floor.DefaultFloorDecorator;
import mod.gottsch.forge.dungeons2.core.decorator.floor.DefaultFloorDrainageDecorator;
import mod.gottsch.forge.dungeons2.core.decorator.floor.border.CrossBorderDecorator;
import mod.gottsch.forge.dungeons2.core.decorator.floor.border.DefaultBorderDecorator;
import mod.gottsch.forge.dungeons2.core.decorator.floor.border.DefaultPaddedBorderDecorator;
import mod.gottsch.forge.dungeons2.core.decorator.wall.DefaultWallDecorator;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.FloorElementType;
import mod.gottsch.forge.dungeons2.core.enums.WallElementType;
import mod.gottsch.forge.dungeons2.core.pattern.ceiling.CeilingPattern;
import mod.gottsch.forge.dungeons2.core.pattern.ceiling.CorridorCeilingPattern;
import mod.gottsch.forge.dungeons2.core.pattern.door.DoorPattern;
import mod.gottsch.forge.dungeons2.core.pattern.floor.CorridorFloorPattern;
import mod.gottsch.forge.dungeons2.core.pattern.floor.FloorDrainagePattern;
import mod.gottsch.forge.dungeons2.core.pattern.floor.FloorPattern;
import mod.gottsch.forge.dungeons2.core.pattern.floor.border.FloorBorderPattern;
import mod.gottsch.forge.dungeons2.core.pattern.wall.WallPattern;
import mod.gottsch.forge.dungeons2.core.registry.BlockProivderRegistry;
import mod.gottsch.forge.dungeons2.core.registry.DecoratorRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Arrays;

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

		// register all motifs (doesn't have to be restricted to the enum's values)
		Arrays.stream(DungeonMotif.values()).sequential().forEach(DungeonsApi::registerMotif);

		// register all pattern elements
		Arrays.stream(WallPattern.values()).sequential().forEach(e -> {
			DungeonsApi.registerPattern(DungeonRoomPatterns.WALL_PATTERN, e);
		});
		Arrays.stream(FloorPattern.values()).sequential().forEach(e -> {
			DungeonsApi.registerPattern(DungeonRoomPatterns.FLOOR_PATTERN, e);
		});
		Arrays.stream(FloorBorderPattern.values()).sequential().forEach(e -> {
			DungeonsApi.registerPattern(DungeonRoomPatterns.FLOOR_BORDER_PATTERN, e);
		});
		Arrays.stream(FloorDrainagePattern.values()).sequential().forEach(e -> {
			DungeonsApi.registerPattern(DungeonRoomPatterns.FLOOR_DRAINAGE_PATTERN, e);
		});
		Arrays.stream(CorridorFloorPattern.values()).sequential().forEach(e -> {
			DungeonsApi.registerPattern(DungeonRoomPatterns.CORRIDOR_FLOOR_PATTERN, e);
		});
		Arrays.stream(CeilingPattern.values()).sequential().forEach(e -> {
			DungeonsApi.registerPattern(DungeonRoomPatterns.CEILING_PATTERN, e);
		});
		Arrays.stream(CorridorCeilingPattern.values()).sequential().forEach(e -> {
			DungeonsApi.registerPattern(DungeonRoomPatterns.CORRIDOR_CEILING_PATTERN, e);
		});
		Arrays.stream(DoorPattern.values()).sequential().forEach(e -> {
			DungeonsApi.registerPattern(DungeonRoomPatterns.DOOR_PATTERN, e);
		});

		// TODO register all patterns with block providers

		// TEMP block provider would be initialized and loaded by a toml file
		// register block providers.
		// OR block providers are registered here, but loaded by the toml file.
		// this ensures that the motif that is read from the toml is registered.
		// -- can't be loaded here from toml since it won't be loaded at this point.
		Arrays.stream(DungeonMotif.values()).sequential().forEach(e -> {
			BlockProivderRegistry.register(e, new BlockProvider());
		});

		// register decorators
		DecoratorRegistry.register(FloorElementType.FLOOR, new DefaultFloorDecorator());
		DecoratorRegistry.register(FloorElementType.FLOOR_BORDER, new DefaultBorderDecorator());
		DecoratorRegistry.register(FloorElementType.FLOOR_BORDER, new CrossBorderDecorator());
		DecoratorRegistry.register(FloorElementType.FLOOR_PADDED_BORDER, new DefaultPaddedBorderDecorator());
//		DecoratorRegistry.register(FloorElementType.FLOOR_CORNER, new DefaultFloorCornerDecorator());
		DecoratorRegistry.register(FloorElementType.FLOOR_DRAINAGE, new DefaultFloorDrainageDecorator());

		DecoratorRegistry.register(WallElementType.WALL, new DefaultWallDecorator());

		// TODO register all




	}

}
