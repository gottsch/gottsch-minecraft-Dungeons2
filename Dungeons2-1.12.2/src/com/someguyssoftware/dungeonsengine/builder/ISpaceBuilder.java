package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;

import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.dungeonsengine.model.ISpace;
import com.someguyssoftware.gottschcore.positional.ICoords;

public interface ISpaceBuilder {
	
	public Boundary getBoundary();
	public void setBoundary(Boundary boundary);
	
	/*
	 * build a generic space
	 */
	ISpace buildSpace(ICoords startPoint, ISpace spaceIn);
	
	/*
	 * build a 'planned' space, using existing spaces to determine where and if it can be placed within the boundary
	 */
	ISpace buildPlannedSpace(ICoords startPoint, List<ISpace> predefinedSpaces);
	
	ISpace buildStartSpace(ICoords startPoint);
	
	ISpace buildEndSpace(ICoords startPoint, List<ISpace> predefinedSpaces);
	
	/**
	 * 
	 * @param random
	 * @param startPoint
	 * @param config
	 * @param predefinedSpaces
	 * @return
	 */
	ISpace buildTreasureSpace(ICoords startPoint, List<ISpace> predefinedSpaces);
	
}