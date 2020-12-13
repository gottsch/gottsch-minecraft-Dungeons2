/**
 * 
 */
package com.someguyssoftware.dungeons2.rotate;


import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.enums.Rotate;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/**
 * 
 * @author Mark Gottschling on Aug 4, 2016
 *
 */
public class RelativeDirectionRotator implements IRotator {	
	private static final PropertyEnum<EnumFacing> BASE = PropertyDirection.create("base", EnumFacing.class);
	
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
			rotatedState = blockState.withProperty(BASE, EnumFacing.NORTH);
			break;
		case EAST:
			rotatedState = blockState.withProperty(BASE, EnumFacing.EAST);
			break;
		case SOUTH:
			rotatedState = blockState.withProperty(BASE, EnumFacing.SOUTH);
			break;
		case WEST:
			rotatedState = blockState.withProperty(BASE, EnumFacing.WEST);
			break;
			default: break;
		}
		return rotatedState;
	}
	
	@Override
	public int  rotate(IBlockState blockState, Rotate rotate) {
		Block block = blockState.getBlock();
		int rotatedMeta = block.getMetaFromState(blockState);
		EnumFacing base = (EnumFacing)blockState.getValue(BASE);
		
		// switch on the rotation
		switch (rotate) {
		case ROTATE_90:
			// switch on the current facing direction
			switch(base) {
			case NORTH:
				// update the block blockState
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.EAST));	
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.SOUTH));				 
				break;
			case SOUTH:
				rotatedMeta = block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.WEST));
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.NORTH));				 
				break;
			default: break;
			}
			break;
		case ROTATE_180:
			switch(base) {
			case NORTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.SOUTH));				 
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.WEST));				 
				break;
			case SOUTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.NORTH));				 
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.EAST));				 
				break;
			default: break;
			}
			break;
		case ROTATE_270:
			switch(base) {
			case NORTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.WEST));				 
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.NORTH));				 
				break;
			case SOUTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.EAST));				 
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(BASE, EnumFacing.SOUTH));				 
				break;
			default: break;
			}
			break;
		default: break;
		}		
		return rotatedMeta;		
	}
}
