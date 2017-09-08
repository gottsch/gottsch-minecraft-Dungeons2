/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * A base class representing a 3D space in the world.
 * 
 * @author Mark Gottschling on Sep 4, 2017
 *
 */
public class Space implements ISpace {

	private int ID;
	private String name;
	private ICoords coords;
	private Dimensions dimensions;
	
	/**
	 * 
	 */
	public Space() {
		setDimensions(new Dimensions());
	}
	
	/**
	 * 
	 * @param ID
	 * @param name
	 * @param coords
	 * @param dim
	 */
	public Space(int ID, String name, ICoords coords, Dimensions dim) {
		setID(ID);
		setName(name);
		setCoords(coords);
		setDimensions(dim);
	}
	
	/**
	 * 
	 * @return
	 */
	@Override
	public AxisAlignedBB getBoundingBox() {
		BlockPos bp1 = getCoords().toPos();
		BlockPos bp2 = getCoords().add(getDimensions().getWidth(), getDimensions().getHeight(), getDimensions().getDepth()).toPos();
		AxisAlignedBB bb = new AxisAlignedBB(bp1, bp2);
		return bb;
	}
	
	/**
	 * Creates a bounding box by the XZ dimensions with a height (Y) of 1
	 * @return
	 */
	@Override
	public AxisAlignedBB getXZBoundingBox() {
		BlockPos bp1 = new BlockPos(getCoords().getX(), 0, getCoords().getZ());
		BlockPos bp2 = getCoords().add(getDimensions().getWidth(), 1, getDimensions().getDepth()).toPos();
		AxisAlignedBB bb = new AxisAlignedBB(bp1, bp2);
		return bb;
	}
	
	/**
	 * 
	 * @return
	 */
	@Override
	public ICoords getCenter() {
		int x = this.getCoords().getX()  + ((this.getDimensions().getWidth()-1) / 2) ;
		int y = this.getCoords().getY()  + ((this.getDimensions().getHeight()-1) / 2);
		int z = this.getCoords().getZ()  + ((this.getDimensions().getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;
	}
	
	/**
	 * 
	 * @return
	 */
	@Override
	public ICoords getXZCenter() {
		int x = this.getCoords().getX()  + ((this.getDimensions().getWidth()-1) / 2);
		int y = this.getCoords().getY();
		int z = this.getCoords().getZ()  + ((this.getDimensions().getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;
	}
	
	/**
	 * 
	 * @return
	 */
	@Override
	public ICoords getTopCenter() {
		int x = this.getCoords().getX()  + ((this.getDimensions().getWidth()-1) / 2);
		int y = this.getCoords().getY() + this.getDimensions().getHeight();
		int z = this.getCoords().getZ()  + ((this.getDimensions().getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;	
	}
	
	@Override
	public int getMinX() {
		return this.getCoords().getX();
	}
	
	@Override
	public int getMaxX() {
		return this.getCoords().getX() + this.getDimensions().getWidth() - 1;
	}
	
	@Override
	public int getMinY() {
		return this.getCoords().getY();
	}
	
	@Override
	public int getMaxY() {
		return this.getCoords().getY() + this.getDimensions().getHeight() - 1;
	}
	
	/**
	 * 
	 * @return
	 */
	@Override
	public int getMinZ() {
		return this.getCoords().getZ();
	}
	
	/**
	 * 
	 * @return
	 */
	@Override
	public int getMaxZ() {
		return this.getCoords().getZ() + this.getDimensions().getDepth() - 1;
	}
	
	@Override
	public int getID() {
		return ID;
	}

	@Override
	public void setID(int ID) {
		this.ID = ID;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public ICoords getCoords() {
		return coords;
	}

	@Override
	public void setCoords(ICoords coords) {
		this.coords = coords;
	}

	@Override
	public Dimensions getDimensions() {
		return dimensions;
	}

	@Override
	public void setDimensions(Dimensions dim) {
		this.dimensions = dim;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Space [ID=" + ID + ", name=" + name + ", coords=" + coords + ", dimensions=" + dimensions + "]";
	}

}
