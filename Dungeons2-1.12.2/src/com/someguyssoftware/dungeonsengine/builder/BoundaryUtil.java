/**
 * 
 */
package com.someguyssoftware.dungeonsengine.builder;

import com.someguyssoftware.gottschcore.positional.BBox;

/**
 * @author Mark Gottschling on Dec 25, 2018
 *
 */
public class BoundaryUtil {

	/**
	 * 
	 */
	private BoundaryUtil() {	}

	/**
	 * 
	 * @param boundary
	 * @param factor
	 * @return
	 */
	public static BBox resize(BBox boundary, double factor) {
		BBox newBB = null;
		// resize field
		if (factor < 1.0D) {
			int amount = (int) ((boundary.getMaxCoords().getX() - boundary.getMinCoords().getX()) * (1.0 - factor) / 2);
			newBB = boundary.grow(-amount);
		}
		else return boundary;
		return newBB;
	}
}
