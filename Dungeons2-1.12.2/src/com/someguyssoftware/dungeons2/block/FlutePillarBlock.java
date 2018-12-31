/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.block.CardinalDirectionBlock;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/**
 * @author Mark Gottschling on Aug 31, 2016
 *
 */
@Deprecated
public class FlutePillarBlock extends CardinalDirectionBlock {

	/**
	 * 
	 * @param material
	 */
	public FlutePillarBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(Dungeons2.DUNGEONS_TAB);		
	}
}
