/**
 * 
 */
package com.someguyssoftware.dungeonsengine.config;

import java.util.List;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Dec 18, 2018
 *
 */
public class LevelConfig implements ILevelConfig {
	/*
	 * Adjusts the size of the level boundary.
	 */
	private Double boundaryFactor;// = 1.0D;
	
	private Double spawnBoundaryFactor;// = 0.5D;
	
	/*
	 * min/max # of rooms in a level
	 */
	private Quantity numRooms;// = new Quantity(15, 30);
	
	/*
	 * min/max dimensions of a room
	 */
	private Quantity width;// = new Quantity(5, 15);
	private Quantity depth;// = new Quantity(5, 15);
	private Quantity height;// = new Quantity(5, 15);
	
	/*
	 * min/max number of edges or hallways that each room can have
	 */
	private Quantity degrees;// = new Quantity(2, 4);
	
	/**
	 * Number of times to perform decay eval on block
	 */
	private Integer decayMultiplier;// = 5;
	
	/*
	 * 
	 */
	private Quantity spawnerFrequency;// = new Quantity(5,10);
	
	// TODO should be it's own class with properties
	// private ChestConfig
	/*
	 * Chest properties
	 */
	private IChestConfig chestConfig;
	private Quantity chestFrequency;// = new Quantity(5, 10);
	
	private List<String> chestCategories;// = Arrays.asList("COMMON", "UNCOMMON", "RARE");
	
	
	// TODO rework these to be more generic - some sort of matrix
	private Quantity numberOfWebs;// = new Quantity(10, 10);
	private Quantity webFrequency;// = new Quantity(10, 20);
	
	private Quantity numberOfVines;// = new Quantity(10, 10);
	private Quantity vineFrequency;// = new Quantity(10, 20);
	
	private String theme;
	
	/*
	 * 
	 */
	private Boolean support;// = true;
	
	/*
	 * 
	 */
	private Boolean decorations;// = true;
	
	/**
	 * 
	 */
	public LevelConfig() {	}

	/**
	 * 
	 * @param c
	 */
	public LevelConfig(LevelConfig c) {
		// TODO all Quantity need to be new copies
		this.setChestConfig(new ChestConfig(c.getChestConfig()));
		this.setChestCategories(c.getChestCategories());
		this.setChestFrequency(c.getChestFrequency());
		this.setDecayMultiplier(c.getDecayMultiplier());
		this.setDegrees(c.getDegrees());
		this.setDepth(c.getDepth());
		this.setBoundaryFactor(c.getBoundaryFactor());
		this.setHeight(c.getHeight());
		this.setNumRooms(c.getNumRooms());
		this.setSpawnerFrequency(c.getSpawnerFrequency());
		this.setSupport(c.isSupport());
		this.setWidth(c.getWidth());
		
		this.setNumberOfVines(c.getNumberOfVines());
		this.setVineFrequency(c.getVineFrequency());
		this.setNumberOfWebs(c.getNumberOfWebs());
		this.setWebFrequency(c.getWebFrequency());

		this.setTheme(c.getTheme());
		this.setSupport(c.isSupport());
		this.setDecorations(c.isDecorations());
	}
	
	/**
	 * 
	 * @return
	 */
	@Override
	public ILevelConfig copy() {
		return new LevelConfig(this);
	}
	
	@Override
	public ILevelConfig apply(ILevelConfig config) {
		if (config == null) {
			Dungeons2.log.debug("apply configIn is null.");
			return this;
		}
		
		if (getBoundaryFactor() == null) {
			setBoundaryFactor(config.getBoundaryFactor());
		}
		
		if (getChestConfig() == null) setChestConfig(new ChestConfig(config.getChestConfig()));
		else getChestConfig().apply(config.getChestConfig());
		
		if (getChestCategories() == null || getChestCategories().equals("")) {
			setChestCategories(config.getChestCategories());
		}
		
		// TODO all Quantity need to be new copies
		if (getChestFrequency() == null) setChestFrequency(config.getChestFrequency());
		if (getDecayMultiplier() == null) setDecayMultiplier(config.getDecayMultiplier());
		if (isDecorations() == null) setDecorations(config.isDecorations());
		if (getDegrees() == null) setDegrees(config.getDegrees());
		if (getDepth() == null) setDepth(config.getDepth());
		if (getHeight() == null) setHeight(config.getHeight());
		if (getNumberOfVines() == null) setNumberOfVines(config.getNumberOfVines());
		if (getNumberOfWebs() == null) setNumberOfWebs(config.getNumberOfWebs());
		if (getNumRooms() == null) setNumRooms(config.getNumRooms());
		if (getSpawnBoundaryFactor() == null) setSpawnBoundaryFactor(config.getSpawnBoundaryFactor());
		if (getSpawnerFrequency() == null) setSpawnerFrequency(config.getSpawnerFrequency());
		if (isSupport() == null) setSupport(config.isSupport());
		if (getTheme() == null || getTheme().equals("")) setTheme(config.getTheme());
		if (getVineFrequency() == null) setVineFrequency(config.getVineFrequency());
		if (getWebFrequency() == null) setWebFrequency(config.getWebFrequency());
		if (getWidth() == null) setWidth(config.getWidth());
		
		return this;
	}
	
	/**
	 * @return the support
	 */
	@Override
	public Boolean isSupport() {
		return support;
	}

	/**
	 * @param support the support to set
	 */
	@Override
	public void setSupport(Boolean support) {
		this.support = support;
	}

	/**
	 * @return the fieldFactor
	 */
	@Override
	public Double getBoundaryFactor() {
		return boundaryFactor;
	}

	/**
	 * @param fieldFactor the fieldFactor to set
	 */
	@Override
	public void setBoundaryFactor(Double fieldFactor) {
		this.boundaryFactor = fieldFactor;
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
	public Integer getDecayMultiplier() {
		return decayMultiplier;
	}

	/**
	 * @param decayMultiplier the decayMultiplier to set
	 */
	@Override
	public void setDecayMultiplier(Integer decayMultiplier) {
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
	public Boolean isDecorations() {
		return decorations;
	}

	/**
	 * @param decorations the decorations to set
	 */
	public void setDecorations(Boolean decorations) {
		this.decorations = decorations;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "LevelConfig [boundaryFactor=" + boundaryFactor + ", spawnBoundaryFactor=" + spawnBoundaryFactor + ", numRooms=" + numRooms + ", width=" + width + ", depth=" + depth + ", height="
				+ height + ", degrees=" + degrees + ", decayMultiplier=" + decayMultiplier + ", spawnerFrequency=" + spawnerFrequency + ", chestConfig=" + chestConfig + ", chestFrequency="
				+ chestFrequency + ", chestCategories=" + chestCategories + ", numberOfWebs=" + numberOfWebs + ", webFrequency=" + webFrequency + ", numberOfVines=" + numberOfVines
				+ ", vineFrequency=" + vineFrequency + ", theme=" + theme + ", support=" + support + ", decorations=" + decorations + "]";
	}

	@Override
	public Double getSpawnBoundaryFactor() {
		return spawnBoundaryFactor;
	}

	@Override
	public void setSpawnBoundaryFactor(Double spawnBoundaryFactor) {
		this.spawnBoundaryFactor = spawnBoundaryFactor;
	}

	/**
	 * @return the theme
	 */
	@Override
	public String getTheme() {
		return theme;
	}

	/**
	 * @param theme the theme to set
	 */
	@Override
	public void setTheme(String theme) {
		this.theme = theme;
	}

	@Override
	public IChestConfig getChestConfig() {
		return chestConfig;
	}

	@Override
	public void setChestConfig(IChestConfig chestConfig) {
		this.chestConfig = chestConfig;
	}

}
