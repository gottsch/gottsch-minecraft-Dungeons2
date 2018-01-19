/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import com.someguyssoftware.gottschcore.block.RelativeDirectionFacadeBlock;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;

/**
 * @author Mark Gottschling on Aug 23, 2016
 *
 */
@Deprecated
public class HalfPillarBaseBlock extends RelativeDirectionFacadeBlock {

	/**
	 * 
	 * @param material
	 */
	public HalfPillarBaseBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
	}	
}
