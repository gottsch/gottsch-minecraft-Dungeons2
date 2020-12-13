/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.block.RelativeDirectionFacadeBlock;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * 
 * @author Mark Gottschling on Aug 28, 2016
 *
 */
public class HalfPillarBlock extends RelativeDirectionFacadeBlock {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public HalfPillarBlock(String modID, String name, Material material) {
		super(modID, name, material);		
		setSoundType(SoundType.STONE);
		setBoundingBox(
				new AxisAlignedBB(0.25F, 0F, 0.25F, 0.75F, 1F, 0.75F), // NORTH
				new AxisAlignedBB(0.25F, 0F, 0.25F, 0.75F, 1F, 0.75F), // EAST
				new AxisAlignedBB(0.25F, 0F, 0.25F, 0.75F, 1F, 0.75F),	 // SOUTH
				new AxisAlignedBB(0.25F, 0F, 0.25F, 0.75F, 1F, 0.75F)); // WEST
		setCreativeTab(Dungeons2.DUNGEONS_TAB);
	}	
}
