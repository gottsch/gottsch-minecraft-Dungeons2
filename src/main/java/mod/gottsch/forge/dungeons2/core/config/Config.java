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
package mod.gottsch.forge.dungeons2.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.google.common.collect.Maps;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.gottschcore.config.AbstractConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
public class Config extends AbstractConfig {
	public static final String CATEGORY_DIV = "##############################";
	public static final String UNDERLINE_DIV = "------------------------------";

	// TODO change to the new Echelons style of config setup
	protected static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
	protected static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
	protected static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
	public static final ForgeConfigSpec DUNGEONS_CONFIG_SPEC;
	public static ForgeConfigSpec CLIENT_CONFIG;
	public static ForgeConfigSpec COMMON_CONFIG;
	public static ForgeConfigSpec SERVER_CONFIG;

	public static final Logging LOGGING;
	public static final ServerConfig SERVER;
	public static Config instance = new Config();

	static {

	}
	static {

		CLIENT_CONFIG = CLIENT_BUILDER.build();

		LOGGING = new Logging(COMMON_BUILDER);
		COMMON_CONFIG = COMMON_BUILDER.build();

		SERVER = new ServerConfig(SERVER_BUILDER);
		SERVER_CONFIG = SERVER_BUILDER.build();

		final Pair<DungeonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder()
				.configure(DungeonConfig::new);
		DUNGEONS_CONFIG_SPEC = specPair.getRight();
	}

	/*
	 * 
	 */
	public static class ServerConfig {
		public DungeonsWorldGen dungeons;

		public ServerConfig(ForgeConfigSpec.Builder builder) {
			dungeons = new DungeonsWorldGen(builder);
		}
	}

	public static class DungeonsWorldGen {
		public ConfigValue<Integer> registrySize;
		public ConfigValue<List<? extends String>> dimensionsWhitelist;
		public ConfigValue<List<? extends String>> biomesWhitelist;
		public ConfigValue<List<? extends String>> biomesBlacklist;

		public ConfigValue<Integer>	 waitChunks;
		public ConfigValue<Integer> minBlockDistance;
		public ConfigValue<Double> probability;


		public DungeonsWorldGen(final ForgeConfigSpec.Builder builder)	 {
			builder.comment(CATEGORY_DIV, " Dungeon properties", CATEGORY_DIV)
			.push("dungeons");

			registrySize = builder
					.comment(" The number of dungeon spawns that are monitored.",
							" Most recent additions replace least recent when the registry is full.",
							" This is the set of dungeons used to measure distance between newly generated wells.",
							" In general, a high number is better than a low number, especially in a multiplayer world.",
							" However, dungeons are large and are defaulted with a high min. distance, ",
							"so the number can be on the lower side unless other properties are changed.")
					.defineInRange("registrySize", 50, 25, 1000);

			dimensionsWhitelist = builder
					.comment(" Permitted dimensions for Dungeons2 execution.", 
							" Dungeons2 was designed for 'normal' overworld-type dimensions.", 
							" This setting does not use any wildcards (*). You must explicitly set the dimensions that are allowed.", 
							" ex. minecraft:overworld")
					.defineList("dimensionsWhitelist", Arrays.asList(new String []{"minecraft:overworld"}), s -> s instanceof String);

			this.waitChunks = builder
					.comment(" The number of chunks that are generated in a new world before dungeons start to spawn.")
					.defineInRange("waitChunks", 100, 10, 32000);

			this.minBlockDistance = builder
					.comment(" The minimum distance, measured in blocks, that two dungeons can be in proximity (ie radius).",
							" Note: Only dungeons in the registry are checked against this property.",
							" Default = 600 blocks, or 16 chunks.")
					.defineInRange("minBlockDistance", 600, 100, 32000);

			this.probability = builder
					.comment(" The probability that a dungeon will generate at selected spawn location.",
							" Including a non-100 value increases the randomization of dungeon placement.")
					.defineInRange("probability", 90.0, 0.0, 100.0);

			biomesWhitelist = builder
					.comment(" Permitted biomes for Dungeons2 execution.",
							" This setting does not use any wildcards (*). You must explicitly set the biomes that are allowed.", 
							" ex. minecraft:overworld")
					.defineList("biomesWhitelist", Arrays.asList(new String []{}), s -> s instanceof String);

			biomesBlacklist = builder
					.comment(" Denied biomes for Dungeons2 execution.", 
							" This setting does not use any wildcards (*). You must explicitly set the biomes that are not allowed.", 
							" ex. minecraft:overworld")
					.defineList("biomesBlacklist", Arrays.asList(new String []{"minecraft:mountain_edge", "minecraft:stone_shore", "minecraft:snowcapped_peaks",
							"minecraft:lofty_peaks", "minecraft:stony_peaks"}), s -> s instanceof String);

			builder.pop();
		}
	}

	public static List<DungeonGeneratorConfiguration> dungeonConfigurations;
	public static Map<String, List<DungeonGeneratorConfiguration>> dungeonGeneratorConfigMap;

	/*
	 * internal config class only. used for loading the defaultconfig file
	 * and transformed into the exposed dungeonConfigs property
	 */
	private static class DungeonConfig {
		public DungeonConfig(ForgeConfigSpec.Builder builder) {
			// IMPORTANT! the define name must match what is in the defaultconfig file!
			builder
			.comment("####", "Dungeon Configs", "####")
			.define("dungeonGeneratorConfigs", new ArrayList<>());
			builder.build();
		}
	}

	/**
	 * 
	 * @param configData
	 */
	public static void transform(CommentedConfig configData) {
		// convert the data to an object
		DungeonConfigHolder holder = new ObjectConverter().toObject(configData, DungeonConfigHolder::new);
		if (holder.dungeonGeneratorConfigs == null || holder.dungeonGeneratorConfigs.isEmpty()) {
			Dungeons.LOGGER.warn(
					"\n===========================================================\n" +
					"The Dungeons Generators config is not found or malformed.\n" +
					"===========================================================");
		}

		// get the list from the holder and set the config property
		dungeonConfigurations = holder.dungeonGeneratorConfigs;

		// create the chest config map
		dungeonGeneratorConfigMap = Maps.newHashMap();
		if (dungeonConfigurations != null && !dungeonConfigurations.isEmpty()) {
			dungeonConfigurations.forEach(generator -> {
				// test if the array has been initialized
				if (!dungeonGeneratorConfigMap.containsKey(generator.getSize())) {
					dungeonGeneratorConfigMap.put(generator.getSize(), new ArrayList<>());
				}
				dungeonGeneratorConfigMap.get(generator.getSize()).add(generator);
			});
		}
	}

	/**
	 * A temporary holder class.
	 *
	 */
	private static class DungeonConfigHolder {
		public List<DungeonGeneratorConfiguration> dungeonGeneratorConfigs;
	}

	@Override
	public String getLogsFolder() {
		return Config.LOGGING.folder.get();
	}

	public void setLogsFolder(String folder) {
		Config.LOGGING.folder.set(folder);
	}

	@Override
	public String getLoggingLevel() {
		return Config.LOGGING.level.get();
	}
}
