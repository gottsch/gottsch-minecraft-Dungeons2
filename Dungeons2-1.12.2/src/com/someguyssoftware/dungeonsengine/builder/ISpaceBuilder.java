package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;

import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.dungeonsengine.model.ISpace;
import com.someguyssoftware.dungeonsengine.model.Space;
import com.someguyssoftware.gottschcore.positional.BBox;
import com.someguyssoftware.gottschcore.positional.ICoords;

public interface ISpaceBuilder {
	public static final ISpace EMPTY_SPACE = new Space();
	
	/*
	 * build a generic room
	 */
	ISpace buildSpace(ICoords startPoint, ISpace roomIn);
	
	/*
	 * build a 'planned' room, using existing rooms to determine where and if it can be placed within the boundary
	 */
	ISpace buildPlannedSpace(ICoords startPoint, List<ISpace> predefinedSpaces);
	
	ISpace buildStartSpace(ICoords startPoint);
	
	ISpace buildEndSpace(ICoords startPoint, List<ISpace> predefinedSpaces);
	
	Boundary getBoundary();
	void setBoundary(Boundary boundary);
	
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