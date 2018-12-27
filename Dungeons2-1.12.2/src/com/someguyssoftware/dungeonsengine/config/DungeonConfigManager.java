/**
 * 
 */
package com.someguyssoftware.dungeonsengine.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.someguyssoftware.dungeons2.Dungeons2;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * @author Mark Gottschling on Dec 20, 2018
 *
 */
public class DungeonConfigManager {

	/*
	 * Guava Table of Dungeon Configs based on Biome Name and Size
	 */
	public static Table<String, DungeonSize, List<IDungeonConfig>> DUNGEON_CONFIG_TABLE = HashBasedTable.create();

	public static IDungeonConfig defaultConfig;
	
	/**
	 * 
	 */
	public DungeonConfigManager() {
		// load the default config
		try {
			defaultConfig = DungeonConfigLoader.loadDefault();
		} catch (Exception e) {
			Dungeons2.log.error(e);
			// TODO manually set values ?
		}
		
		// load all the configs
		List<IDungeonConfig> configs = DungeonConfigLoader.loadAll();
		// map the configs to the table
		for (IDungeonConfig c : configs) {
			if (c.getBiomeWhiteList().contains("*") || (c.getBiomeWhiteList().isEmpty() && c.getBiomeBlackList().isEmpty())) {
				Set<Biome> biomes = (Set<Biome>) ForgeRegistries.BIOMES.getValuesCollection();
				for (Biome biome : biomes) {
					// exclude nether and end biome
					if (!BiomeDictionary.hasType(biome, Type.END) && !BiomeDictionary.hasType(biome, Type.NETHER)) {
						if (!DUNGEON_CONFIG_TABLE.contains(biome.getBiomeName(), c.getSize())) {
							DUNGEON_CONFIG_TABLE.put(biome.getBiomeName(), c.getSize(), new ArrayList<>(3));
						}
						DUNGEON_CONFIG_TABLE.get(biome.getBiomeName(), c.getSize()).add(c);
					}
				}
			}
			else {
				if (!c.getBiomeWhiteList().isEmpty()) {
					// register configs
					for (String b : c.getBiomeWhiteList()) {
						String biomeName = b.trim().toLowerCase();
						Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(biomeName));
						Dungeons2.log.debug("wl.cname -> {}, biome -> {}, size -> {}", c.getName(), biome.getBiomeName(), c.getSize());
						if (!BiomeDictionary.hasType(biome, Type.END) && !BiomeDictionary.hasType(biome, Type.NETHER)) {
							if (!DUNGEON_CONFIG_TABLE.contains(biome.getBiomeName(), c.getSize())) {
								DUNGEON_CONFIG_TABLE.put(biome.getBiomeName(), c.getSize(), new ArrayList<>(3));
							}
							DUNGEON_CONFIG_TABLE.get(biome.getBiomeName(), c.getSize()).add(c);
						}
					}
				}
				else if (!c.getBiomeBlackList().isEmpty()) {
					// register configs
					List<Biome> biomes = (List<Biome>) ForgeRegistries.BIOMES.getValuesCollection();
					for (Biome biome : biomes) {
						Dungeons2.log.debug("bl.cname -> {}, biome -> {}, size -> {}", c.getName(), biome.getBiomeName(), c.getSize());
						if (!c.getBiomeBlackList().contains(biome.getBiomeName().toLowerCase()) &&
								!BiomeDictionary.hasType(biome, Type.END) &&
								!BiomeDictionary.hasType(biome, Type.NETHER)) {
							if (!DUNGEON_CONFIG_TABLE.contains(biome.getBiomeName(), c.getSize())) {
								DUNGEON_CONFIG_TABLE.put(biome.getBiomeName(), c.getSize(), new ArrayList<>(3));
							}
							DUNGEON_CONFIG_TABLE.get(biome.getBiomeName(), c.getSize()).add(c);
						}
					}
				}				
			}
		}
	}

	/**
	 * 
	 * @param biomeName
	 * @return
	 */
	public List<IDungeonConfig> getByBiome(String biomeName) {
		List<IDungeonConfig> list = new ArrayList<>();
		for (List<IDungeonConfig> l :DUNGEON_CONFIG_TABLE.row(biomeName).values()) {
			list.addAll(l);
		}
		return list;
	}
}
