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
 * @author Mark Gottschling on Sep 3, 2016
 *
 */
public class SevenEightsPillarFacadeBlock extends CardinalDirectionFacadeBlock {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public SevenEightsPillarFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(Dungeons2.DUNGEONS_TAB);
		setBoundingBox(
				new AxisAlignedBB(0.0625F, 0, 0.0625F, 0.9375F, 1F, 0.9375F),
				new AxisAlignedBB(0.0625F, 0, 0.0625F, 0.9375F, 1F, 0.9375F),
				new AxisAlignedBB(0.0625F, 0, 0.0625F, 0.9375F, 1F, 0.9375F),
				new AxisAlignedBB(0.0625F, 0, 0.0625F, 0.9375F, 1F, 0.9375F)
				);
	}
}
