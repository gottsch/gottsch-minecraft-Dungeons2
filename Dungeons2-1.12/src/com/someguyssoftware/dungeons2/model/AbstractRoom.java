/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.someguyssoftware.dungeons2.style.Layout;

/**
 * @author Mark Gottschling on Sep 5, 2017
 *
 */
public abstract class AbstractRoom extends Space implements IRoom {
	private boolean anchor;
	private boolean reject;
	private double distance;
	private List<Door> doors;
	private Layout layout;
	
	/**
	 * 
	 */
	public AbstractRoom() {
		super();
	}
	
	/**
	 * Comparator to sort by Id
	 */
	public static Comparator<IRoom> idComparator = new Comparator<IRoom>() {
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
	public static Comparator<IRoom> distanceComparator = new Comparator<IRoom>() {
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
	
	/**
	 * @return the doors
	 */
	public List<Door> getDoors() {
		if (doors == null) doors = new ArrayList<>(3);
		return doors;
	}
	/**
	 * @param doors the doors to set
	 */
	public void setDoors(List<Door> doors) {
		this.doors = doors;
	}
	
	/**
	 * Determines if the room has doors defined.
	 * Proper usage will help reduce lazy-initialization of doors.
	 */
	public boolean hasDoors() {
		if (doors != null && doors.size() > 0) return true;
		return false;
	}
	
	/**
	 * @return the layout
	 */
	public Layout getLayout() {
		return layout;
	}
	/**
	 * @param layout the layout to set
	 */
	public void setLayout(Layout layout) {
		this.layout = layout;
	}
	/**
	 * @return the anchor
	 */
	public boolean isAnchor() {
		return anchor;
	}
	/**
	 * @param anchor the anchor to set
	 */
	public IRoom setAnchor(boolean anchor) {
		this.anchor = anchor;
		return this;
	}
	/**
	 * @return the distance
	 */
	@Override
	public double getDistance() {
		return distance;
	}
	/**
	 * @param distance the distance to set
	 */
	@Override
	public void setDistance(double distance) {
		this.distance = distance;
	}
	/**
	 * @return the reject
	 */
	public boolean isReject() {
		return reject;
	}
	/**
	 * @param reject the reject to set
	 */
	public IRoom setReject(boolean reject) {
		this.reject = reject;
		return this;
	}
}
