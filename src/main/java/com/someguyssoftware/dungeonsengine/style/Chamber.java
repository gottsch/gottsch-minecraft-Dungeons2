/**
 * 
 */
package com.someguyssoftware.dungeonsengine.style;

import com.someguyssoftware.dungeonsengine.model.IRoom;
import com.someguyssoftware.dungeonsengine.model.ISpace;

/**
 * @author Mark Gottschling on Jan 9, 2019
 *
 */
public class Chamber {
	private ISpace space;
	
	/**
	 * 
	 * @param space
	 */
	public Chamber(ISpace space) {
		setSpace(space);
	}

	public ISpace getSpace() {
		return space;
	}

	private void setSpace(ISpace space) {
		this.space = space;
	}
	
	// TODO needed?
	public IRoom getRoom() {
		return (IRoom) getSpace();
	}
	
}
