/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Jul 27, 2016
 *
 */
public class DungeonConfig {
	
	/*
	 * min/max # of levels in a dungeon
	 */
	private Quantity numberOfLevels;
	
	/*
	 * min/max y value for the bottom of the dungeon
	 */
	private int yBottom;
	/*
	 * min/max value for the top of the dungeon - TODO change back to Quantity instead of fixed?
	 */
	private int yTop;
	
	/*
	 * 
	 */
	private Integer surfaceBuffer;
	
	/*
	 * TODO this is probably deprecated - it's set in the level config
	 */
	private boolean support;
	
	/**
	 * 
	 */
	public DungeonConfig() {
		// set default values
		numberOfLevels = new Quantity(1, 6);
		surfaceBuffer = 10;
		yBottom = 3;
		yTop = 230;
		support = true;
	}

	/**
	 * @return the numberOfLevels
	 */
	public Quantity getNumberOfLevels() {
		return numberOfLevels;
	}

	/**
	 * @param numberOfLevels the numberOfLevels to set
	 */
	public void setNumberOfLevels(Quantity numberOfLevels) {
		this.numberOfLevels = numberOfLevels;
	}

	/**
	 * @return the yBottom
	 */
	public int getYBottom() {
		return yBottom;
	}

	/**
	 * @param yBottom the yBottom to set
	 */
	public void setYBottom(int yBottom) {
		this.yBottom = yBottom;
	}

	/**
	 * @return the yTop
	 */
	public int getYTop() {
		return yTop;
	}

	/**
	 * @param yTop the yTop to set
	 */
	public void setYTop(int yTop) {
		this.yTop = yTop;
	}

	/**
	 * @return the surfaceBuffer
	 */
	public Integer getSurfaceBuffer() {
		return surfaceBuffer;
	}

	/**
	 * @param surfaceBuffer the surfaceBuffer to set
	 */
	public void setSurfaceBuffer(Integer surfaceBuffer) {
		this.surfaceBuffer = surfaceBuffer;
	}

	/**
	 * @return the support
	 */
	public boolean useSupport() {
		return support;
	}

	/**
	 * @param support the support to set
	 */
	public void setUseSupport(boolean support) {
		this.support = support;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "DungeonConfig [numberOfLevels=" + numberOfLevels + ", yBottom=" + yBottom + ", yTop=" + yTop
				+ ", surfaceBuffer=" + surfaceBuffer + ", support=" + support + "]";
	}

}
