/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.block.CardinalDirectionFacadeBlock;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * @author Mark Gottschling on Aug 31, 2016
 *
 */
public class BasicFacadeBlock extends CardinalDirectionFacadeBlock {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public BasicFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(Dungeons2.DUNGEONS_TAB);
//		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		setBoundingBox(
				new AxisAlignedBB(0F, 0F, 0.5F, 1F, 1F, 1F), 	// N (starts at half)
				new AxisAlignedBB(0F, 0F, 0F, 0.5F, 1F, 1F),  	// E (starts at zero)
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 0.5F),  	// S
				new AxisAlignedBB(0.5F, 0F, 0F, 1F, 1F, 1F)	// W
				);
	}
}
