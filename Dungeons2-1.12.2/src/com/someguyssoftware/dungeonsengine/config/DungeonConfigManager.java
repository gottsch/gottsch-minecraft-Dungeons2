/**
 * 
 */
package com.someguyssoftware.dungeonsengine.config;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * @author Mark Gottschling on Dec 20, 2018
 *
 */
public class DungeonConfigManager {

	/*
	 * Guava Table of Dungeon Configs based on Biome Name and Size
	 */
	public static Table<String, DungeonSize, List<IDungeonConfig>> DUNGEON_CONFIG_TABLE = HashBasedTable.create();

	/**
	 * 
	 */
	public DungeonConfigManager() {
		// load all the configs
		List<IDungeonConfig> configs = DungeonConfigLoader.loadAll();
		// map the configs to the table
		for (IDungeonConfig c : configs) {
			if (c.getBiomeWhiteList().contains("*") || (c.getBiomeWhiteList().isEmpty() && c.getBiomeBlackList().isEmpty())) {
				if (!DUNGEON_CONFIG_TABLE.contains("*", c.getSize())) {
					DUNGEON_CONFIG_TABLE.put("*", c.getSize(), new ArrayList<>());
				}
				DUNGEON_CONFIG_TABLE.get("*", c.getSize()).add(c);
			}
			else {
				if (!c.getBiomeWhiteList().isEmpty()) {
					for (String biome : c.getBiomeWhiteList()) {
						String b = biome.trim().toUpperCase();
						if (!DUNGEON_CONFIG_TABLE.contains(b, c.getSize())) {
							DUNGEON_CONFIG_TABLE.put(b, c.getSize(), new ArrayList<>());
						}
						DUNGEON_CONFIG_TABLE.get(b, c.getSize()).add(c);
					}
				}
				else if (!c.getBiomeBlackList().isEmpty()) {
					for (String biome : c.getBiomeBlackList()) {
						String b = biome.trim().toUpperCase();
						if (!DUNGEON_CONFIG_TABLE.contains(b, c.getSize())) {
							DUNGEON_CONFIG_TABLE.put(b, c.getSize(), new ArrayList<>());
						}
						DUNGEON_CONFIG_TABLE.get(b, c.getSize()).add(c);
					}
				}				
			}
		}
	}

}
