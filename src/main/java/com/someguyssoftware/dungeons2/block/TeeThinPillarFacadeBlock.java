/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.block.CardinalDirectionFacadeBlock;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * @author Mark Gottschling on Aug 31, 2016
 *
 */
public class TeeThinPillarFacadeBlock extends CardinalDirectionFacadeBlock {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public TeeThinPillarFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(Dungeons2.DUNGEONS_TAB);
		setBoundingBox(
				new AxisAlignedBB(0F, 0F, 0.625F, 1F, 1F, 1F),
				new AxisAlignedBB(0F, 0F, 0F, 0.375F, 1F, 1F), 
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 0.375F), 
				new AxisAlignedBB(0.625F, 0F, 0F, 1F, 1F, 1F)
				);		
	}	

}
