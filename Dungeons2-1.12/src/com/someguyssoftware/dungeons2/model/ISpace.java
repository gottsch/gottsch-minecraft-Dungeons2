/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.util.math.AxisAlignedBB;

/**
 * @author Mark Gottschling on Sep 4, 2017
 *
 */
public interface ISpace {

	public int getID();
	public void setID(int ID);
	
	public String getName();
	public void setName(String name);
	
	public ICoords getCoords();
	public void setCoords(ICoords coords);
	
	public Dimensions getDimensions();
	public void setDimensions(Dimensions dim);
	
	public AxisAlignedBB getBoundingBox();
	public AxisAlignedBB getXZBoundingBox();
	public ICoords getCenter();
	public ICoords getXZCenter();
	public ICoords getTopCenter();
	
	public int getMinX();
	public int getMaxX();
	public int getMinY();
	public int getMaxY();
	public int getMinZ();
	public int getMaxZ();
	
}
