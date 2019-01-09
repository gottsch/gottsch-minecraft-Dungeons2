/**
 * 
 */
package com.someguyssoftware.dungeonsengine.style;

import com.someguyssoftware.dungeonsengine.model.IRoom;

/**
 * @author Mark Gottschling on Jan 9, 2019
 *
 */
public class Chamber {
	private IRoom model;
	
	/**
	 * 
	 * @param model
	 */
	public Chamber(IRoom model) {
		setModel(model);
	}

	public IRoom getModel() {
		return model;
	}

	public void setModel(IRoom model) {
		this.model = model;
	}
	
}
