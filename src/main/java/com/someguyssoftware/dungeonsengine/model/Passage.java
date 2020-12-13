/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import com.someguyssoftware.gottschcore.enums.Alignment;

/**
 * @author Mark Gottschling on Jan 9, 2019
 *
 */
public class Passage extends AbstractSpace {
	private Alignment alignment;
	
	/**
	 * 
	 */
	public Passage() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * 
	 * @param passage
	 */
	public Passage(Passage passage) {
		// TODO Auto-generated constructor stub
	}

	public Passage copy () {
		return new Passage(this);
	}
	
	/**
	 * @return the alignment
	 */
	public Alignment getAlignment() {
		return alignment;
	}

	/**
	 * @param alignment the alignment to set
	 */
	public void setAlignment(Alignment alignment) {
		this.alignment = alignment;
	}

}
