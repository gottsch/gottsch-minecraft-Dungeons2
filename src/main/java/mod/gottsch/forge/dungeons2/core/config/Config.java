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

import java.util.Arrays;
import java.util.List;

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
	
	public static ForgeConfigSpec CLIENT_CONFIG;
	public static ForgeConfigSpec COMMON_CONFIG;
	public static ForgeConfigSpec SERVER_CONFIG;
	
	public static final Logging LOGGING;
	public static final ServerConfig SERVER;
	public static Config instance = new Config();
	
	static {
		
		CLIENT_CONFIG = CLIENT_BUILDER.build();
		
		LOGGING = new Logging(COMMON_BUILDER);
		COMMON_CONFIG = COMMON_BUILDER.build();
		
		SERVER = new ServerConfig(SERVER_BUILDER);
		SERVER_CONFIG = SERVER_BUILDER.build();
	}
	
	/*
	 * 
	 */
	public static class ServerConfig {
//		public ForgeConfigSpec.IntValue mageFlameLifespan;
//		public ForgeConfigSpec.IntValue lesserRevelationLifespan;
//		public ForgeConfigSpec.IntValue greaterRevelationLifespan;
		public Dungeons dungeons;
		
		public ServerConfig(ForgeConfigSpec.Builder builder) {

			dungeons = new Dungeons(builder);
//			builder.comment(CATEGORY_DIV, "Dungeon Properties", CATEGORY_DIV)
//			.push("dungeons");
			
//			mageFlameLifespan = builder
//					.comment(" The lifespan of a Mage Flame spell/entity in ticks.", 
//							"Ex. 20 ticks * 60 seconds * 5 = 6000 = 5 minutes.")
//					.defineInRange("mageFlameLifespan", 12000, 1200, 72000);
//
//			lesserRevelationLifespan = builder
//					.comment(" The lifespan of a Lesser Revelation spell/entity in ticks.")
//					.defineInRange("lesserRevelationLifespan", 18000, 1200, 72000);
//			
//			greaterRevelationLifespan = builder
//					.comment(" The lifespan of a Greater Revelation spell/entity in ticks.")
//					.defineInRange("greaterRevelationLifespan", 36000, 1200, 72000);

//			builder.pop();
		}
	}
	
	public static class Dungeons {
		public ConfigValue<Integer> registrySize;
		public ConfigValue<List<? extends String>> dimensionsWhitelist;
		public ConfigValue<List<? extends String>> biomesWhitelist;
		public ConfigValue<List<? extends String>> biomesBlacklist;
		
		public ConfigValue<Integer>	 waitChunks;
		public ConfigValue<Integer> minBlockDistance;
		public ConfigValue<Double> probability;

		
		public Dungeons(final ForgeConfigSpec.Builder builder)	 {
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
