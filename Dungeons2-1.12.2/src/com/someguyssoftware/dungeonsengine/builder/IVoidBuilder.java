package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;

import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.dungeonsengine.model.IVoid;
import com.someguyssoftware.gottschcore.positional.ICoords;

public interface IVoidBuilder {
	
	public Boundary getBoundary();
	public void setBoundary(Boundary boundary);
	
	/*
	 * build a generic space
	 */
	IVoid buildSpace(ICoords startPoint, IVoid spaceIn);
	
	/*
	 * build a 'planned' space, using existing spaces to determine where and if it can be placed within the boundary
	 */
	IVoid buildPlannedSpace(ICoords startPoint, List<IVoid> predefinedSpaces);
	
	IVoid buildStartSpace(ICoords startPoint);
	
	IVoid buildEndSpace(ICoords startPoint, List<IVoid> predefinedSpaces);
	
	/**
	 * 
	 * @param random
	 * @param startPoint
	 * @param config
	 * @param predefinedSpaces
	 * @return
	 */
	IVoid buildTreasureSpace(ICoords startPoint, List<IVoid> predefinedSpaces);
	
}