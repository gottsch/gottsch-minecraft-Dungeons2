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
	 * 
	 * @param factor
	 * @return
	 */
	public Boundary grow(final int factor) {
		int amount = (int) ((this.bbox.getMaxCoords().getX() - this.bbox.getMinCoords().getX()) * (1.0 - factor) / 2);
		BBox bbox = this.bbox.grow(amount, 0, amount);
		return new Boundary(bbox);
	}
	
	/**
	 * 
	 * @param factor
	 * @return
	 */
	public Boundary shrink(final double factor) {
		int amount = (int) ((this.bbox.getMaxCoords().getX() - this.bbox.getMinCoords().getX()) * (1.0 - factor) / 2);
		BBox bbox = this.bbox.grow(-amount, 0, -amount);
		return new Boundary(bbox);
	}
	
	public ICoords getMaxCoords() {
		return this.bbox.getMaxCoords();
	}
	
	public ICoords getMinCoords() {
		return this.bbox.getMinCoords();
	}
	
	/**
	 * @return the bbox
	 */
	public BBox getBbox() {
		return bbox;
	}

	/**
	 * @param bbox the bbox to set
	 */
	public void setBbox(BBox bbox) {
		this.bbox = bbox;
	}

}
