/**
 * 
 */
package com.someguyssoftware.gottschcore.block;

import net.minecraft.block.BlockLog;

/**
 * @author Mark Gottschling on Mar 19, 2018
 *
 */
public class ModLogBlock extends BlockLog {
	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public ModLogBlock(String modID, String name) {
		super();
		setBlockName(modID, name);
	}
	
	/**
	 * 
	 * @param modID
	 * @param name
	 */
	public void setBlockName(String modID, String name) {
		setRegistryName(modID, name);
		setUnlocalizedName(getRegistryName().toString());
	}
	
}
