/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

/**
 * @author Mark Gottschling on Aug 30, 2016
 * @version 2.0
 * @since 1.0.0
 */
public class Door {	
	/**
	 * @since 2.0
	 */
	private int id;
	private ICoords coords;
	private Room room;
	/**
	 * @since 2.0
	 */
	private Hallway hallway;
	/**
	 * @since 2.0
	 */
	private Direction direction;
	
	/**
	 * 
	 */
	public Door() {
		setId(RandomHelper.randomInt(5001, 9999));
	}
	
	/**
	 * 
	 * @param coords
	 * @param room
	 */
	public Door(ICoords coords, Room room) {
		setId(RandomHelper.randomInt(5001, 9999));
		this.coords = coords;
		this.room = room;
	}
	
	/**
	 * 
	 * @param coords
	 * @param room
	 * @param hallway
	 * @param direction
	 * @since 2.0
	 */
	public Door(ICoords coords, Room room, Hallway hallway, Direction direction) {
		setId(RandomHelper.randomInt(5001, 9999));
		this.coords = coords;
		this.room = room;
		this.hallway = hallway;
		this.direction = direction;
	}

	/**
	 * 
	 * @return
	 */
	public int getId() {
		return id;
	}

	/**
	 * 
	 * @param id
	 */
	public void setId(int id) {
		this.id = id;
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

	public Hallway getHallway() {
		return hallway;
	}

	public void setHallway(Hallway hallway) {
		this.hallway = hallway;
	}

	public Direction getDirection() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}
}
