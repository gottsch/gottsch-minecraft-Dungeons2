/**
 * 
 */
package com.someguyssoftware.dungeons2.rotate;


import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.enums.Rotate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockLever.EnumOrientation;
import net.minecraft.block.BlockLog.EnumAxis;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;

/**
 * @author Mark Gottschling on Sep 10, 2015
 *
 */
public class LogRotator implements IRotator {
	public static final PropertyEnum<EnumAxis> AXIS = PropertyEnum.create("axis", BlockLog.EnumAxis.class);
	
	/**
	 * 
	 * @param blockState
	 * @param direction the direction the blockState should face.
	 * @return
	 */
	@Override
	public IBlockState rotate(IBlockState blockState, Direction direction) {
		IBlockState rotatedState = blockState;
		switch(direction) {
		case EAST:
			rotatedState = blockState.withProperty(AXIS, EnumAxis.Z);
			break;
		case WEST:
			rotatedState = blockState.withProperty(AXIS, EnumAxis.Z);
			break;
			default: break;
		}
		return rotatedState;
	}
	
	@Override
	public int  rotate(IBlockState blockState, Rotate rotate) {
		Block block = blockState.getBlock();
		int rotatedMeta = block.getMetaFromState(blockState);

		BlockLog.EnumAxis axis = (EnumAxis) blockState.getValue(AXIS);
		
		// switch on the rotation
		switch (rotate) {
		case ROTATE_90:
			// switch on the current facing direction
			switch(axis) {
			case X:
				// update the block blockState
				rotatedMeta =block.getMetaFromState(blockState.withProperty(AXIS, EnumAxis.Z));	
				break;
			case Z:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(AXIS, EnumAxis.X));				 
				break;
			default: break;
			}
			break;
		case ROTATE_270:
			switch(axis) {
			case X:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(AXIS, EnumAxis.Z));				 
				break;
			case Z:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(AXIS, EnumAxis.X));				 
				break;
			default: break;
			}
			break;
		default: break;
		}
		
		return rotatedMeta;		
	}
}
