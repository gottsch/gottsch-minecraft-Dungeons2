/**
 * 
 */
package com.someguyssoftware.gottschcore.positional;

import net.minecraft.util.math.AxisAlignedBB;

/**
 * @author Mark Gottschling on Jul 18, 2016
 *
 */
public class Intersect {
	private double x;
	private double y;
	private double z;
	
	/**
	 * 
	 */
	public Intersect() {
		
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 */
	public Intersect(double x, double y, double z) {
		setX(x);
		setY(y);
		setZ(z);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Intersect [\n\tx=" + x + ",\n\ty=" + y + ",\n\tz=" + z + "\n]";
	}

	/**
	 * 
	 * @return
	 */
	public ICoords asCoords() {
		return new Coords((int)getX(), (int) getY(), (int) getZ());
	}
	
	public static ICoords asCoords(Intersect intersect) {
		return new Coords((int)intersect.getX(), (int) intersect.getY(), (int) intersect.getZ());		
	}
	
	/**
	 * 
	 * @param bb1
	 * @param bb2
	 * @return
	 */
	public static Intersect getIntersect(AxisAlignedBB bb1, AxisAlignedBB bb2) {
		double x = Math.max(0.0, Math.abs(Math.min(bb1.maxX, bb2.maxX) - Math.max(bb1.minX, bb2.minX)));
		double y = Math.max(0.0, Math.abs(Math.min(bb1.maxY, bb2.maxY) - Math.max(bb1.minY, bb2.minY)));
		double z = Math.max(0.0, Math.abs(Math.min(bb1.maxZ, bb2.maxZ) - Math.max(bb1.minZ, bb2.minZ)));		
		return new Intersect(x, y, z);
	}
	
	public static Intersect getIntersect2(AxisAlignedBB bb1, AxisAlignedBB bb2) {
		double x = Math.max(0.0, Math.min(bb1.maxX, bb2.maxX) - Math.max(bb1.minX, bb2.minX));
		double y = Math.max(0.0, Math.min(bb1.maxY, bb2.maxY) - Math.max(bb1.minY, bb2.minY));
		double z = Math.max(0.0, Math.min(bb1.maxZ, bb2.maxZ) - Math.max(bb1.minZ, bb2.minZ));		
		return new Intersect(x, y, z);
	}
	/**
	 * @return the x
	 */
	public double getX() {
		return x;
	}

	/**
	 * @param x the x to set
	 */
	public void setX(double x) {
		this.x = x;
	}

	/**
	 * @return the y
	 */
	public double getY() {
		return y;
	}

	/**
	 * @param y the y to set
	 */
	public void setY(double y) {
		this.y = y;
	}

	/**
	 * @return the z
	 */
	public double getZ() {
		return z;
	}

	/**
	 * @param z the z to set
	 */
	public void setZ(double z) {
		this.z = z;
	}	
}
