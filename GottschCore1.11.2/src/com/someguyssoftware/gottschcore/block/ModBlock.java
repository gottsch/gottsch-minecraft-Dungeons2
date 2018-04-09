package com.someguyssoftware.gottschcore.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * A thin wrapper on Block to add some basic additions/conveniences/
 * 
 * @author Mark Gottschling on Jul 23, 2017
 *
 */
public class ModBlock extends Block {
	private boolean normalCube = true;
	
	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public ModBlock(String modID, String name, Material material) {
		super(material);
		setBlockName(modID, name);
	}

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 * @param mapColor
	 */
	public ModBlock(String modID, String name, Material material, MapColor mapColor) {
		super(material, mapColor);
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
	public ModBlock setSoundType(SoundType sound) {
        this.blockSoundType = sound;
        return this;
	}
}
