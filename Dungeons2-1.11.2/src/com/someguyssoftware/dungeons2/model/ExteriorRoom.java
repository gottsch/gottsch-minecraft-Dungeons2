/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

/**
 * @author Mark Gottschling on Sep 10, 2016
 *
 */
public class ExteriorRoom extends Room {

	private boolean insetWall;
	
	public ExteriorRoom() {
		
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean hasInsetWall() {
		return this.insetWall;
	}
	
	/**
	 * 
	 * @param hasInsetWall
	 */
	public void setHasInsetWall(boolean hasInsetWall) {
		this.insetWall = hasInsetWall;
	}
}
