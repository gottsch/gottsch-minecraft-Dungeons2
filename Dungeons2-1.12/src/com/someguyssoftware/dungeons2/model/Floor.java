/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Mark Gottschling on Sep 4, 2017
 *
 */
public class Floor extends Space {
	List<IRoom> rooms;
	
	/**
	 * 
	 */
	public Floor() {}

	/**
	 * @return the rooms
	 */
	public List<IRoom> getRooms() {
		if (rooms == null) rooms = new LinkedList<>();
		return rooms;
	}

	/**
	 * @param rooms the rooms to set
	 */
	public void setRooms(List<IRoom> rooms) {
		this.rooms = rooms;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Floor [rooms=" + rooms + "]";
	}
}
