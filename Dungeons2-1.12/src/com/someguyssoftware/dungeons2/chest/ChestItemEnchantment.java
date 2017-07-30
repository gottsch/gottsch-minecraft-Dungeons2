/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import com.google.gson.annotations.Since;

/**
 * @author Mark Gottschling on Jul 4, 2016
 *
 */
public class ChestItemEnchantment {
	@Since(1.0)
	private int id;
	@Since(1.0)
	private String name;
	@Since(1.0)
	private int level;
	
	/**
	 * 
	 */
	public ChestItemEnchantment() {}

	/**
	 * @return the NAME
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param NAME the NAME to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * @return the level
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * @param level the level to set
	 */
	public void setLevel(int level) {
		this.level = level;
	}
}
