/**
 * 
 */
package com.someguyssoftware.gottschcore.block;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * @author Mark Gottschling on Mar 2, 2018
 *
 */
public class ModFallingBlock extends BlockFalling {
	private boolean normalCube = true;
	
	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public ModFallingBlock(String modID, String name, Material material) {
		super(material);
		setBlockName(modID, name);
	}
	
	/**
	 * 
	 * @param modID
	 * @param name
	 */
	public void setBlockName(String modID, String name) {
		setRegistryName(modID, name);
		setUnlocalizedName(getRegistryName().toString());
	}
	
	/**
	 * 
	 */
	@Override
	public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
		return normalCube;
	}
	
	/**
	 * 
	 * @param normalCube
	 */
	protected void setNormalCube(boolean normalCube) {
		this.normalCube = normalCube;
	}
	
	@Override
	public ModFallingBlock setSoundType(SoundType sound) {
        this.blockSoundType = sound;
        return this;
	}
}
