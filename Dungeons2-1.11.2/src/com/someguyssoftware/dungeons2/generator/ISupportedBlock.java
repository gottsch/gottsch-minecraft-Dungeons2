package com.someguyssoftware.dungeons2.generator;

import net.minecraft.block.state.IBlockState;

public interface ISupportedBlock {

	/**
	 * @return the block
	 */
	IBlockState getBlockState();

	/**
	 * @param block the block to set
	 */
	void setBlockState(IBlockState state);

	/**
	 * @return the amount
	 */
	int getAmount();

	/**
	 * @param amount the amount to set
	 */
	void setAmount(int amount);

}