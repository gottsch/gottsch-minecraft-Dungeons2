/**
 * 
 */
package com.someguyssoftware.dungeons2.spawner;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Since;
import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.random.IRandomProbabilityItem;

/**
 * Simple Mob Spawner group.
 * @author Mark Gottschling on Jan 16, 2017
 *
 */
public class SpawnGroup implements IRandomProbabilityItem {
	@Since(1.0)
	private String name;
	@Since(1.0)
	private String category;
	@Since(1.0)
	Quantity level;
	@Since(1.0)
	private double chance;
	@Since(1.0)
	private double weight;
	@Since(1.0)
	private List<String> mobs;
	
	public SpawnGroup() {
		mobs = new ArrayList<>(5);
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
	public void setName(String name) {
		this.name = name;
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

	/**
	 * @return the level
	 */
	public Quantity getLevel() {
		return level;
	}

	/**
	 * @param level the level to set
	 */
	public void setLevel(Quantity level) {
		this.level = level;
	}

	/**
	 * @return the chance
	 */
	public double getChance() {
		return chance;
	}

	/**
	 * @param chance the chance to set
	 */
	public void setChance(double chance) {
		this.chance = chance;
	}

	/**
	 * @return the weight
	 */
	public double getWeight() {
		return weight;
	}

	/**
	 * @param weight the weight to set
	 */
	public void setWeight(double weight) {
		this.weight = weight;
	}

	/**
	 * @return the mobs
	 */
	public List<String> getMobs() {
		return mobs;
	}

	/**
	 * @param mobs the mobs to set
	 */
	public void setMobs(List<String> mobs) {
		this.mobs = mobs;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.mod.IRandomProbabilityItem#getProbability()
	 */
	@Override
	public int getProbability() {
		return (int) getWeight();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SpawnGroup [NAME=" + name + ", category=" + category + ", level=" + level + ", chance=" + chance
				+ ", weight=" + weight + ", mobs=" + mobs + "]";
	}
}
