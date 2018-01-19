/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Mark Gottschling on Jul 4, 2016
 *
 */
public class ChestItemGroup {
	private String name;
	private List<RandomItem> items;
	
	/**
	 * 
	 */
	public ChestItemGroup() {
		items = new ArrayList<>();
	}

	public ChestItemGroup(String group) {
		items = new ArrayList<>();
		setName(group);
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

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ChestItemGroup [NAME=" + name + ", items=" + items + "]";
	}

	/**
	 * @return the items
	 */
	public List<RandomItem> getItems() {
		return items;
	}

	/**
	 * @param items the items to set
	 */
	public void setItems(List<RandomItem> items) {
		this.items = items;
	}
}
