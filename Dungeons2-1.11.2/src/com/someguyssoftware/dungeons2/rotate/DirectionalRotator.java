/**
 * 
 */
package com.someguyssoftware.dungeons2.rotate;

import com.someguyssoftware.mod.enums.Direction;
import com.someguyssoftware.mod.enums.Rotate;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/**
 * 
 * @author Mark Gottschling on Aug 4, 2016
 *
 */
public class DirectionalRotator implements IRotator {
	private static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
	
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
		case NORTH:
			rotatedState = blockState.withProperty(FACING, EnumFacing.NORTH);
			break;
		case EAST:
			rotatedState = blockState.withProperty(FACING, EnumFacing.EAST);
			break;
		case SOUTH:
			rotatedState = blockState.withProperty(FACING, EnumFacing.SOUTH);
			break;
		case WEST:
			rotatedState = blockState.withProperty(FACING, EnumFacing.WEST);
			break;
			default: break;
		}
		return rotatedState;
	}
	
	/**
	 * @return the rotate meta value
	 */
	@Override
	public int  rotate(IBlockState blockState, Rotate rotate) {
		Block block = blockState.getBlock();
		int rotatedMeta =block.getMetaFromState(blockState);

		EnumFacing facing = (EnumFacing)blockState.getValue(FACING);
		
		// switch on the rotation
		switch (rotate) {
		case ROTATE_90:
			// switch on the current facing direction
			switch(facing) {
			case NORTH:
				// update the block state
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.EAST));	
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.SOUTH));				 
				break;
			case SOUTH:
				rotatedMeta = block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.WEST));
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.NORTH));				 
				break;
			default: break;
			}
			break;
		case ROTATE_180:
			switch(facing) {
			case NORTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.SOUTH));				 
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.WEST));				 
				break;
			case SOUTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.NORTH));				 
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.EAST));				 
				break;
			default: break;
			}
			break;
		case ROTATE_270:
			switch(facing) {
			case NORTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.WEST));				 
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.NORTH));				 
				break;
			case SOUTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.EAST));				 
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumFacing.SOUTH));				 
				break;
			default: break;
			}
			break;
		default: break;
		}
		
		return rotatedMeta;		
	}
}
