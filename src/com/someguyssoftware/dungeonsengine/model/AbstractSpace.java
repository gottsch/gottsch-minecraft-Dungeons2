/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import java.util.List;

import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Jan 9, 2019
 *
 */
public abstract class AbstractSpace implements ISpace {	
	protected ICoords coords;

	protected int depth;
	protected int width;
	protected int height;
	
	/*
	 * 
	 */
	protected List<IExit> exits;

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getCoords()
	 */
	@Override
	public ICoords getCoords() {
		return coords;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setCoords(com.someguyssoftware.gottschcore.positional.ICoords)
	 */
	@Override
	public void setCoords(ICoords coords) {
		this.coords = coords;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDepth()
	 */
	@Override
	public int getDepth() {
		return depth;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setDepth(int)
	 */
	@Override
	public void setDepth(int depth) {
		this.depth = depth;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getWidth()
	 */
	@Override
	public int getWidth() {
		return width;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setWidth(int)
	 */
	@Override
	public void setWidth(int width) {
		this.width = width;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getHeight()
	 */
	@Override
	public int getHeight() {
		return height;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setHeight(int)
	 */
	@Override
	public void setHeight(int height) {
		this.height = height;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getExits()
	 */
	@Override
	public List<IExit> getExits() {
		return exits;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setExits(java.util.List)
	 */
	@Override
	public void setExits(List<IExit> exits) {
		this.exits = exits;
	}

}
