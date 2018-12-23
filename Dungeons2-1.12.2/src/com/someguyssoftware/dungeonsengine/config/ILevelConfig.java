package com.someguyssoftware.dungeonsengine.config;

import com.someguyssoftware.gottschcore.Quantity;

public interface ILevelConfig {

	boolean isSupport();
	void setSupport(boolean support);

	double getFieldFactor();
	void setFieldFactor(double fieldFactor);
	
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

}
