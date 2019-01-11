package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;

import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.dungeonsengine.model.IRoom;
import com.someguyssoftware.gottschcore.positional.ICoords;

public interface IVoidBuilder {
	
	public Boundary getBoundary();
	public void setBoundary(Boundary boundary);
	
	/*
	 * build a generic space
	 */
	IRoom buildSpace(ICoords startPoint, IRoom spaceIn);
	
	/*
	 * build a 'planned' space, using existing spaces to determine where and if it can be placed within the boundary
	 */
	IRoom buildPlannedSpace(ICoords startPoint, List<IRoom> predefinedSpaces);
	
	IRoom buildStartSpace(ICoords startPoint);
	
	IRoom buildEndSpace(ICoords startPoint, List<IRoom> predefinedSpaces);
	
	/**
	 * 
	 * @param random
	 * @param startPoint
	 * @param config
	 * @param predefinedSpaces
	 * @return
	 */
	IRoom buildTreasureSpace(ICoords startPoint, List<IRoom> predefinedSpaces);
	
}