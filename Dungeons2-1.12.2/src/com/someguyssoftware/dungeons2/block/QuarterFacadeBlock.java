/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * @author Mark Gottschling on Jan 14, 2019
 *
 */
public class QuarterFacadeBlock extends BasicFacadeBlock {

	/**
	 * @param modID
	 * @param name
	 * @param material
	 */
	public QuarterFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setBounds(new AxisAlignedBB[]{
				// straight
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 0.25F),	// S
				new AxisAlignedBB(0.75F, 0F, 0F, 1F, 1F, 1F),	// W
				new AxisAlignedBB(0F, 0F, 0.75F, 1F, 1F, 1F),	// N (starts at half)
				new AxisAlignedBB(0F, 0F, 0F, 0.25F, 1F, 1F),	// E (starts at zero)
				
				// inner left
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// S
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// W
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// N
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// E
							
				// inner right
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// S
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// W
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// N
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// E
				
				// outer left
				new AxisAlignedBB(0.75F, 0F, 0F, 1F, 1F, 0.25F),	// S
				new AxisAlignedBB(0.75F, 0F, 0.75F, 1F, 1F, 1F),	// W
				new AxisAlignedBB(0F, 0F, 0.75F, 0.25F, 1F, 1F),	// N (starts at half)
				new AxisAlignedBB(0F, 0F, 0F, 0.75F, 1F, 0.75F),	// E (starts at zero)		
				
				// outer right
				new AxisAlignedBB(0F, 0F, 0F, 0.25F, 1F, 0.25F),	// S
				new AxisAlignedBB(0.75F, 0F, 0F, 1F, 1F, 0.25F),	// W
				new AxisAlignedBB(0.75F, 0F, 0.75F, 1F, 1F, 1F),	// N (starts at half)
				new AxisAlignedBB(0F, 0F, 0.75F, 0.25F, 1F, 1F)	// E (starts at zero)					
		});
	}
	
    /**
     * Checks if a block is basic
     */
    public static boolean isBlockBasic(Block block) {
        return block instanceof QuarterFacadeBlock;
    }

}
