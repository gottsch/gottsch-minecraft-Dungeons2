/**
 * 
 */
package com.someguyssoftware.dungeons2.rotate;

import com.someguyssoftware.mod.enums.Direction;
import com.someguyssoftware.mod.enums.Rotate;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;

/**
 * 
 * @author Mark Gottschling on Sep 9, 2016
 *
 */
public class VineRotator implements IRotator {	
    private static final PropertyBool NORTH = PropertyBool.create("north");
    private static final PropertyBool EAST = PropertyBool.create("east");
    private static final PropertyBool SOUTH = PropertyBool.create("south");
    private static final PropertyBool WEST = PropertyBool.create("west");
	
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
			rotatedState = blockState.withProperty(NORTH, Boolean.valueOf(true));
			break;
		case EAST:
			rotatedState = blockState.withProperty(EAST, Boolean.valueOf(true));
			break;
		case SOUTH:
			rotatedState = blockState.withProperty(SOUTH, Boolean.valueOf(true));
			break;
		case WEST:
			rotatedState = blockState.withProperty(WEST, Boolean.valueOf(true));
			break;
			default: break;
		}
		return rotatedState;
	}
	
	@Override
	public int  rotate(IBlockState blockState, Rotate rotate) {
		Block block = blockState.getBlock();
		int rotatedMeta = block.getMetaFromState(blockState);
//		EnumFacing base = blockState.getValue(FACING);
		PropertyBool base = null;
		if (blockState.getValue(NORTH).booleanValue()) base = NORTH;
		else if (blockState.getValue(SOUTH).booleanValue()) base = SOUTH;
		else if (blockState.getValue(EAST).booleanValue()) base = EAST;
		else if (blockState.getValue(WEST).booleanValue()) base = WEST;
		
		// switch on the rotation
		switch (rotate) {
		case ROTATE_90:
			// switch on the current facing direction
			if (base ==NORTH) rotatedMeta =block.getMetaFromState(blockState.withProperty(EAST, Boolean.valueOf(true)));	
			if (base == EAST) rotatedMeta =block.getMetaFromState(blockState.withProperty(SOUTH, Boolean.valueOf(true)));
			if (base == SOUTH) rotatedMeta = block.getMetaFromState(blockState.withProperty(WEST, Boolean.valueOf(true)));
			if (base == WEST) rotatedMeta =block.getMetaFromState(blockState.withProperty(NORTH, Boolean.valueOf(true)));
			break;
		case ROTATE_180:
			if (base ==NORTH) rotatedMeta =block.getMetaFromState(blockState.withProperty(SOUTH, Boolean.valueOf(true)));	
			if (base == EAST) rotatedMeta =block.getMetaFromState(blockState.withProperty(WEST, Boolean.valueOf(true)));
			if (base == SOUTH) rotatedMeta = block.getMetaFromState(blockState.withProperty(NORTH, Boolean.valueOf(true)));
			if (base == WEST) rotatedMeta =block.getMetaFromState(blockState.withProperty(EAST, Boolean.valueOf(true)));
			break;
		case ROTATE_270:
			if (base ==NORTH) rotatedMeta =block.getMetaFromState(blockState.withProperty(WEST, Boolean.valueOf(true)));	
			if (base == EAST) rotatedMeta =block.getMetaFromState(blockState.withProperty(NORTH, Boolean.valueOf(true)));
			if (base == SOUTH) rotatedMeta = block.getMetaFromState(blockState.withProperty(EAST, Boolean.valueOf(true)));
			if (base == WEST) rotatedMeta =block.getMetaFromState(blockState.withProperty(SOUTH, Boolean.valueOf(true)));
			break;
		default: break;
		}		
		return rotatedMeta;		
	}
}
