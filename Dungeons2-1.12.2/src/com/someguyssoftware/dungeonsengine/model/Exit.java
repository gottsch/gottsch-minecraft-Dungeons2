/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

/**
 * @author Mark Gottschling on Jan 9, 2019
 *
 */
public class Exit implements IExit {
	private int id;
	private ICoords coords;
	private IRoom room;
	
	private Direction direction;
	
	/**
	 * 
	 */
	public Exit() {
		setID(RandomHelper.randomInt(5001, 9999));
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IExit#getID()
	 */
	@Override
	public int getID() {
		return id;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IExit#setID(int)
	 */
	@Override
	public IExit setID(int id) {
		this.id = id;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IExit#getCoords()
	 */
	@Override
	public ICoords getCoords() {
		return coords;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IExit#setCoords(com.someguyssoftware.gottschcore.positional.ICoords)
	 */
	@Override
	public IExit setCoords(ICoords coords) {
		this.coords = coords;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IExit#getRoom()
	 */
	@Override
	public IRoom getRoom() {
		return room;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IExit#setRoom(com.someguyssoftware.dungeonsengine.model.IRoom)
	 */
	@Override
	public IExit setRoom(IRoom room) {
		this.room = room;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IExit#getDirection()
	 */
	@Override
	public Direction getDirection() {
		return direction;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IExit#setDirection(com.someguyssoftware.gottschcore.enums.Direction)
	 */
	@Override
	public IExit setDirection(Direction direction) {
		this.direction = direction;
		return this;
	}
}
