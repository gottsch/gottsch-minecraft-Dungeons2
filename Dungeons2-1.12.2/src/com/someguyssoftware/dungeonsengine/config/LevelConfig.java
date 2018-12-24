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
	
	private Quantity numberOfWebs;
	private Quantity webFrequency;
	
	private Quantity numberOfVines;
	private Quantity vineFrequency;
	
	/*
	 * 
	 */
	private boolean support;
	
	/*
	 * 
	 */
	private boolean decorations;
	
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
	@Override
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
	@Override
	public int getDecayMultiplier() {
		return decayMultiplier;
	}

	/**
	 * @param decayMultiplier the decayMultiplier to set
	 */
	@Override
	public void setDecayMultiplier(int decayMultiplier) {
		this.decayMultiplier = decayMultiplier;
	}

	/**
	 * @return the spawnerFrequency
	 */
	@Override
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
	@Override
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
	@Override
	public List<String> getChestCategories() {
		return chestCategories;
	}

	/**
	 * @param chestCategories the chestCategories to set
	 */
	public void setChestCategories(List<String> chestCategories) {
		this.chestCategories = chestCategories;
	}

	/**
	 * @return the numberOfWebs
	 */
	@Override
	public Quantity getNumberOfWebs() {
		return numberOfWebs;
	}

	/**
	 * @param numberOfWebs the numberOfWebs to set
	 */
	public void setNumberOfWebs(Quantity numberOfWebs) {
		this.numberOfWebs = numberOfWebs;
	}

	/**
	 * @return the webFrequency
	 */
	@Override
	public Quantity getWebFrequency() {
		return webFrequency;
	}

	/**
	 * @param webFrequency the webFrequency to set
	 */
	public void setWebFrequency(Quantity webFrequency) {
		this.webFrequency = webFrequency;
	}

	/**
	 * @return the numberOfVines
	 */
	@Override
	public Quantity getNumberOfVines() {
		return numberOfVines;
	}

	/**
	 * @param numberOfVines the numberOfVines to set
	 */
	public void setNumberOfVines(Quantity numberOfVines) {
		this.numberOfVines = numberOfVines;
	}

	/**
	 * @return the vineFrequency
	 */
	@Override
	public Quantity getVineFrequency() {
		return vineFrequency;
	}

	/**
	 * @param vineFrequency the vineFrequency to set
	 */
	public void setVineFrequency(Quantity vineFrequency) {
		this.vineFrequency = vineFrequency;
	}

	/**
	 * @return the decorations
	 */
	@Override
	public boolean isDecorations() {
		return decorations;
	}

	/**
	 * @param decorations the decorations to set
	 */
	public void setDecorations(boolean decorations) {
		this.decorations = decorations;
	}

}
