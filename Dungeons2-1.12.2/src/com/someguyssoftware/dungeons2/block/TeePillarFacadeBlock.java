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
public class TeePillarFacadeBlock extends CardinalDirectionFacadeBlock {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public TeePillarFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(Dungeons2.DUNGEONS_TAB);
		setBoundingBox(
				new AxisAlignedBB(0F, 0F, 0.25F, 1F, 1F, 1F),
				new AxisAlignedBB(0F, 0F, 0F, 0.25F, 1F, 1F), 
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 0.25F), 
				new AxisAlignedBB(0.25F, 0F, 0F, 0F, 1F, 1F)
				);		
	}
}
