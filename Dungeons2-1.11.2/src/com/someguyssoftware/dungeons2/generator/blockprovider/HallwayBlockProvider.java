/**
 * 
 */
package com.someguyssoftware.dungeons2.generator.blockprovider;

import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.Layout;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Aug 28, 2016
 *
 */
public class HallwayBlockProvider implements IDungeonsBlockProvider {
	
	/**
	 * 
	 */
	public DesignElement getDesignElement(ICoords coords, Room room, Layout layout) {
		
		// check for floor
		if (isFloorElement(coords, room, layout))return DesignElement.FLOOR;
		
		// check for wall
		if (isWallElement(coords, room, layout)) return DesignElement.WALL;

		// check for ceiling
		if(isCeilingElement(coords, room, layout)) return DesignElement.CEILING;
		
		return DesignElement.AIR;
	}
}
