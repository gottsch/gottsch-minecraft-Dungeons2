/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import com.someguyssoftware.gottschcore.positional.BBox;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Dec 22, 2018
 *
 */
public class Boundary {
	private BBox bbox;
	
	/**
	 * @param c1
	 * @param c2
	 */
	public Boundary(ICoords c1, ICoords c2) {
		this.bbox = new BBox(c1, c2);
	}

	/**
	 * 
	 * @param bbox
	 */
	public Boundary(BBox bbox) {
		this.bbox = bbox;
	}

	/**
	 * 
	 * @param bbox
	 * @param factor
	 */
	public Boundary(BBox bbox, double factor) {
		// TODO do the calculations
	}
	
	/**
	 * @return the bbox
	 */
	protected BBox getBbox() {
		return bbox;
	}

	/**
	 * @param bbox the bbox to set
	 */
	protected void setBbox(BBox bbox) {
		this.bbox = bbox;
	}

}
