package com.someguyssoftware.dungeonsengine.config;

import java.util.List;

import com.someguyssoftware.gottschcore.Quantity;

public interface ILevelConfig {

	boolean isSupport();
	void setSupport(boolean support);

	double getBoundaryFactor();
	void setBoundaryFactor(double factor);
	
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
	LevelConfig copy();
	int getDecayMultiplier();
	void setDecayMultiplier(int decayMultiplier);
	Quantity getSpawnerFrequency();
	Quantity getChestFrequency();
	List<String> getChestCategories();
	Quantity getNumberOfWebs();
	Quantity getWebFrequency();
	Quantity getNumberOfVines();
	Quantity getVineFrequency();
	boolean isDecorations();

}
