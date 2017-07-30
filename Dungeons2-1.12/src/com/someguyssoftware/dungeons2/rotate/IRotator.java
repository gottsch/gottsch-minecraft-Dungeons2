package com.someguyssoftware.dungeons2.rotate;


import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.enums.Rotate;

import net.minecraft.block.state.IBlockState;

/**
 * This does NOT depend on PlansApi.  It is a stand-alone interface for Dungeons2.
 * @author Mark Gottschling on Aug 4, 2016
 *
 */
public interface IRotator {

	public int rotate(IBlockState blockState, Rotate rotate);

	public IBlockState rotate(IBlockState blockState, Direction direction);

}