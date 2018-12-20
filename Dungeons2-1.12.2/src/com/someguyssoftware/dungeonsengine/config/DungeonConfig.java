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
public class DungeonConfig implements IDungeonConfig {
	
	private String name;
	private String version;
	
	private DungeonSize size;
		
	/*
	 * min/max # of levels in a dungeon
	 */
	private Quantity numLevels;
	
	/*
	 * min/max y value for the bottom of the dungeon
	 */
	private int bottomLimit;
	
	/*
	 * min/max value for the top of the dungeon
	 */
	private int topLimit;
	
	/*
	 * 
	 */
	private Integer surfaceBuffer;
	
	/*
	 * 
	 */
	private List<String> biomeWhiteList;
	/*
	 * 
	 */
	private List<String> biomeBlackList;
	
	/*
	 * 
	 */
	private double fieldFactor;
	
	/*
	 * 
	 */
	private ILevelConfig[] levelConfigs;
	
	/*
	 * 
	 */
	private ILevelConfig surfaceConfig;
	
	/**
	 * 
	 */
	public DungeonConfig() {
	}

	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IDungeonConfig#getNumLevels()
	 */
	@Override
	public Quantity getNumLevels() {
		return numLevels;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IDungeonConfig#setNumLevels(int)
	 */
	@Override
	public void setNumLevels(Quantity num) {
		this.numLevels = num;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IDungeonConfig#getTopLimit()
	 */
	@Override
	public int getTopLimit() {
		return topLimit;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IDungeonConfig#setTopLimit(int)
	 */
	@Override
	public void setTopLimit(int limit) {
		this.topLimit = limit;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IDungeonConfig#getBottomLimit()
	 */
	@Override
	public int getBottomLimit() {
		return bottomLimit;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IDungeonConfig#setBottomLimit(int)
	 */
	@Override
	public void setBottomLimit(int limit) {
		this.bottomLimit = limit;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IDungeonConfig#getLevelConfigs()
	 */
	@Override
	public ILevelConfig[] getLevelConfigs() {
		return levelConfigs;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IDungeonConfig#setLevelConfigs(com.someguyssoftware.dungeonsengine.config.ILevelConfig)
	 */
	@Override
	public void setLevelConfigs(ILevelConfig[] configs) {
		this.levelConfigs = configs;
	}

	/**
	 * @return the surfaceBuffer
	 */
	@Override
	public Integer getSurfaceBuffer() {
		return surfaceBuffer;
	}


	/**
	 * @param surfaceBuffer the surfaceBuffer to set
	 */
	@Override
	public void setSurfaceBuffer(Integer surfaceBuffer) {
		this.surfaceBuffer = surfaceBuffer;
	}


	/**
	 * @return the name
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	@Override
	public void setName(String name) {
		this.name = name;
	}


	/**
	 * @return the size
	 */
	@Override
	public DungeonSize getSize() {
		return size;
	}

	/**
	 * @param size the size to set
	 */
	@Override
	public void setSize(DungeonSize size) {
		this.size = size;
	}

	/**
	 * @return the biomeWhiteList
	 */
	@Override
	public List<String> getBiomeWhiteList() {
		return biomeWhiteList;
	}

	/**
	 * @param biomeWhiteList the biomeWhiteList to set
	 */
	@Override
	public void setBiomeWhiteList(List<String> biomeWhiteList) {
		this.biomeWhiteList = biomeWhiteList;
	}

	/**
	 * @return the biomeBlackList
	 */
	@Override
	public List<String> getBiomeBlackList() {
		return biomeBlackList;
	}

	/**
	 * @param biomeBlackList the biomeBlackList to set
	 */
	@Override
	public void setBiomeBlackList(List<String> biomeBlackList) {
		this.biomeBlackList = biomeBlackList;
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
	 * @return the surfaceConfig
	 */
	public ILevelConfig getSurfaceConfig() {
		return surfaceConfig;
	}

	/**
	 * @param surfaceConfig the surfaceConfig to set
	 */
	public void setSurfaceConfig(ILevelConfig surfaceConfig) {
		this.surfaceConfig = surfaceConfig;
	}


	/**
	 * @return the version
	 */
	protected String getVersion() {
		return version;
	}


	/**
	 * @param version the version to set
	 */
	protected void setVersion(String version) {
		this.version = version;
	}
}
