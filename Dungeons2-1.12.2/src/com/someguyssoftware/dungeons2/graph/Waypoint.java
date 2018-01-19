/**
 * 
 */
package com.someguyssoftware.dungeons2.graph;

import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Jul 20, 2016
 *
 */
public class Waypoint {
	private int id;
	private ICoords coords;
	private boolean terminated;
	
	/**
	 * 
	 * @param id
	 * @param coords
	 */
	public Waypoint(int id, ICoords coords) {
		setId(id);
		setCoords(coords);
		setTerminated(true);
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
		setTerminated(isTerminal);		
	}

	/**
	 * 
	 * @param id
	 * @param x
	 * @param y
	 * @param z
	 */
	public Waypoint(int id, int x, int y, int z) {
		setId(id);
		setCoords(new Coords(x, y, z));
		setTerminated(true);
	}
	
	/**
	 * 
	 * @param id
	 * @param x
	 * @param y
	 * @param z
	 * @param isTerminal
	 */
	public Waypoint(int id, int x, int y, int z, boolean isTerminal) {
		setId(id);
		setCoords(new Coords(x, y, z));
		setTerminated(isTerminal);		
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 */
	public Waypoint(int x, int y, int z) {
		this(-1, x, y, z);
		setTerminated(true);
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
		return "Waypoint [id=" + id + ", coords=" + coords + ", terminated=" + terminated + "]";
	}

	/**
	 * @return the terminated
	 */
	public boolean isTerminated() {
		return terminated;
	}

	/**
	 * @param terminated the terminated to set
	 */
	public void setTerminated(boolean terminal) {
		this.terminated = terminal;
	}
}
