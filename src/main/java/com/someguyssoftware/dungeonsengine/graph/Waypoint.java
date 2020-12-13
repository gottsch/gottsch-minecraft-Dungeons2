/**
 * 
 */
package com.someguyssoftware.dungeonsengine.graph;

import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * 
 * @author Mark Gottschling on Jan 9, 2019
 *
 */
public class Waypoint {
	private int id;
	private ICoords coords;
	private boolean terminal;
	
	/**
	 * 
	 * @param id
	 * @param coords
	 */
	public Waypoint(int id, ICoords coords) {
		setId(id);
		setCoords(coords);
		setTerminal(true);
	}
	
	/**
	 * 
	 * @param id
	 * @param coords
	 * @param isTerminal
	 */
	public Waypoint(int id, ICoords coords, boolean isTerminal) {
		setId(id);
		setCoords(coords);
		setTerminal(isTerminal);		
	}
	
	/**
	 * 
	 * @return
	 */
	public int getX() {
		return this.coords.getX();
	}
	
	/**
	 * 
	 * @return
	 */
	public int getY() {
		return this.coords.getY();
	}
	
	/**
	 * 
	 * @return
	 */
	public int getZ() {
		return this.coords.getZ();
	}
	
	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @param id the id to set
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

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Waypoint [id=" + id + ", coords=" + coords + ", terminal=" + terminal + "]";
	}

	/**
	 * @return the terminal
	 */
	public boolean isTerminal() {
		return terminal;
	}

	/**
	 * @param terminal the terminal to set
	 */
	public void setTerminal(boolean terminal) {
		this.terminal = terminal;
	}
}
