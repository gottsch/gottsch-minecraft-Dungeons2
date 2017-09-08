/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import java.util.Comparator;
import java.util.List;

import com.someguyssoftware.dungeons2.style.Layout;

/**
 * @author Mark Gottschling on Sep 5, 2017
 *
 */
public interface IRoom extends ISpace {

	public IRoom copy();
	
	public Layout getLayout();
	public void setLayout(Layout layout);
	
	public List<Door> getDoors();
	public void setDoors(List<Door> doors);
	public boolean hasDoors();
	
	public double getDistance();
	public void setDistance(double distance);
	
	public boolean isAnchor();
	public IRoom setAnchor(boolean anchor);
	
	public boolean isReject();
	public IRoom setReject(boolean reject);
	
	/**
	 * Comparator to sort by Id
	 */
	public static final Comparator<IRoom> idComparator = new Comparator<IRoom>() {
		@Override
		public int compare(IRoom p1, IRoom p2) {
			if (p1.getID() > p2.getID()) {
				// greater than
				return 1;
			}
			else {
				// less than
				return -1;
			}
		}
	};
	
	/**
	 * Comparator to sort plans by set weight
	 */
	public static final Comparator<IRoom> distanceComparator = new Comparator<IRoom>() {
		@Override
		public int compare(IRoom p1, IRoom p2) {
			if (p1.getDistance() > p2.getDistance()) {
				// greater than
				return 1;
			}
			else {
				// less than
				return -1;
			}
		}
	};
}
