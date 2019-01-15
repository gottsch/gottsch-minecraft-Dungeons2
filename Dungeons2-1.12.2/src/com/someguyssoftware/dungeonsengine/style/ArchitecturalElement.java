/**
 * 
 */
package com.someguyssoftware.dungeonsengine.style;

import com.someguyssoftware.dungeonsengine.enums.Face;

/**
 * @author Mark Gottschling on Aug 19, 2018
 *
 */
public class ArchitecturalElement implements IArchitecturalElement {

	private String name;
	private double horizontalSupport = -1D;
	private double verticalSupport = -1D;
	private Face face;
	private IArchitecturalElement base;
	
	/**
	 * 
	 */
	public ArchitecturalElement() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * 
	 * @param name
	 * @param vSupport
	 * @param hSupport
	 */
	public ArchitecturalElement(String name, double vSupport, double hSupport) {
		this.name = name;
		this.verticalSupport = vSupport;
		this.horizontalSupport = hSupport;
	}
	
	/**
	 * 
	 * @param name
	 * @param vSupport
	 * @param hSupport
	 * @param face
	 * @param base
	 */
	public ArchitecturalElement(String name, double vSupport, double hSupport, Face face, IArchitecturalElement base) {
		this.name = name;
		this.horizontalSupport = hSupport;
		this.verticalSupport = vSupport;
		this.face = face;
		this.base = base;
	}
	
	/**
	 * 
	 * @param name
	 * @param hSupport
	 * @param vSupport
	 * @param base
	 */
	public ArchitecturalElement(String name, double hSupport, double vSupport, IArchitecturalElement base) {
		this.name = name;
		this.horizontalSupport = hSupport;
		this.verticalSupport = vSupport;
		this.base = base;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#setName(java.lang.String)
	 */
	@Override
	public void setName(String name) {
		this.name = name;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#isHasHorizontalSupport()
	 */
	@Override
	public boolean hasHorizontalSupport() {
		if (getHorizontalSupport() > 0D) return true;
		return false;
	}


	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#isHasVerticalSupport()
	 */
	@Override
	public boolean hasVerticalSupport() {
		if (getVerticalSupport() > 0D) return true;
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#getHorizontalSupport()
	 */
	@Override
	public double getHorizontalSupport() {
		return horizontalSupport;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#setHorizontalSupport(double)
	 */
	@Override
	public void setHorizontalSupport(double horizontalSupport) {
		this.horizontalSupport = horizontalSupport;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#getVerticalSupport()
	 */
	@Override
	public double getVerticalSupport() {
		return verticalSupport;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#setVerticalSupport(double)
	 */
	@Override
	public void setVerticalSupport(double verticalSupport) {
		this.verticalSupport = verticalSupport;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#getFace()
	 */
	@Override
	public Face getFace() {
		return face;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#setFace(com.someguyssoftware.dungeonsengine.enums.Face)
	 */
	@Override
	public IArchitecturalElement setFace(Face face) {
		this.face = face;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#getBase()
	 */
	@Override
	public IArchitecturalElement getBase() {
		return base;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.IArchitecturalElement#setBase(com.someguyssoftware.dungeonsengine.model.IArchitecturalElement)
	 */
	@Override
	public void setBase(IArchitecturalElement base) {
		this.base = base;
	}

}
