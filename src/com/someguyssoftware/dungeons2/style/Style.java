/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public class Style {
	@Expose(serialize = false, deserialize = false)
	public static final Style NO_STYLE = new Style();
	@Since(1.0)
	private String category;
	@Since(1.0)
	private String name;
	@Since(1.0)
	private String block;
	@Since(1.0)
	private double decay;
	@Since(1.0)
	private List<String> decayBlocks;

	/**
	 * 
	 */
	public Style() {
		decayBlocks = new ArrayList<>();
	}
	
	/**
	 * 
	 * @param style
	 */
	public Style(Style style) {
		this();
		setName(style.getName());
		setCategory(style.getCategory());
		setBlock(style.getBlock());
		setDecay(style.getDecay());
		decayBlocks.addAll(style.getDecayBlocks());
	}
	
	/**
	 * 
	 * @return
	 */
	public Style copy() {
		return new Style(this);
	}

	/**
	 * @return the NAME
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param NAME the NAME to set
	 */
	public Style setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * @return the block
	 */
	public String getBlock() {
		return block;
	}

	/**
	 * @param block the block to set
	 */
	public Style setBlock(String block) {
		this.block = block;
		return this;
	}

	/**
	 * @return the decay
	 */
	public double getDecay() {
		return decay;
	}

	/**
	 * @param decay the decay to set
	 */
	public Style setDecay(double decay) {
		this.decay = decay;
		return this;
	}

	/**
	 * @return the decayBlocks
	 */
	public List<String> getDecayBlocks() {
		return decayBlocks;
	}

	/**
	 * @param decayBlocks the decayBlocks to set
	 */
	public Style setDecayBlocks(List<String> decayBlocks) {
		this.decayBlocks = decayBlocks;
		return this;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Style [NAME=" + name + ", block=" + block + ", decay=" + decay + ", decayBlocks=" + decayBlocks + "]";
	}

	/**
	 * @return the category
	 */
	public String getCategory() {
		return category;
	}

	/**
	 * @param category the category to set
	 */
	public void setCategory(String category) {
		this.category = category;
	}

}
