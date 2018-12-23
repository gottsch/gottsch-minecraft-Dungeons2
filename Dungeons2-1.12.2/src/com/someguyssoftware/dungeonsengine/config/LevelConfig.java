/**
 * 
 */
package com.someguyssoftware.dungeonsengine.config;

import java.util.List;

import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Dec 18, 2018
 *
 */
public class LevelConfig implements ILevelConfig {
	/*
	 * Adjusts the size of the room field.
	 */
	private double fieldFactor;
	
	/*
	 * min/max # of rooms in a level
	 */
	private Quantity numRooms;
	
	/*
	 * min/max dimensions of a room
	 */
	private Quantity width;
	private Quantity depth;
	private Quantity height;
	
	/*
	 * min/max number of edges or hallways that each room can have
	 */
	private Quantity degrees;
	
	/**
	 * Number of times to perform decay eval on block
	 */
	private int decayMultiplier;
	
	/*
	 * 
	 */
	private Quantity spawnerFrequency;
	
	/*
	 * Chest properties
	 */
	private Quantity chestFrequency;
	
	private List<String> chestCategories;
	
	/*
	 * 
	 */
	private boolean support;
	
	
	/**
	 * 
	 */
	public LevelConfig() {	}

	/**
	 * 
	 * @param c
	 */
	public LevelConfig(LevelConfig c) {
		this.setChestCategories(c.getChestCategories());
		this.setChestFrequency(c.getChestFrequency());
		this.setDecayMultiplier(c.getDecayMultiplier());
		this.setDegrees(c.getDegrees());
		this.setDepth(c.getDepth());
		this.setFieldFactor(c.getFieldFactor());
		this.setHeight(c.getHeight());
		this.setNumRooms(c.getNumRooms());
		this.setSpawnerFrequency(c.getSpawnerFrequency());
		this.setSupport(c.isSupport());
		this.setWidth(c.getWidth());
	}
	
	/**
	 * 
	 * @return
	 */
	public LevelConfig copy() {
		return new LevelConfig(this);
	}
	
	/**
	 * @return the support
	 */
	@Override
	public boolean isSupport() {
		return support;
	}

	/**
	 * @param support the support to set
	 */
	@Override
	public void setSupport(boolean support) {
		this.support = support;
	}

	/**
	 * @return the fieldFactor
	 */
	@Override
	public double getFieldFactor() {
		return fieldFactor;
	}

	/**
	 * @param fieldFactor the fieldFactor to set
	 */
	@Override
	public void setFieldFactor(double fieldFactor) {
		this.fieldFactor = fieldFactor;
	}

	/**
	 * @return the numRooms
	 */
	@Override
	public Quantity getNumRooms() {
		return numRooms;
	}

	/**
	 * @param numRooms the numRooms to set
	 */
	@Override
	public void setNumRooms(Quantity numRooms) {
		this.numRooms = numRooms;
	}

	/**
	 * @return the width
	 */
	@Override
	public Quantity getWidth() {
		return width;
	}

	/**
	 * @param width the width to set
	 */
	@Override
	public void setWidth(Quantity width) {
		this.width = width;
	}

	/**
	 * @return the depth
	 */
	@Override
	public Quantity getDepth() {
		return depth;
	}

	/**
	 * @param depth the depth to set
	 */
	@Override
	public void setDepth(Quantity depth) {
		this.depth = depth;
	}

	/**
	 * @return the height
	 */
	@Override
	public Quantity getHeight() {
		return height;
	}

	/**
	 * @param height the height to set
	 */
	@Override
	public void setHeight(Quantity height) {
		this.height = height;
	}

	/**
	 * @return the degrees
	 */
	@Override
	public Quantity getDegrees() {
		return degrees;
	}

	/**
	 * @param degrees the degrees to set
	 */
	@Override
	public void setDegrees(Quantity degrees) {
		this.degrees = degrees;
	}

	/**
	 * @return the decayMultiplier
	 */
	public int getDecayMultiplier() {
		return decayMultiplier;
	}

	/**
	 * @param decayMultiplier the decayMultiplier to set
	 */
	public void setDecayMultiplier(int decayMultiplier) {
		this.decayMultiplier = decayMultiplier;
	}

	/**
	 * @return the spawnerFrequency
	 */
	public Quantity getSpawnerFrequency() {
		return spawnerFrequency;
	}

	/**
	 * @param spawnerFrequency the spawnerFrequency to set
	 */
	public void setSpawnerFrequency(Quantity spawnerFrequency) {
		this.spawnerFrequency = spawnerFrequency;
	}

	/**
	 * @return the chestFrequency
	 */
	public Quantity getChestFrequency() {
		return chestFrequency;
	}

	/**
	 * @param chestFrequency the chestFrequency to set
	 */
	public void setChestFrequency(Quantity chestFrequency) {
		this.chestFrequency = chestFrequency;
	}

	/**
	 * @return the chestCategories
	 */
	public List<String> getChestCategories() {
		return chestCategories;
	}

	/**
	 * @param chestCategories the chestCategories to set
	 */
	public void setChestCategories(List<String> chestCategories) {
		this.chestCategories = chestCategories;
	}

}
