/**
 * 
 */
package com.someguyssoftware.gottschcore.biome;

import net.minecraft.world.biome.Biome;

/**
 * @author Mark Gottschling on Sep 29, 2015
 *
 */
public interface IBiomeDictionary {

    /**
     * Checks to see if the given biome is registered as being a specific type
     *
     * @param biome the biome to be considered
     * @param type the type to check for
     * @return returns true if the biome is registered as being of type type, false otherwise
     */
    public boolean isBiomeOfType(Biome biome, IBiomeType type);
    
    /**
     * 
     * @param name
     * @return
     */
	public IBiomeType getTypeByName(String name);
}
