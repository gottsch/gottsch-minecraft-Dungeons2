/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import com.someguyssoftware.gottschcore.block.RelativeDirectionFacadeBlock;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * 
 * @author Mark Gottschling on Sep 6, 2016
 *
 */
public class GrateBlock extends RelativeDirectionFacadeBlock {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public GrateBlock(String modID, String name, Material material) {
		super(modID, name, material);		
		setSoundType(SoundType.STONE);
		setBoundingBox(
				new AxisAlignedBB(0F, 0.5F, 0F, 1F, 1F, 1F),	// NORTH
				new AxisAlignedBB(0F, 0.5F, 0F, 1F, 1F, 1F),	// EAST
				new AxisAlignedBB(0F, 0.5F, 0F, 1F, 1F, 1F),	// SOUTH
				new AxisAlignedBB(0F, 0.5F, 0F, 1F, 1F, 1F));	// WEST
		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		this.setDefaultState(blockState.getBaseState().withProperty(BASE, EnumFacing.DOWN));
	}	
}
