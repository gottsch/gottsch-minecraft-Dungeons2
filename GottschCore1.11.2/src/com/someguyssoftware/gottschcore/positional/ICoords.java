package com.someguyssoftware.gottschcore.positional;

import net.minecraft.util.math.BlockPos;

/**
 * This class is a wrapper for Minecraft positional classes and calculations. Ex. BlockPos in 1.8+, or for (x, y, z) in <1.8
 * @author Mark Gottschling on May 5, 2017
 *
 */
public interface ICoords {

	/**
	 * Convert to Minecraft BlockPos object
	 * @return
	 */
	public BlockPos toPos();

	/**
	 * Convenience method
	 * @param axis
	 * @return
	 */
	public int get(int axis);

	/**
	 * Convenience method
	 * @param axis
	 * @return
	 */
	public int get(char axis);

	public int getX();

	public int getY();

	public int getZ();

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode();

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj);

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString();

	public ICoords add(int x, int y, int z);

	public ICoords add(ICoords coords);

	public ICoords delta(ICoords coords);

	public ICoords rotate90(int width);

	public ICoords rotate180(int depth, int width);

	public ICoords rotate270(int depth);

	public String toShortString();

	/**
	 * @param toX
	 * @param toY
	 * @param toZ
	 * @return
	 */
	double getDistanceSq(double toX, double toY, double toZ);

	/**
	 * @param coords
	 * @return
	 */
	double getDistanceSq(ICoords coords);

	/**
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	double getDistance(double x, double y, double z);

	/**
	 * @param coords
	 * @return
	 */
	double getDistance(ICoords coords);

	/**
	 * @param targetX
	 * @param targetZ
	 * @return
	 */
	double getXZAngle(double targetX, double targetZ);

	/**
	 * @param coords
	 * @return
	 */
	double getXZAngle(ICoords coords);

	/**
	 * @param n
	 * @return
	 */
	ICoords up(int n);
	ICoords down(int n);
	ICoords north(int n);
	ICoords south(int n);
	ICoords east(int n);
	ICoords west(int n);

}