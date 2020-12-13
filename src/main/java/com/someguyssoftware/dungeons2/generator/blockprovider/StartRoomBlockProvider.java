/**
 * 
 */
package com.someguyssoftware.dungeons2.generator.blockprovider;

import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.Layout;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Aug 27, 2016
 *
 */
public class StartRoomBlockProvider implements IDungeonsBlockProvider {

	/**
	 * Generates a simple cube room without any decorations (crown, trim, etc)
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	@Override
	public DesignElement getDesignElement(ICoords coords, Room room, Layout layout) {

		// check for floor
		if (isFloorElement(coords, room, layout))return DesignElement.FLOOR;
		// check for wall
		if (isWallElement(coords, room, layout)) {
			if (isFacadeSupport(coords, room, layout)) return DesignElement.FACADE_SUPPORT;
			return DesignElement.WALL;
		}
		
		// check for ladder pillar - needs to come before ceiling
		if (isLadderPillarElement(coords, room, layout)) return DesignElement.LADDER_PILLAR;
		if (isLadderElement(coords, room, layout)) return DesignElement.LADDER;
		
		
		/*
		 * pillars should only be near corners ie only 4 pillars near corners - not necessarily attached.
		 */
		if (room.hasPillar() && isPillarElement(coords, room, layout)) {
			if (isBaseElement(coords, room, layout)) return DesignElement.PILLAR_BASE;
			else if (isCapitalElement(coords, room, layout)) return DesignElement.PILLAR_CAPITAL;
			else return DesignElement.PILLAR;			
		}

		// check for crown molding
		if (room.hasCrown() && isCrownElement(coords, room, layout)) return DesignElement.CROWN;
		// check for trim
		if (room.hasTrim() && isTrimElement(coords, room, layout)) return DesignElement.TRIM;		

		// check for ceiling
		if(isCeilingElement(coords, room, layout)) return DesignElement.CEILING;
		
		return DesignElement.AIR;
	}
	
	// TODO move to AbstractAdvanced or AbstractSpecial RoomGenerator --> actually to AbstractStartRoomGenerator
	/**
	 * 
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	public boolean isLadderPillarElement(ICoords coords, Room room, Layout layout) {
		ICoords center = room.getCenter();
		if (coords.getX() == center.getX() && coords.getZ() == center.getZ() && coords.getY() > room.getMinY()) return true;
		return false;
	}
	
	/**
	 * Determines whether the position at (x, y, z) is a ladder
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	@Override
	public boolean isLadderElement(ICoords coords, Room room, Layout layout) {
		ICoords center = room.getCenter();
		Direction direction = room.getDirection();

		// want the ladder on the opposite side the room is facing ie if room faces north, want the ladder on the south side of pillar (so that the ladder still faces north)
		switch(direction) {		
		case NORTH:
			if (coords.getX() == center.getX() && coords.getZ() == (center.getZ()+1) && coords.getY() > room.getMinY()) return true;
			break;
		case EAST:
			if (coords.getX() == (center.getX()-1) && coords.getZ() == center.getZ() && coords.getY() > room.getMinY()) return true;
			break;
		case SOUTH:
			if (coords.getX() == center.getX() && coords.getZ() == (center.getZ()-1) && coords.getY() > room.getMinY()) return true;
			break;
		case WEST:
			if (coords.getX() == (center.getX()+1) && coords.getZ() == center.getZ() && coords.getY() > room.getMinY()) return true;
			break;
		default:			
		}
		return false;
	}

	//  pillar
	@Override
	public boolean isPillarElement(ICoords coords, Room room, Layout layout) {
		int x = coords.getX();
		int y = coords.getY();
		int z = coords.getZ();
		
		if (!room.hasPillar() || Math.min(room.getWidth(), room.getDepth()) < 7 || y == room.getMaxY()) return false;
		
		if ((x == room.getMinX() + 2 || x == room.getMaxX() -2) && (z == room.getMinZ() + 2 || z == room.getMaxZ() - 2)) return true;
		return false;
	}
}
