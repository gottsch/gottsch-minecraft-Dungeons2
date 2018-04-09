/**
 * 
 */
package com.someguyssoftware.gottschcore.cube;

import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;

/**
 * For Forge 1.8+
 * Wrapper class for Block/BlockState.
 * @author Mark Gottschling on May 6, 2017
 *
 */
public class Cube {
	private World world;
	private ICoords coords;
	private IBlockState state;
	
	/**
	 * 
	 * @param world
	 * @param coords
	 */
	public Cube(World world, ICoords coords) {
		this.world = world;
		this.coords = coords;
		this.state = world.getBlockState(coords.toPos());
	}
	
	public IBlockState getState() {return null;}
	
	/**
	 * 
	 * @param world
	 * @return
	 */
	public Block toBlock() {
		IBlockState blockState = this.world.getBlockState(this.coords.toPos());
		if (blockState != null) return blockState.getBlock();
		return null;
	}
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @return
	 */
	public static Block toBlock(final World world, final ICoords coords) {
		IBlockState blockState = world.getBlockState(coords.toPos());
		if (blockState != null) return blockState.getBlock();
		return null;
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean hasState() {
		IBlockState blockState = world.getBlockState(coords.toPos());
		if (blockState == null) return false;
		return true;
	}
	
	/**
	 * 
	 * @param world
	 * @param block
	 * @return
	 */
	public boolean equalsBlock(Block block) {
		if (state.getBlock() == block) return true;
		return false;
	}

	/**
	 * 
	 * @param world
	 * @param material
	 * @return
	 */
	public boolean equalsMaterial(Material material) {		
		if (state.getMaterial() == material) return true;
		return false;	
	}
	
	/**
	 * Wrapper for Material.isReplaceable();
	 * @return
	 */
	public boolean isReplaceable() {
		return state.getMaterial().isReplaceable();
	}
	
	/**
	 * Wrapper for Material.isSolid()
	 * @return
	 */
	public boolean isSolid() {
		return getState().getMaterial().isSolid();
	}
	
	/**
	 * Wrapper to Block.isBurning()
	 * @param world
	 * @return
	 */
	public boolean isBurning() {
		return toBlock().isBurning(this.world, this.coords.toPos());
	}
	
	/**
	 * @return the coords
	 */
	public ICoords getCoords() {
		return coords;
	}

	/**
	 * @param coords the coords to set
	 */
	public void setCoords(ICoords coords) {
		this.coords = coords;
	}
}
