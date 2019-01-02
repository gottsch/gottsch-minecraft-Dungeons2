/**
 * 
 */
package com.someguyssoftware.dungeonsengine.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Dec 18, 2018
 *
 */
public class DungeonConfig implements IDungeonConfig {
	
	private String name;
	private String version = "1.0.0";
	
	private DungeonSize size = DungeonSize.MEDIUM;
		
	/*
	 * min/max # of levels in a dungeon
	 */
	private Quantity numLevels = new Quantity(4, 7);
	
	/*
	 * min/max y value for the bottom of the dungeon
	 */
	private int bottomLimit = 5;
	
	/*
	 * min/max value for the top of the dungeon
	 */
	private int topLimit = 235;
	
	/*
	 * 
	 */
	private Integer surfaceBuffer = 7;
	
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
	private double boundaryFactor = 1.0;
	
	/*
	 * 
	 */
	private ILevelConfig[] levelConfigs = {new LevelConfig()};
	
	/*
	 * 
	 */
	private ILevelConfig surfaceConfig;
	
	/**
	 * 
	 */
	private boolean minecraftConstraints = true;
	
	/**
	 * 
	 */
	public DungeonConfig() {
		this.biomeWhiteList = new ArrayList<>();
		this.biomeBlackList = new ArrayList<>();
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
	public double getBoundaryFactor() {
		return boundaryFactor;
	}


	/**
	 * @param fieldFactor the fieldFactor to set
	 */
	@Override
	public void setBoundaryFactor(double factor) {
		this.boundaryFactor = factor;
	}

	/**
	 * @return the surfaceConfig
	 */
	@Override
	public ILevelConfig getSurfaceConfig() {
		return surfaceConfig;
	}

	/**
	 * @param surfaceConfig the surfaceConfig to set
	 */
	@Override
	public void setSurfaceConfig(ILevelConfig surfaceConfig) {
		this.surfaceConfig = surfaceConfig;
	}


	/**
	 * @return the version
	 */
	@Override
	public String getVersion() {
		return version;
	}


	/**
	 * @param version the version to set
	 */
	@Override
	public void setVersion(String version) {
		this.version = version;
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "DungeonConfig [name=" + name + ", version=" + version + ", size=" + size + ", numLevels=" + numLevels + ", bottomLimit=" + bottomLimit + ", topLimit=" + topLimit + ", surfaceBuffer="
				+ surfaceBuffer + ", biomeWhiteList=" + biomeWhiteList + ", biomeBlackList=" + biomeBlackList + ", boundaryFactor=" + boundaryFactor + ", levelConfigs=" + Arrays.toString(levelConfigs)
				+ ", surfaceConfig=" + surfaceConfig + "]";
	}


	/**
	 * @return the minecraftConstraints
	 */
	@Override
	public boolean isMinecraftConstraints() {
		return minecraftConstraints;
	}

	/**
	 * @param minecraftConstraints the minecraftConstraints to set
	 */
	@Override
	public void setMinecraftConstraints(boolean minecraftConstraints) {
		this.minecraftConstraints = minecraftConstraints;
	}
}
