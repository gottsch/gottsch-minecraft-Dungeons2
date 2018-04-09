/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

/**
 * 
 * @author Mark Gottschling on Aug 15, 2016
 *
 */
public class Shaft extends Room {
	Room parent;

	public Shaft() {
		super();
	}

	/**
	 * @return the parent
	 */
	public Room getParent() {
		return parent;
	}

	/**
	 * @param parent the parent to set
	 */
	public void setParent(Room parent) {
		this.parent = parent;
	}
}
