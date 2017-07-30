/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.gottschcore.enums.Direction;

/**
 * @author Mark Gottschling on Aug 4, 2016
 *
 */
public class Arrangement {
	private DesignElement element;
	private Direction direction;
	private Location location;
	
	/**
	 * 
	 */
	public Arrangement() {
		
	}
	
	/**
	 * 
	 * @param element
	 * @param locatoin
	 * @param direction
	 */
	public Arrangement(DesignElement element, Location locatoin, Direction direction) {
		setElement(element);
		setLocation(locatoin);
		setDirection(direction);
	}

	/**
	 * @return the element
	 */
	public DesignElement getElement() {
		return element;
	}

	/**
	 * @param element the element to set
	 */
	public void setElement(DesignElement element) {
		this.element = element;
	}

	/**
	 * @return the direction
	 */
	public Direction getDirection() {
		return direction;
	}

	/**
	 * @param direction the direction to set
	 */
	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	/**
	 * @return the location
	 */
	public Location getLocation() {
		return location;
	}

	/**
	 * @param location the location to set
	 */
	public void setLocation(Location location) {
		this.location = location;
	}
}
