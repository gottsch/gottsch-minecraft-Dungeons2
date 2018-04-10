/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Since;
import com.someguyssoftware.gottschcore.random.IRandomProbabilityItem;

/**
 * @author Mark Gottschling on Jul 4, 2016
 *
 */
public class ChestContainer implements IRandomProbabilityItem {
	@Since(1.0)
	private String name;
	@Since(1.0)
	private String category;
	// TODO need to add weight, and chance because a rare chest shouldn't occur as often as a common chest
	@Since(1.0)
	private List<RandomGroup> randomGroups;
	@Since(1.0)
	private List<RandomItem> randomItems;
	@Since(1.0)
	private double chance;
	@Since(1.0)
	private double weight;
	
	/**
	 * 
	 */
	public ChestContainer() {
		setRandomGroups(new ArrayList<RandomGroup>());
		setRandomItems(new ArrayList<RandomItem>());
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
	 * @return the randomItems
	 */
	public List<RandomItem> getRandomItems() {
		return randomItems;
	}

	/**
	 * @param randomItems the randomItems to set
	 */
	public void setRandomItems(List<RandomItem> items) {
		this.randomItems = items;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ChestContainer [NAME=" + name + ", randomItems=" + randomItems + "]";
	}

	/**
	 * @return the randomGroups
	 */
	public List<RandomGroup> getRandomGroups() {
		return randomGroups;
	}

	/**
	 * @param randomGroups the randomGroups to set
	 */
	public void setRandomGroups(List<RandomGroup> randomGroups) {
		this.randomGroups = randomGroups;
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
	 * Required to use RandomProbabilityCollection. Uses the weight instead of the chance.
	 */
	@Override
	public int getProbability() {
		return (int) getWeight();
	}
	
}
