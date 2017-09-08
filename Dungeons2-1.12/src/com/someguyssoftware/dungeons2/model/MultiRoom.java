package com.someguyssoftware.dungeons2.model;

import java.util.List;

/**
 * TODO Figure this out. A class to hold multiple overlapping rooms to form one odd-shaped room.
 * therefor should extend a base Room class having same props as a room, but with an added list of other Rooms... like a Floor but these rooms overlap.
 * 
 * @author Mark Gottschling on Sep 5, 2017
 *
 */
public class MultiRoom extends AbstractRoom {
	private List<IRoom> rooms;
	
	/**
	 * 
	 */
	public MultiRoom() {}

	// TODO add multi room specific methods here, such as finding a wall to attach a door to. ie the outer dimension aren't necessarily where the 
	// actual wall is located ie need to find the given X point for a given Z point and vise versa.
	
	/**
	 * @return the rooms
	 */
	public List<IRoom> getRooms() {
		return rooms;
	}

	/**
	 * @param rooms the rooms to set
	 */
	public void setRooms(List<IRoom> rooms) {
		this.rooms = rooms;
	}
}
