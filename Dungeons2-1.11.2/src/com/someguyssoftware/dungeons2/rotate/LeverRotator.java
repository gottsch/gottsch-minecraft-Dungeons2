/**
 * 
 */
package com.someguyssoftware.dungeons2.rotate;

import com.someguyssoftware.mod.enums.Direction;
import com.someguyssoftware.mod.enums.Rotate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockLever.EnumOrientation;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/**
 * 
 * @author Mark Gottschling on Aug 5, 2016
 *
 */
public class LeverRotator implements IRotator {
	public static final PropertyEnum<EnumOrientation> FACING = PropertyEnum.create("facing", BlockLever.EnumOrientation.class);
	
	/**
	 * 
	 * @param blockState
	 * @param direction the direction the blockState should face.
	 * @return
	 */
	@Override
	public IBlockState rotate(IBlockState blockState, Direction direction) {
		IBlockState rotatedState = blockState;;
		switch(direction) {
		case NORTH:
			rotatedState = blockState.withProperty(FACING, EnumOrientation.NORTH);
			break;
		case EAST:
			rotatedState = blockState.withProperty(FACING, EnumOrientation.EAST);
			break;
		case SOUTH:
			rotatedState = blockState.withProperty(FACING, EnumOrientation.SOUTH);
			break;
		case WEST:
			rotatedState = blockState.withProperty(FACING, EnumOrientation.WEST);
			break;
			default: break;
		}
		return rotatedState;
	}
	
	@Override
	public int  rotate(IBlockState blockState, Rotate rotate) {
		Block block = blockState.getBlock();
		int rotatedMeta = block.getMetaFromState(blockState);		

		BlockLever.EnumOrientation facing = (EnumOrientation) blockState.getValue(FACING);
		
		// switch on the rotation
		switch (rotate) {
		case ROTATE_90:
			// switch on the current facing direction
			switch(facing) {
			case UP_X:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.UP_Z));	
				break;
			case UP_Z:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.UP_X));	
				break;
			case DOWN_X:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.DOWN_Z));	
				break;
			case DOWN_Z:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.DOWN_X));	
				break;	
			case NORTH:
				// update the block blockState
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.EAST));	
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.SOUTH));				 
				break;
			case SOUTH:
				rotatedMeta = block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.WEST));
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.NORTH));				 
				break;
			default: 
				break;
			}
			break;
		case ROTATE_180:
			switch(facing) {
			case NORTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.SOUTH));				 
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.WEST));				 
				break;
			case SOUTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.NORTH));				 
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.EAST));				 
				break;
			default: break;
			}
			break;
		case ROTATE_270:
			switch(facing) {
			case UP_X:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.UP_Z));	
				break;
			case UP_Z:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.UP_X));	
				break;
			case DOWN_X:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.DOWN_Z));	
				break;
			case DOWN_Z:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.DOWN_X));	
				break;			
			case NORTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.WEST));				 
				break;
			case EAST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.NORTH));				 
				break;
			case SOUTH:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.EAST));				 
				break;
			case WEST:
				rotatedMeta =block.getMetaFromState(blockState.withProperty(FACING, EnumOrientation.SOUTH));				 
				break;
			default: break;
			}
			break;
		default: break;
		}
		
		return rotatedMeta;		
	}
}
