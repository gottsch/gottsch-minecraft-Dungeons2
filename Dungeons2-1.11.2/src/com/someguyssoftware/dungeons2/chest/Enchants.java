/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Jul 4, 2016
 *
 */
public class Enchants {
	@Since(1.0)
	@SerializedName("num")
	private Quantity quantity;
	@Since(1.0)
	@SerializedName("enchanments")
	private List<ChestItemEnchantment> enchantments;
	
	/**
	 * 
	 */
	public Enchants() {
		setEnchantments(new ArrayList<ChestItemEnchantment>());
	}

	/**
	 * @return the quantity
	 */
	public Quantity getQuantity() {
		if (quantity == null) {
			quantity = new Quantity(0, 0);
		}
		return quantity;
	}

	/**
	 * @param quantity the quantity to set
	 */
	public void setQuantity(Quantity quantity) {
		this.quantity = quantity;
	}

	/**
	 * @return the enchantments
	 */
	public List<ChestItemEnchantment> getEnchantments() {
		return enchantments;
	}

	/**
	 * @param enchantments the enchantments to set
	 */
	public void setEnchantments(List<ChestItemEnchantment> enchantments) {
		this.enchantments = enchantments;
	}
}
