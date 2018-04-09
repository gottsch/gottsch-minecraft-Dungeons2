/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.random.IRandomProbabilityItem;

/**
 * @author Mark Gottschling on Jul 4, 2016
 *
 */
public class RandomItem implements IRandomProbabilityItem{
	@Since(1.0)
	private String ref;
	@Since(1.0)
	private double chance;
	@Since(1.0)
	private double weight;
	@Since(1.0)
	@SerializedName("num")
	private Quantity quantity;
	@Since(1.0)
	private double order;
	@Since(1.0)
	private Enchants enchants;
	
	/**
	 * 
	 */
	public RandomItem() {
		setQuantity(new Quantity());
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
	 * @return the quantity
	 */
	public Quantity getQuantity() {
		return quantity;
	}

	/**
	 * @param quantity the quantity to set
	 */
	public void setQuantity(Quantity quantity) {
		this.quantity = quantity;
	}

	/**
	 * @return the order
	 */
	public double getOrder() {
		return order;
	}

	/**
	 * @param order the order to set
	 */
	public void setOrder(double order) {
		this.order = order;
	}

	/**
	 * @return the enchants
	 */
	public Enchants getEnchants() {
		if (enchants == null) {
			enchants = new Enchants();
		}
		return enchants;
	}

	/**
	 * @param enchants the enchants to set
	 */
	public void setEnchants(Enchants enchants) {
		this.enchants = enchants;
	}


	/**
	 * @return the ref
	 */
	public String getRef() {
		return ref;
	}

	/**
	 * @param ref the ref to set
	 */
	public void setRef(String ref) {
		this.ref = ref;
	}
	
	public void setRef(ChestItem item) {
		this.ref = item.getId();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "RandomItem [ref=" + ref + ", chance=" + chance + ", weight=" + weight + ", quantity=" + quantity
				+ ", order=" + order + ", enchants=" + enchants + "]";
	}

	@Override
	public int getProbability() {
		return (int) getChance();
	}
	
}
