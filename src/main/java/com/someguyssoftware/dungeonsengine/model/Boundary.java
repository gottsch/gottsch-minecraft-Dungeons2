/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.positional.BBox;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.util.math.AxisAlignedBB;

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
	 * @param minSize
	 * @return
	 */
	public Boundary resize(final double factor, final int minSize) {
		// resize boundary
		if (factor < 1.0D) {
			double deltaX = (getMaxCoords().getX() - getMinCoords().getX());
			double deltaZ = (getMaxCoords().getZ() - getMinCoords().getZ());	
			Dungeons2.log.debug("deltaX -> {}", deltaX);
			Dungeons2.log.debug("deltaZ -> {}", deltaZ);
			int xAmount = (int) ((deltaX * (1.0 - factor)) / 2);
			int zAmount = (int) ((deltaZ * (1.0 - factor)) / 2);
			Dungeons2.log.debug("initial shrink amounts -> {} {}", xAmount, zAmount);
			
			// determine and recalculate if the shrunk amount is less than the minimum size
			if (Math.abs(deltaX - (xAmount*2)) < minSize ) {
				// calculate what amount will give 50
				double p = 1.0D - 50/Math.abs(deltaX);
				xAmount = (int) ((deltaX * p) / 2);
				Dungeons2.log.debug("x less than min, new amount -> {} [{}%]", xAmount, p);
			}
			if (Math.abs(deltaZ - (zAmount*2)) < minSize) {
				// calculate what amount wil give 50
				double p = 1.0D - 50/Math.abs(deltaZ);
				zAmount = (int) ((deltaZ * p) / 2);
				Dungeons2.log.debug("z less than min, new amount -> {} [{}%]", zAmount, p);
			}
			
			Boundary newBoundary = grow(-xAmount, 0, -zAmount);
			Dungeons2.log.debug("boundary shrunk by -> {} {}, to new size -> {}", xAmount, zAmount, newBoundary);
			return newBoundary;
		}
		else return this;	
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
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public Boundary grow(int x, int y, int z) {
		return new Boundary(bbox.grow(x, y, z));
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public Boundary expand(int x, int y, int z) {
		return new Boundary(bbox.expand(x, y, z));
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
