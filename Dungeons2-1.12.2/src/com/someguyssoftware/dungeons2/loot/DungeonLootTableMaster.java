/**
 * 
 */
package com.someguyssoftware.dungeons2.loot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.enums.Rarity;
import com.someguyssoftware.gottschcore.loot.LootTable;
import com.someguyssoftware.gottschcore.loot.LootTableMaster;
import com.someguyssoftware.gottschcore.mod.IMod;

import net.minecraft.util.ResourceLocation;

/**
 * @author Mark Gottschling on Jan 7, 2019
 *
 */
public class DungeonLootTableMaster extends LootTableMaster {
	private static final String CUSTOM_LOOT_TABLES_RESOURCE_PATH = "/loot_tables/";
	private static final String CUSTOM_LOOT_TABLES_PATH = "loot_tables";
	
	public static final String CUSTOM_LOOT_TABLE_KEY = "CUSTOM";
	
	/*
	 * Guava Table of LootTable ResourceLocations based on LootTableManager-key and Rarity 
	 */
	public final Table<String, Rarity, List<ResourceLocation>> CHEST_LOOT_TABLES_RESOURCE_LOCATION_TABLE = HashBasedTable.create();

	/*
	 * Guava Table of LootTables based on LootTableManager-key and Rarity
	 */
	public final Table<String, Rarity, List<LootTable>> CHEST_LOOT_TABLES_TABLE = HashBasedTable.create();
	
	/*
	 * relative location of chest loot tables - in resource path or file system
	 */
	private static final List<String> CHEST_LOOT_TABLE_FOLDER_LOCATIONS = ImmutableList.of(
			"chests/common",
			"chests/uncommon",
			"chests/scarce",
			"chests/rare",
			"chests/epic"
			);
	
	/*
	 * relative location of other loot tables - in resource path or file system
	 */
	private static final List<String> NON_CHEST_LOOT_TABLE_FOLDER_LOCATIONS = ImmutableList.of(
			"armor",
			"food",
			"items",
			"potions",
			"tools"
			);
	
	/**
	 * @param mod
	 * @param resourcePath
	 * @param folderName
	 */
	public DungeonLootTableMaster(IMod mod, String resourcePath, String folderName) {
		super(mod, resourcePath, folderName);
		buildAndExpose(CUSTOM_LOOT_TABLES_RESOURCE_PATH, Dungeons2.MODID, CHEST_LOOT_TABLE_FOLDER_LOCATIONS);
		buildAndExpose(CUSTOM_LOOT_TABLES_RESOURCE_PATH, Dungeons2.MODID, NON_CHEST_LOOT_TABLE_FOLDER_LOCATIONS);
		// initialize the maps
		for (Rarity r : Rarity.values()) {
//			CHEST_LOOT_TABLES_RESOURCE_LOCATION_TABLE.put(BUILTIN_LOOT_TABLE_KEY, r, new ArrayList<ResourceLocation>());
			CHEST_LOOT_TABLES_RESOURCE_LOCATION_TABLE.put(CUSTOM_LOOT_TABLE_KEY, r, new ArrayList<ResourceLocation>());

//			CHEST_LOOT_TABLES_TABLE.put(BUILTIN_LOOT_TABLE_KEY, r, new ArrayList<TreasureLootTable>());
			CHEST_LOOT_TABLES_TABLE.put(CUSTOM_LOOT_TABLE_KEY, r, new ArrayList<LootTable>());
		}
	}

	/**
	 * Call in WorldEvent.Load event handler.
	 * Overide this method if you have a different cache mechanism.
	 * @param modIDIn
	 * @param location
	 */
	public void register(String modID) {
		for (String location : CHEST_LOOT_TABLE_FOLDER_LOCATIONS) {
			// get loot table files as ResourceLocations from the file system location
			List<ResourceLocation> locs = getLootTablesResourceLocations(modID, location);
			
			// load each ResourceLocation as LootTable and map it.
			for (ResourceLocation loc : locs) {
				Path path = Paths.get(loc.getResourcePath());
				Dungeons2.log.debug("path to resource loc -> {}", path.toString());
				// map the loot table resource location
				Rarity key = Rarity.valueOf(path.getFileName().toString().toUpperCase());
				// add to resourcemap
				CHEST_LOOT_TABLES_RESOURCE_LOCATION_TABLE.get(CUSTOM_LOOT_TABLE_KEY, key).add(loc);				
				// create loot table
				LootTable lootTable = getLootTableManager().getLootTableFromLocation(loc);
				// add loot table to map
				CHEST_LOOT_TABLES_TABLE.get(CUSTOM_LOOT_TABLE_KEY, key).add(lootTable);
				Dungeons2.log.debug("tabling loot table: {} {} -> {}", CUSTOM_LOOT_TABLE_KEY, key, loc);
			}			
		}
	}
	
	/**
	 * 
	 * @param rarity
	 * @return
	 */
	public List<LootTable> getLootTableByRarity(Rarity rarity) {
		// get all loot tables by column key
		List<LootTable> tables = new ArrayList<>();
		Map<String, List<LootTable>> mapOfLootTables = CHEST_LOOT_TABLES_TABLE.column(rarity);
		// convert to a single list
		for(Entry<String, List<LootTable>> n : mapOfLootTables.entrySet()) {
			Dungeons2.log.debug("Adding table entry to loot table list -> {} {}: size {}", rarity, n.getKey(), n.getValue().size());
			tables.addAll(n.getValue());
		}
		return tables;
	}
}
