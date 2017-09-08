/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

/**
 * @author Mark Gottschling on Sep 4, 2017
 *
 */
public class Dimensions {
	private int depth;
	private int width;
	private int height;
	
	/**
	 * 
	 */
	public Dimensions() {}
	
	/**
	 * 
	 * @param depth
	 * @param width
	 * @param height
	 */
	public Dimensions(int width, int height, int depth) {
		setDepth(depth);
		setWidth(width);
		setHeight(height);
	}

	/**
	 * @return the depth
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * @param depth the depth to set
	 */
	public void setDepth(int depth) {
		this.depth = depth;
	}

	/**
	 * @return the width
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * @param width the width to set
	 */
	public void setWidth(int width) {
		this.width = width;
	}

	/**
	 * @return the height
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * @param height the height to set
	 */
	public void setHeight(int height) {
		this.height = height;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Dimensions [depth=" + depth + ", width=" + width + ", height=" + height + "]";
	}
}
