/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Jul 4, 2016
 *
 */
public class RandomGroup {
	@Since(1.0)
	private String ref;
	/*
	 * This is a itemsFactor (factor) for the Quantity of an item found in a chest.  It is applied to the entire group of items.
	 */
	@Since(1.0)
	private double itemsFactor;
	@Since(1.0)
	private double chanceFactor;	
	@Since(1.0)
	@SerializedName("num")
	private Quantity quantity;
	@Since(1.0)
	private double order;
	
	/**
	 * 
	 */
	public RandomGroup() {
		setQuantity(new Quantity());
		setItemsFactor(1.0);
		setChanceFactor(1.0);
	}

	/**
	 * @return the itemsFactor
	 */
	public double getItemsFactor() {
		return itemsFactor;
	}

	/**
	 * @param itemsFactor the itemsFactor to set
	 */
	public void setItemsFactor(double factor) {
		this.itemsFactor = factor;
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
	
	/**
	 * 
	 * @param group
	 */
	public void setRef(ChestItemGroup group) {
		this.ref = group.getName();
	}

	/**
	 * @return the chanceFactor
	 */
	public double getChanceFactor() {
		return chanceFactor;
	}

	/**
	 * @param chanceFactor the chanceFactor to set
	 */
	public void setChanceFactor(double chanceFactor) {
		this.chanceFactor = chanceFactor;
	}
}
