package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.gottschcore.Quantity;

/**
 * 
 * @author Mark Gottschling on Sep 7, 2017
 *
 */
public class FloorConfig {

	private  Quantity numberOfRooms;
	
	/*
	 * min/max dimensions of a room
	 */
	private Quantity width;
	private Quantity depth;
	private Quantity height;
	
	/*
	 * min/max distance from start point.
	 * used for controlling the pattern of the randomness
	 */
	private Quantity xDistance;
	private Quantity zDistance;
	
	/*
	 * 
	 */
	private boolean minecraftConstraintsOn = true;
	
	/**
	 * 
	 */
	public FloorConfig() {}

	/**
	 * @return the numberOfRooms
	 */
	public Quantity getNumberOfRooms() {
		return numberOfRooms;
	}

	/**
	 * @param numberOfRooms the numberOfRooms to set
	 */
	public void setNumberOfRooms(Quantity numberOfRooms) {
		this.numberOfRooms = numberOfRooms;
	}

	/**
	 * @return the xDistance
	 */
	public Quantity getXDistance() {
		return xDistance;
	}

	/**
	 * @param xDistance the xDistance to set
	 */
	public void setXDistance(Quantity xDistance) {
		this.xDistance = xDistance;
	}

	/**
	 * @return the zDistance
	 */
	public Quantity getZDistance() {
		return zDistance;
	}

	/**
	 * @param zDistance the zDistance to set
	 */
	public void setZDistance(Quantity zDistance) {
		this.zDistance = zDistance;
	}

	/**
	 * @return the width
	 */
	public Quantity getWidth() {
		return width;
	}

	/**
	 * @param width the width to set
	 */
	public void setWidth(Quantity width) {
		this.width = width;
	}

	/**
	 * @return the depth
	 */
	public Quantity getDepth() {
		return depth;
	}

	/**
	 * @param depth the depth to set
	 */
	public void setDepth(Quantity depth) {
		this.depth = depth;
	}

	/**
	 * @return the height
	 */
	public Quantity getHeight() {
		return height;
	}

	/**
	 * @param height the height to set
	 */
	public void setHeight(Quantity height) {
		this.height = height;
	}

	/**
	 * @return the minecraftConstraintsOn
	 */
	public boolean isMinecraftConstraintsOn() {
		return minecraftConstraintsOn;
	}

	/**
	 * @param minecraftConstraintsOn the minecraftConstraintsOn to set
	 */
	public void setMinecraftConstraintsOn(boolean minecraftConstraintsOn) {
		this.minecraftConstraintsOn = minecraftConstraintsOn;
	}
}
