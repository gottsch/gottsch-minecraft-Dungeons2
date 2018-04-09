/**
 * 
 */
package com.someguyssoftware.gottschcore.biome;

import com.google.common.collect.ImmutableMap;

import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;

/**
 * This class is a singleton
 * @author Mark Gottschling on Dec 26, 2016
 *
 */
public class BiomeTypeMap {
	/*
	 * NOTE by making this a final ImmutableMap, you can not register your own new Types
	 */
	private static final ImmutableMap<String, BiomeDictionary.Type> map;

	static {
		map =
	       new ImmutableMap.Builder<String, BiomeDictionary.Type>()
	           .put("BEACH", Type.BEACH)
	           .put("COLD", Type.COLD)
	           .put("CONIFEROUS", Type.CONIFEROUS)
	           .put("DEAD", Type.DEAD)
	           .put("DENSE", Type.DENSE)
	           .put("DRY", Type.DRY)
	           .put("END", Type.END)
	           .put("FOREST", Type.FOREST)
	           .put("HILLS", Type.HILLS)
	           .put("HOT", Type.HOT)
	           .put("JUNGLE", Type.JUNGLE)
	           .put("LUSH", Type.LUSH)
	           .put("MAGICAL", Type.MAGICAL)
	           .put("MESA", Type.MESA)
	           .put("MOUNTAIN", Type.MOUNTAIN)
	           .put("MUSHROOM", Type.MUSHROOM)
	           .put("NETHER", Type.NETHER)
	           .put("OCEAN", Type.OCEAN)
	           .put("PLAINS", Type.PLAINS)
	           .put("RARE", Type.RARE)
	           .put("RIVER", Type.RIVER)
	           .put("SANDY", Type.SANDY)
	           .put("SAVANNA", Type.SAVANNA)
	           .put("SNOWY", Type.SNOWY)
	           .put("SPARSE", Type.SPARSE)
	           .put("SPOOKY", Type.SPOOKY)
	           .put("SWAMP", Type.SWAMP)
	           .put("VOID", Type.VOID)
	           .put("WASTELAND", Type.WASTELAND)
	           .put("WATER", Type.WATER)
	           .put("WET", Type.WET)
	           .build();
	}
	
	/**
	 * 
	 */
	private BiomeTypeMap() {}
	
	/**
	 * 
	 * @param name
	 * @return
	 */
	public static BiomeDictionary.Type getByName(String name) {
		BiomeDictionary.Type type = null;
		String key = name.trim().toUpperCase();
		if (BiomeTypeMap.map.containsKey(key)) {
			type = BiomeTypeMap.map.get(key);
		}
		return type;
	}

	/**
	 * @return
	 */
	public static boolean isEmpty() {
		if (BiomeTypeMap.map == null || BiomeTypeMap.map.isEmpty()) return true;
		return false;
	}
}
