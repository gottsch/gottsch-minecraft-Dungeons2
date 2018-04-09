/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import net.minecraft.util.EnumFacing;

/**
 * @author Mark Gottschling on Aug 4, 2016
 *
 */
public enum Location {
	NORTH_SIDE,
	EAST_SIDE,
	SOUTH_SIDE,
	WEST_SIDE,
	MIDDLE;
	
	/**
	 * 
	 * @return
	 */
	public EnumFacing getFacing() {
		if (this == NORTH_SIDE) return EnumFacing.SOUTH;
		if (this == EAST_SIDE) return EnumFacing.WEST;
		if (this == SOUTH_SIDE) return EnumFacing.NORTH;
		if (this == WEST_SIDE) return EnumFacing.EAST;
		return EnumFacing.NORTH;
	}
}
