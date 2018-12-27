package com.someguyssoftware.dungeonsengine.config;

import java.util.List;

import com.someguyssoftware.gottschcore.Quantity;

public interface ILevelConfig {

	Boolean isSupport();
	void setSupport(Boolean support);

	Double getBoundaryFactor();
	void setBoundaryFactor(Double factor);
	
	Double getSpawnBoundaryFactor();
	void setSpawnBoundaryFactor(Double spawnBoundaryFactor);
	
	Quantity getHeight();
	void setHeight(Quantity height);
	Quantity getWidth();
	void setWidth(Quantity width);
	Quantity getDepth();
	void setDepth(Quantity depth);
	Quantity getDegrees();
	void setDegrees(Quantity degrees);
	Quantity getNumRooms();
	void setNumRooms(Quantity numRooms);
	ILevelConfig copy();
	Integer getDecayMultiplier();
	void setDecayMultiplier(Integer decayMultiplier);
	Quantity getSpawnerFrequency();
	Quantity getChestFrequency();
	List<String> getChestCategories();
	Quantity getNumberOfWebs();
	Quantity getWebFrequency();
	Quantity getNumberOfVines();
	Quantity getVineFrequency();
	
	Boolean isDecorations();
	void setDecorations(Boolean decorations);
	
	String getTheme();
	void setTheme(String theme);
	
	ILevelConfig apply(ILevelConfig config);
	
}
