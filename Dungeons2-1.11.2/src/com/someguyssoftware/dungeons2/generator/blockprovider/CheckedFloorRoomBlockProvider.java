/**
 * 
 */
package com.someguyssoftware.dungeons2.generator.blockprovider;

import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.Layout;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Feb 14, 2017
 *
 */
public class CheckedFloorRoomBlockProvider implements IDungeonsBlockProvider {
	
	@Override
	public DesignElement getDesignElement(ICoords coords, Room room, Layout layout) {		
		if (isFloorAltElement(coords, room, layout)) {
			return DesignElement.FLOOR_ALT;
		}		
		return IDungeonsBlockProvider.super.getDesignElement(coords, room, layout);
	}
	
	/**
	 * 
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	public boolean isFloorAltElement(ICoords coords, Room room, Layout layout) {
		int x = coords.getX();
		int z = coords.getZ();
		
		// get the indexes
		int xIndex = x - room.getCoords().getX();
		int zIndex = z - room.getCoords().getZ();
		
		int xmod = Math.abs(xIndex % 2);
		int zmod = Math.abs(zIndex % 2);
		
		if (coords.getY() == room.getMinY()) {
			if ((xmod == 0 && zmod == 0) || (xmod == 1 && zmod == 1)) return true;
		}
		return false;
	}
	
}
