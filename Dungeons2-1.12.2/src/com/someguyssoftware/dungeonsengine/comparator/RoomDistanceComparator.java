/**
 * 
 */
package com.someguyssoftware.dungeonsengine.comparator;

import java.util.Comparator;

import com.someguyssoftware.dungeonsengine.model.IRoom;
import com.someguyssoftware.dungeonsengine.model.Room;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Sep 24, 2018
 *
 */
public class RoomDistanceComparator implements Comparator<IRoom> {

	private ICoords point;
	
	/**
	 * 
	 */
	public RoomDistanceComparator(ICoords point) {
		this.point = point;
	}

	/**
	 * 
	 */
	@Override
	public int compare(IRoom p1, IRoom p2) {
		if (p1.getCenter().getDistanceSq(this.point) > p2.getCenter().getDistanceSq(this.point)) {
			// greater than
			return 1;
		}
		else {
			// less than
			return -1;
		}
	}
}
