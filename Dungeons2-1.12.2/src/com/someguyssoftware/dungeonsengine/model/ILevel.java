package com.someguyssoftware.dungeonsengine.model;

import java.util.List;

import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.gottschcore.positional.BBox;
import com.someguyssoftware.gottschcore.positional.ICoords;

public interface ILevel {

	/**
	 * @return the startSpace
	 */
	ISpace getStartSpace();

	/**
	 * @param startSpace the startSpace to set
	 */
	void setStartSpace(ISpace startSpace);

	/**
	 * @return the endSpace
	 */
	ISpace getEndSpace();

	/**
	 * @param endSpace the endSpace to set
	 */
	void setEndSpace(ISpace space);

	/**
	 * @return the Spaces
	 */
	List<ISpace> getSpaces();

	/**
	 * @param spaces the spaces list to set
	 */
	void setSpaces(List<ISpace> spaces);

	/**
	 * 
	 * @return
	 */
	BBox getBoundingBox();

	/**
	 * @return the id
	 */
	int getId();

	/**
	 * @param id the id to set
	 */
	void setId(int id);

	/**
	 * @return the NAME
	 */
	String getName();

	/**
	 * @param NAME the NAME to set
	 */
	void setName(String name);

	/**
	 * @return the minX
	 */
	int getMinX();

	/**
	 * @param minX the minX to set
	 */
	void setMinX(int minX);

	/**
	 * @return the maxX
	 */
	int getMaxX();

	/**
	 * @param maxX the maxX to set
	 */
	void setMaxX(int maxX);

	/**
	 * @return the minY
	 */
	int getMinY();

	/**
	 * @param minY the minY to set
	 */
	void setMinY(int minY);

	/**
	 * @return the maxY
	 */
	int getMaxY();

	/**
	 * @param maxY the maxY to set
	 */
	void setMaxY(int maxY);

	/**
	 * @return the minZ
	 */
	int getMinZ();

	/**
	 * @param minZ the minZ to set
	 */
	void setMinZ(int minZ);

	/**
	 * @return the maxZ
	 */
	int getMaxZ();

	/**
	 * @param maxZ the maxZ to set
	 */
	void setMaxZ(int maxZ);

	/**
	 * @return the startPoint
	 */
	ICoords getStartPoint();

	/**
	 * @param startPoint the startPoint to set
	 */
	void setStartPoint(ICoords startPoint);

	/**
	 * @return the config
	 */
	ILevelConfig getConfig();

	/**
	 * @param config the config to set
	 */
	void setConfig(ILevelConfig config);

//	/**
//	 * @return the shafts
//	 */
//	List<IShaft> getShafts();
//
//	/**
//	 * @param shafts the shafts to set
//	 */
//	void setShafts(List<IShaft> shafts);
//
//	List<Hallway> getHallways();
//
//	void setHallways(List<Hallway> hallways);

	/**
	 * @return the spawnPoint
	 */
	ICoords getSpawnPoint();

	/**
	 * @param spawnPoint the spawnPoint to set
	 */
	void setSpawnPoint(ICoords spawnPoint);

	/**
	 * Convenience method
	 * @return
	 */
	int getDepth();

	/**
	 * Convenience method
	 * @return
	 */
	int getWidth();

	/**
	 * @return the boundary
	 */
	Boundary getBoundary();

	/**
	 * @param boundary the boundary to set
	 */
	void setBoundary(Boundary boundary);

}