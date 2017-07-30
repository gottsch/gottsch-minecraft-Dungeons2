/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Aug 30, 2016
 *
 */
public class Door {
	private ICoords coords;
	private Room room;
	
	/**
	 * 
	 */
	public Door() {}
	
	/**
	 * 
	 * @param coords
	 * @param room
	 */
	public Door(ICoords coords, Room room) {
		this.coords = coords;
		this.room = room;
	}

	/**
	 * @return the coords
	 */
	public ICoords getCoords() {
		return coords;
	}

	/**
	 * @param coords the coords to set
	 */
	public void setCoords(ICoords coords) {
		this.coords = coords;
	}

	/**
	 * @return the room
	 */
	public Room getRoom() {
		return room;
	}

	/**
	 * @param room the room to set
	 */
	public void setRoom(Room room) {
		this.room = room;
	}
}
