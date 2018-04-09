/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import com.someguyssoftware.dungeons2.Dungeons2;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * @author Mark Gottschling on Sep 3, 2016
 *
 */
public class NullBlock extends Block {

	/**
	 * @param materialIn
	 */
	public NullBlock() {
		super(Material.AIR);
		setRegistryName("null_block");
		setUnlocalizedName(Dungeons2.MODID + "." + "null_block");
	}

}
