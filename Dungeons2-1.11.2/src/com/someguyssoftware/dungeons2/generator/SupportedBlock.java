/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import net.minecraft.block.state.IBlockState;

/**
 * @author Mark Gottschling on May 19, 2015
 *
 */

public class SupportedBlock implements ISupportedBlock {
	private IBlockState blockState;
	private int amount;
	
	public SupportedBlock() {
		
	}
	
	public SupportedBlock(IBlockState blockState, int amount) {
		setBlockState(blockState);
		setAmount(amount);
	}
	
	/**
	 * Copy constructor
	 * @param supported
	 */
	public SupportedBlock(ISupportedBlock supported) {
		setBlockState(supported.getBlockState());
		setAmount(supported.getAmount());
	}
	
	/**
	 * @return the block
	 */
	@Override
	public IBlockState getBlockState() {
		return blockState;
	}
	/**
	 * @param block the block to set
	 */
	@Override
	public void setBlockState(IBlockState blockState) {
		this.blockState = blockState;
	}
	/**
	 * @return the amount
	 */
	@Override
	public int getAmount() {
		return amount;
	}
	/**
	 * @param amount the amount to set
	 */
	@Override
	public void setAmount(int amount) {
		this.amount = amount;
	}
}
