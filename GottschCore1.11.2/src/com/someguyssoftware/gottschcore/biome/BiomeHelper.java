/**
 * 
 */
package com.someguyssoftware.gottschcore.biome;

import java.util.List;

import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;

/**
 * @author Mark Gottschling on May 8, 2017
 *
 */
public class BiomeHelper {
	
//	/**
//	 * Loads all the Minecraft Forge Biome Types into a map by name.
//	 * Helper method
//	 */
//	public static void loadBiomeTypeMap() {
//		if (BiomeTypeMap.isEmpty()) {
//			// TODO throw an error
//			return;
//		}
//	}	
	
	/**
	 * 
	 * @param biomeTypeNames
	 * @param typeHolders
	 */
	public static void loadBiomeList(String[] biomeTypeNames, List<BiomeTypeHolder> typeHolders) {
		BiomeTypeHolder holder = null;
		Object t = null;

		for (String s : biomeTypeNames) {
			holder = null;
			// check against Forge BiomeTypeMap
			if ((t = BiomeTypeMap.getByName(s.trim().toUpperCase()))!= null) {
				holder = new BiomeTypeHolder(0, t);
			}
			// check against all registered BiomeDictionaries
			else {
				for (IBiomeDictionary d : BiomeDictionaryManager.getInstance().getAll()) {
					t = d.getTypeByName(s.toUpperCase());
					holder = new BiomeTypeHolder(1, t);
					break;
				}
			}
			if (holder != null) {
				typeHolders.add(holder);
			}
		}
	}
	
	/**
	 * Load the Biome Type Holders with all the biome types.
	 * @param biome
	 * @param whiteList
	 * @param blackList
	 * @return
	 */
	public static boolean isBiomeAllowed(Biome biome, List<BiomeTypeHolder> whiteList, List<BiomeTypeHolder> blackList) {
        /*
         * check the white list first.
         * if white list is not null && not empty but biome is NOT in the list, then return false
         */
        if (whiteList != null && whiteList.size() > 0) {
	        for (BiomeTypeHolder holder : whiteList) {
	        	// check which dictionary to use
	        	if (holder.getDictionaryId() == 0) {
	        		if (BiomeDictionary.hasType(biome, (Type)holder.getBiomeType())) {
	        			return true;
	        		}
	        	}
	        	else {
	        		// check against all registered BiomeDictionaries
	        		for (IBiomeDictionary d : BiomeDictionaryManager.getInstance().getAll()) {
	        			if (d.isBiomeOfType(biome, (IBiomeType)holder.getBiomeType())) {
	        				return true;
	        			}
	        		}
	        	}
	        }
	        return false;
        }
        else if (blackList != null && blackList.size() > 0) {
        	// check the black list
	        for (BiomeTypeHolder holder : blackList) {
	        	// check which dictionary to use
	        	if (holder.getDictionaryId() == 0) {
	        		if (BiomeDictionary.hasType(biome, (Type)holder.getBiomeType())) {
	        			return false;
	        		}
	        	}
	        	else {
	        		// check against all registered BiomeDictionaries
	        		for (IBiomeDictionary d : BiomeDictionaryManager.getInstance().getAll()) {
	        			if (d.isBiomeOfType(biome, (IBiomeType)holder.getBiomeType())) {
	        				return false;
	        			}	        			
	        		}
	        	}
	        }  	
	        return true;
        }
        else {
        	// neither white list nor black list have values = all biomes are valid
        	return true;
        }
	}
}
