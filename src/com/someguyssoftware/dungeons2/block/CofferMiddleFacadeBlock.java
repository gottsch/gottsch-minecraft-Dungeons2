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
public class CofferMiddleFacadeBlock extends CardinalDirectionFacadeBlock {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public CofferMiddleFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(Dungeons2.DUNGEONS_TAB);		
		setBoundingBox(
				new AxisAlignedBB(0D, 0.5D, 0D, 1D, 1D, 1D),
				new AxisAlignedBB(0D, 0.5D, 0D, 1D, 1D, 1D), 
				new AxisAlignedBB(0D, 0.5D, 0D, 1D, 1D, 1D), 
				new AxisAlignedBB(0D, 0.5D, 0D, 1D, 1D, 1D)
				);
	}
}
