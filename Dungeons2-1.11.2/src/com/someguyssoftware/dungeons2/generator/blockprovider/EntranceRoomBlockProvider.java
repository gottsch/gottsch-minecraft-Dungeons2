/**
 * 
 */
package com.someguyssoftware.dungeons2.generator.blockprovider;

import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.Layout;
import com.someguyssoftware.mod.ICoords;

/**
 * @author Mark Gottschling on Aug 27, 2016
 *
 */
public class EntranceRoomBlockProvider implements IExteriorDungeonsBlockProvider {

	private static final int INSET_WALL_EXTERIOR_MIN_WIDTH = 7;

	/**
	 * Generates a simple cube room without any decorations (crown, trim, etc)
	 * @param x
	 * @param y
	 * @param z
	 * @param room
	 * @param layout
	 * @return
	 */
	@Override
	public DesignElement getDesignElement(ICoords coords, Room room, Layout layout) {
		// TODO make ceiling height a property of Room, then wouldn't have to recalculate this for every block
		int ceilingHeight = getCeilingHeight(room, layout);
		int corniceHeight = getCorniceHeight(room, layout);
		
		// check for ladder pillar - needs to come before floor
//		if (isLadderPillarElement(x, y, z, room, layout)) return DesignElement.LADDER_PILLAR;
		if (isLadderElement(coords, room, layout)) return DesignElement.LADDER;
		
		if ((room.hasPlinth() || room.hasColumn() || room.hasCornice()) 
				&& room.getWidth() >= INSET_WALL_EXTERIOR_MIN_WIDTH) {	
			if (isInsetWallElement(coords, room, layout)) {
//				return DesignElement.WALL;
				if (isFacadeSupport(coords, room, layout)) return DesignElement.FACADE_SUPPORT;
				return DesignElement.WALL;
			}
			
			DesignElement element = null;
			// build plinths and cornices before because they will get replaced/overwritten by columns
			if (isPlinthElement(coords, room, layout)) element = DesignElement.PLINTH;
			else if (isCorniceElement(coords, room, layout)) element = DesignElement.CORNICE;
			
			if (isColumnElement(coords, room, layout)) {
				// determine if base, shaft or capital
				if (isBaseElement(coords, room, layout)) element = DesignElement.BASE;
				else if (isCapitalElement(coords, room, layout)) element = DesignElement.CAPITAL;
				else element = DesignElement.COLUMN;
//				Dungeons2.log.debug("Is Column Element @ " + coords.toShortString());
			}
			if (element != null) return element;
		}
		else { 
			// TODO should return if any part of the window, then check if base, window, or capital 
			// check for window
			if (isWindowElement(coords, room, layout)) return DesignElement.WINDOW;
						
			// check for wall
			if (isFacadeSupport(coords, room, layout)) return DesignElement.FACADE_SUPPORT;
			if (isWallElement(coords, room, layout)) return DesignElement.WALL;
		}
		
		// check for floor
		if (isFloorElement(coords, room, layout))return DesignElement.FLOOR;		
				
		// check for trim
		if (room.hasTrim() && isTrimElement(coords, room, layout)) return DesignElement.TRIM;		

		// check for crown molding
		if (room.hasCrown() && isCrownElement(coords, room, layout)) return DesignElement.CROWN;

		// check for ceiling
		if(isCeilingElement(coords, room, layout)) return DesignElement.CEILING;
		
		// check for crenellation, merlon and/or parapet
		if (room.hasCrenellation()) {
			if (isCrenellationElement(coords, room, layout)) {
				if (isParapetElement(coords, room, layout)) return DesignElement.PARAPET;
				if (isMerlonElement(coords, room, layout)) return DesignElement.MERLON;
			}
		}
		else if (room.hasMerlon() && isMerlonElement(coords, room, layout)) return DesignElement.MERLON;
		else if (room.hasParapet() && isParapetElement(coords, room, layout)) return DesignElement.PARAPET;
		
		return DesignElement.AIR;
	}
	
	/**
	 * Checks for support block for crown and cornice.
	 * It is assumed that the element at x,y,z has already been determined to be a wall. This method simply checks
	 * the vertical position to determine if this element is the 1 less than the ceiling. No additional checks are made to ensure
	 * that is position is indeed a wall.
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	@Override
	public boolean isFacadeSupport(ICoords coords, Room room, Layout layout) {
		int h = getCeilingHeight(room, layout);
		// check if y is 1 less than top (ceiling)
		if (coords.getY() == h ||
				coords.getY() == h - 1 ||
				coords.getY() == getCorniceHeight(room, layout)) return true;
		return false;
	}
	
}
