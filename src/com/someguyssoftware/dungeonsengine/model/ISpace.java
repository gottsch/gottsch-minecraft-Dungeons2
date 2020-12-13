package com.someguyssoftware.dungeonsengine.model;

import java.util.List;

import com.someguyssoftware.gottschcore.positional.ICoords;

public interface ISpace {

	/**
	 * @return the coords
	 */
	ICoords getCoords();

	/**
	 * @param coords the coords to set
	 */
	void setCoords(ICoords coords);

	/**
	 * @return the depth
	 */
	int getDepth();

	/**
	 * @param depth the depth to set
	 */
	void setDepth(int depth);

	/**
	 * @return the width
	 */
	int getWidth();

	/**
	 * @param width the width to set
	 */
	void setWidth(int width);

	/**
	 * @return the height
	 */
	int getHeight();

	/**
	 * @param height the height to set
	 */
	void setHeight(int height);

	/**
	 * @return the exits
	 */
	List<IExit> getExits();

	/**
	 * @param exits the exits to set
	 */
	void setExits(List<IExit> exits);

}